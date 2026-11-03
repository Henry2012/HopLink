package main

/*
#include <stdlib.h>
*/
import "C"

import (
	"context"
	"encoding/json"
	"errors"
	"net/netip"
	"strings"
	"sync"
	"syscall"
	"time"

	"github.com/metacubex/mihomo/adapter/outboundgroup"
	"github.com/metacubex/mihomo/common/utils"
	"github.com/metacubex/mihomo/config"
	CT "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/hub/executor"
	LC "github.com/metacubex/mihomo/listener/config"
	"github.com/metacubex/mihomo/listener/sing_tun"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
	"github.com/metacubex/mihomo/tunnel/statistic"
	"gopkg.in/yaml.v3"
)

type node struct {
	Name string `json:"name"`
	Type string `json:"type"`
}

var state struct {
	sync.RWMutex
	nodes    []node
	listener *sing_tun.Listener
	selected string
}

var batches = struct {
	sync.Mutex
	next     int64
	contexts map[int64]context.Context
	cancels  map[int64]context.CancelFunc
}{contexts: make(map[int64]context.Context), cancels: make(map[int64]context.CancelFunc)}

func newBatch() int64 {
	batches.Lock()
	defer batches.Unlock()
	batches.next++
	ctx, cancel := context.WithCancel(context.Background())
	id := batches.next
	batches.contexts[id] = ctx
	batches.cancels[id] = cancel
	return id
}
func cancelBatch(id int64) {
	batches.Lock()
	defer batches.Unlock()
	if cancel := batches.cancels[id]; cancel != nil {
		cancel()
	}
	delete(batches.contexts, id)
	delete(batches.cancels, id)
}
func batchContext(id int64) (context.Context, bool) {
	batches.Lock()
	defer batches.Unlock()
	ctx, ok := batches.contexts[id]
	return ctx, ok
}

//export CoreBeginBatch
func CoreBeginBatch() C.longlong { return C.longlong(newBatch()) }

//export CoreCancelBatch
func CoreCancelBatch(id C.longlong) { cancelBatch(int64(id)) }

// Construct a local, global-only runtime. Never inherit remote providers,
// controllers, LAN listeners, scripts or update URLs from the imported file.
func runtimeConfig(raw string) ([]byte, []node, error) {
	var source struct {
		Proxies []map[string]any `yaml:"proxies"`
	}
	if yaml.Unmarshal([]byte(raw), &source) != nil || len(source.Proxies) == 0 {
		return nil, nil, errors.New("配置无效：需要包含节点的 Clash YAML 文件")
	}
	names := []string{}
	nodes := []node{}
	seen := map[string]bool{}
	for _, p := range source.Proxies {
		name, _ := p["name"].(string)
		typ, _ := p["type"].(string)
		switch strings.ToLower(typ) {
		case "direct", "reject", "dns", "pass":
			return nil, nil, errors.New("配置无效：列表只能包含代理节点，不能包含直连或拒绝出口")
		}
		if name == "" || typ == "" || seen[name] || name == "GLOBAL" || name == "DIRECT" || name == "REJECT" || name == "PASS" {
			return nil, nil, errors.New("配置无效：节点名称缺失、重复或使用了保留名称")
		}
		seen[name] = true
		names = append(names, name)
		nodes = append(nodes, node{name, typ})
	}
	cfg := map[string]any{
		"mode": "global", "log-level": "silent", "ipv6": true,
		"allow-lan": false, "find-process-mode": "off", "unified-delay": true,
		"proxy-groups": []any{map[string]any{"name": "GLOBAL", "type": "select", "proxies": names}},
		"proxies":      source.Proxies, "rules": []string{"MATCH,GLOBAL"},
		"profile": map[string]any{"store-selected": false, "store-fake-ip": false},
		"dns": map[string]any{
			"enable": true, "ipv6": true, "enhanced-mode": "fake-ip", "fake-ip-range": "198.18.0.1/16",
			"default-nameserver":      []string{"223.5.5.5", "119.29.29.29"},
			"proxy-server-nameserver": []string{"223.5.5.5", "119.29.29.29"},
			"nameserver":              []string{"https://1.1.1.1/dns-query", "https://8.8.8.8/dns-query"},
			"respect-rules":           true,
		},
	}
	data, err := yaml.Marshal(cfg)
	return data, nodes, err
}

