package cn.hqq.simplenode;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.VpnService;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class MainActivity extends Activity {
    private NodeRepository repository;
    private NodeState state;
    private TextView status,summary;
    private Button connect,test,auto,load;
    private ListView list;
    private BaseAdapter adapter;
    private List<NodeState.Node> visible=new ArrayList<>();
    private boolean registered;
    private final Runnable observer=this::render;
    private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){render();}};
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);repository=NodeRepository.get(this);state=repository.state;
        getWindow().setStatusBarColor(Color.WHITE);getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(20),dp(24),dp(20),dp(12));root.setBackgroundColor(Color.WHITE);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(dp(20),dp(24)+insets.getSystemWindowInsetTop(),dp(20),dp(12)+insets.getSystemWindowInsetBottom());return insets;});
        TextView title=new TextView(this);title.setText("简连");title.setTextSize(30);title.setTextColor(Color.rgb(24,36,44));root.addView(title);
        TextView subtitle=new TextView(this);subtitle.setText("全局代理 · 全部节点");subtitle.setTextSize(14);subtitle.setPadding(0,dp(6),0,dp(12));root.addView(subtitle);
        status=new TextView(this);status.setTextSize(15);root.addView(status);
        connect=new Button(this);connect.setText("连接");connect.setOnClickListener(v->toggle());root.addView(connect,new LinearLayout.LayoutParams(-1,dp(54)));
        LinearLayout actions=new LinearLayout(this);
        load=new Button(this);load.setText("导入配置");load.setOnClickListener(v->importFile());actions.addView(load,new LinearLayout.LayoutParams(0,dp(48),1));
        test=new Button(this);test.setText("测速");test.setOnClickListener(v->{if(repository.measuring)repository.stopMeasure();else repository.measure();});actions.addView(test,new LinearLayout.LayoutParams(0,dp(48),1));
        auto=new Button(this);auto.setText("自动选择");auto.setOnClickListener(v->repository.automatic());actions.addView(auto,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(actions);
        summary=new TextView(this);summary.setTextSize(13);summary.setPadding(0,dp(8),0,dp(8));root.addView(summary);
        list=new ListView(this);list.setDividerHeight(dp(1));root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        adapter=new BaseAdapter(){
            public int getCount(){return visible.size();}public Object getItem(int p){return visible.get(p);}public long getItemId(int p){return p;}
            public View getView(int p,View recycled,android.view.ViewGroup parent){
                NodeState.Node n=visible.get(p);LinearLayout row=new LinearLayout(MainActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(2),dp(12),dp(2),dp(12));
                String chosen=repository.busy&&state.manual==null?repository.prefs.getString("selected",null):state.selected();
                RadioButton radio=new RadioButton(MainActivity.this);radio.setClickable(false);radio.setFocusable(false);radio.setChecked(n.name.equals(chosen));row.addView(radio);
                LinearLayout words=new LinearLayout(MainActivity.this);words.setOrientation(1);
                TextView name=new TextView(MainActivity.this);name.setText(n.name);name.setTextSize(16);name.setTextColor(Color.rgb(24,36,44));words.addView(name);
                TextView detail=new TextView(MainActivity.this);detail.setText(n.type+" · "+(n.delay>0?n.delay+" ms":n.delay<0?"超时":"未测速"));detail.setTextSize(13);words.addView(detail);
                row.addView(words,new LinearLayout.LayoutParams(0,-2,1));return row;
            }
        };
        list.setAdapter(adapter);list.setOnItemClickListener((a,v,p,id)->repository.choose(visible.get(p).name));
        setContentView(root);render();repository.restore();
    }
    @Override public void onStart(){super.onStart();IntentFilter f=new IntentFilter("cn.hqq.simplenode.STATE");if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(receiver,f);registered=true;repository.observe(observer);}
    @Override public void onStop(){repository.removeObserver(observer);if(registered){unregisterReceiver(receiver);registered=false;}super.onStop();}
    private void render(){
        if(status==null)return;
        String selected=state.selected();status.setText(NodeVpnService.message+(NodeVpnService.connected||selected==null?"":" · "+selected));
        connect.setText(NodeVpnService.connected?"断开连接":NodeVpnService.starting?"取消连接":"连接");connect.setEnabled(!NodeVpnService.stopping&&(NodeVpnService.connected||NodeVpnService.starting||(!repository.busy&&selected!=null)));
        load.setEnabled(!repository.busy&&!NodeVpnService.connected&&!NodeVpnService.starting&&!NodeVpnService.stopping);test.setText(repository.measuring?"停止测速":"测速");test.setEnabled(repository.measuring||(!repository.busy&&!state.nodes.isEmpty()));auto.setEnabled(!repository.busy&&!state.nodes.isEmpty());
        list.setEnabled(!repository.busy);visible=state.sorted();adapter.notifyDataSetChanged();
        long success=state.nodes.stream().filter(n->n.delay>0).count();
        summary.setText(repository.info+"\n"+state.nodes.size()+" 个节点 · "+success+" 个有延迟结果 · "+(state.manual!=null?"手动选择":state.automaticHold!=null?"保持当前节点":"自动选最低延迟"));
    }
    private void importFile(){startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),10);}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==20){if(result==RESULT_OK)startVpn();return;}
        if(request==10&&result==RESULT_OK&&data!=null&&data.getData()!=null)repository.importFile(data.getData());
    }
    private void toggle(){if(NodeVpnService.connected||NodeVpnService.starting){startService(new Intent(this,NodeVpnService.class).setAction("STOP"));return;}Intent permission=VpnService.prepare(this);if(permission!=null)startActivityForResult(permission,20);else startVpn();}
    private void startVpn(){repository.applyChoice();startForegroundService(new Intent(this,NodeVpnService.class));render();}
}
