package main

import (
	"context"
	CT "github.com/metacubex/mihomo/constant"
	"gopkg.in/yaml.v3"
	"os"
	"strings"
	"testing"
)

func TestBatchCancellationIsIsolatedAndRejectsLateProbes(t *testing.T) {
	a, b := newBatch(), newBatch()
	defer cancelBatch(b)
	parent, ok := batchContext(a)
	if !ok {
		t.Fatal("batch missing")
	}
	child, done := context.WithCancel(parent)
	defer done()
	cancelBatch(a)
	if child.Err() != context.Canceled {
		t.Fatal("in-flight child not cancelled")
	}
	if _, ok := batchContext(a); ok {
		t.Fatal("late probe could register after stop")
	}
	other, ok := batchContext(b)
	if !ok || other.Err() != nil {
		t.Fatal("cancelled newer batch")
	}
}

func TestRuntimeDoesNotInheritRemoteEndpoints(t *testing.T) {
	raw := "proxies:\n  - {name: one, type: socks5, server: 127.0.0.1, port: 1}\nexternal-controller: 0.0.0.0:9090\nproxy-providers:\n  secret: {url: 'https://example.invalid/PRIVATE-CANARY'}\n"
	data, nodes, err := runtimeConfig(raw)
	if err != nil || len(nodes) != 1 {
		t.Fatal("valid node rejected")
	}
	if strings.Contains(string(data), "PRIVATE-CANARY") || strings.Contains(string(data), "external-controller") {
		t.Fatal("remote credentials inherited")
	}
	var result map[string]any
	yaml.Unmarshal(data, &result)
	if result["mode"] != "global" || result["allow-lan"] != false {
		t.Fatal("global/private invariant")
	}
}
func TestInvalidImportErrorsDoNotEchoSecrets(t *testing.T) {
	for _, raw := range []string{"PRIVATE-CANARY: [", "proxies: []", "proxies: [{name: DIRECT, type: socks5}]", "proxies: [{name: n, type: socks5},{name: n, type: socks5}]"} {
		_, _, err := runtimeConfig(raw)
		if err == nil {
			t.Fatal("invalid input accepted")
		}
		if strings.Contains(err.Error(), "PRIVATE-CANARY") {
			t.Fatal("credential in error")
		}
	}
}

func TestNonProxyExitsCannotEnterGlobalList(t *testing.T) {
	for _, kind := range []string{"direct", "reject", "dns", "pass"} {
		_, _, err := runtimeConfig("proxies: [{name: ordinary-name, type: " + kind + "}]")
		if err == nil {
			t.Fatalf("non-proxy exit accepted: %s", kind)
		}
	}
}

func TestTunDoesNotForwardICMPDirectly(t *testing.T) {
	options := tunOptions(42)
	if !options.DisableICMPForwarding || options.AutoRoute || options.AutoDetectInterface || options.FileDescriptor != 42 || options.Stack != CT.TunGvisor {
		t.Fatal("Android global TUN safety invariant failed")
	}
}
func TestPrepareAcceptsUnicodeAndPreservesWorkingProfileOnFailure(t *testing.T) {
	raw := "proxies:\n  - {name: '🇺🇸 测试节点', type: socks5, server: 127.0.0.1, port: 1}\n"
	result := prepare(t.TempDir(), raw)
	if !strings.Contains(result, `"ok":true`) || !strings.Contains(result, "🇺🇸 测试节点") {
		t.Fatal("native preparation rejected valid unicode node")
	}
	failed := prepare(t.TempDir(), "PRIVATE-CANARY: [")
	if !strings.Contains(failed, `"ok":false`) || strings.Contains(failed, "PRIVATE-CANARY") {
		t.Fatal("invalid preparation result")
	}
	if len(state.nodes) != 1 || state.nodes[0].Name != "🇺🇸 测试节点" {
		t.Fatal("working nodes replaced after failed import")
	}
}

func TestValidationDoesNotSwitchActiveNodes(t *testing.T) {
	active := "proxies: [{name: active, type: socks5, server: 127.0.0.1, port: 1}]"
	candidate := "proxies: [{name: candidate, type: socks5, server: 127.0.0.1, port: 2}]"
	if !strings.Contains(prepare(t.TempDir(), active), `"ok":true`) {
		t.Fatal("fixture rejected")
	}
	if !strings.Contains(prepareCore(t.TempDir(), candidate, false), `"ok":true`) {
		t.Fatal("candidate rejected")
	}
	if state.nodes[0].Name != "active" {
		t.Fatal("validation switched active runtime")
	}
}

// Explicit opt-in integration check; default test runs only use disposable fixtures.
func TestLocalProfileCompatibility(t *testing.T) {
	path := os.Getenv("SIMPLE_NODE_LOCAL_PROFILE")
	if path == "" {
		t.Skip("local profile check not requested")
	}
	raw, err := os.ReadFile(path)
	if err != nil {
		t.Fatal("local profile cannot be read")
	}
	result := prepare(t.TempDir(), string(raw))
	if !strings.Contains(result, `"ok":true`) {
		t.Fatal("local profile incompatible; no credentials printed")
	}
	t.Logf("Validated %d local node definitions without printing connection parameters", len(state.nodes))
}