func prepare(home, raw string) string {
	return prepareCore(home, raw, true)
}

func prepareCore(home, raw string, apply bool) string {
	data, nodes, err := runtimeConfig(raw)
	if err != nil {
		return reply(false, err.Error(), nil)
	}
	state.Lock()
	defer state.Unlock()
	if state.listener != nil {
		return reply(false, "请先断开连接再导入配置", nil)
	}
	CT.SetHomeDir(home)
	log.SetLevel(log.SILENT)
	parsed, err := config.Parse(data)
	if err != nil {
		return reply(false, "节点配置无法加载，请检查协议与字段；原配置未替换", nil)
	}
	if apply {
		executor.ApplyConfig(parsed, true)
		state.nodes = nodes
		state.selected = ""
	}
	return reply(true, "", nodes)
}

func reply(ok bool, message string, nodes []node) string {
	b, _ := json.Marshal(map[string]any{"ok": ok, "error": message, "nodes": nodes})
	return string(b)
}

//export CorePrepare
func CorePrepare(home, raw *C.char) *C.char {
	return C.CString(prepare(C.GoString(home), C.GoString(raw)))
}

//export CoreValidate
func CoreValidate(home, raw *C.char) *C.char {
	return C.CString(prepareCore(C.GoString(home), C.GoString(raw), false))
}

//export CoreMeasure
func CoreMeasure(batch C.longlong, name *C.char) C.int {
	parent, ok := batchContext(int64(batch))
	if !ok {
		return -1
	}
	state.RLock()
	p := tunnel.Proxies()[C.GoString(name)]
	state.RUnlock()
	if p == nil {
		return -1
	}
	ctx, cancel := context.WithTimeout(parent, 5*time.Second)
	defer cancel()
	expected, _ := utils.NewUnsignedRanges[uint16]("204")
	ms, err := p.URLTest(ctx, "https://cp.cloudflare.com", expected)
	if err != nil || !p.AliveForTestUrl("https://cp.cloudflare.com") {
		return -1
	}
	if ms == 0 {
		ms = 1
	}
	return C.int(ms)
}

//export CoreSelect
func CoreSelect(name *C.char) C.int {
	state.Lock()
	defer state.Unlock()
	selected := C.GoString(name)
	found := false
	for _, n := range state.nodes {
		if n.Name == selected {
			found = true
			break
		}
	}
	if !found {
		return 0
	}
	if state.selected == selected {
		return 1
	}
	group := tunnel.Proxies()["GLOBAL"]
	if group == nil {
		return 0
	}
	s, ok := group.Adapter().(outboundgroup.SelectAble)
	if !ok || s.Set(selected) != nil {
		return 0
	}
	state.selected = selected
	statistic.DefaultManager.Range(func(t statistic.Tracker) bool { _ = t.Close(); return true })
	return 1
}

//export CoreStartTun
func CoreStartTun(fd C.int) C.int {
	state.Lock()
	defer state.Unlock()
	if state.listener != nil {
		return 0
	}
	owned, err := syscall.Dup(int(fd))
	if err != nil {
		return 0
	}
	listener, err := sing_tun.New(tunOptions(owned), tunnel.Tunnel)
	if err != nil {
		syscall.Close(owned)
		return 0
	}
	state.listener = listener
	return 1
}

func tunOptions(fd int) LC.Tun {
	return LC.Tun{
		Enable: true, Device: "SimpleNode", Stack: CT.TunGvisor, MTU: 1500,
		AutoRoute: false, AutoDetectInterface: false, FileDescriptor: fd,
		DisableICMPForwarding: true,
		DNSHijack:             []string{"any:53"},
		Inet4Address:          []netip.Prefix{netip.MustParsePrefix("172.19.0.1/30")},
		Inet6Address:          []netip.Prefix{netip.MustParsePrefix("fdfe:dcba:9876::1/126")},
	}
}

//export CoreStopTun
func CoreStopTun() {
	state.Lock()
	listener := state.listener
	state.listener = nil
	state.Unlock()
	statistic.DefaultManager.Range(func(t statistic.Tracker) bool { _ = t.Close(); return true })
	if listener != nil {
		_ = listener.Close()
	}
}

func main() {}
