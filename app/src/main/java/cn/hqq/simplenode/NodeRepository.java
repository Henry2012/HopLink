package cn.hqq.simplenode;

import android.content.*;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Application-owned tasks and selection survive Activity recreation. UI state changes run on main. */
final class NodeRepository {
    private static NodeRepository instance;
    static synchronized NodeRepository get(Context context){if(instance==null)instance=new NodeRepository(context.getApplicationContext());return instance;}
    final NodeState state=new NodeState();
    final Context context;
    final SharedPreferences prefs;
    final ExecutorService coordinator=Executors.newSingleThreadExecutor();
    final Handler main=new Handler(Looper.getMainLooper());
    boolean busy,loaded,measuring;
    private long measureGeneration,nativeBatch;
    private Future<?> measureTask;
    String info="导入配置后开始使用";
    private Runnable observer;
    private NodeRepository(Context ctx){context=ctx;prefs=ctx.getSharedPreferences("state",0);}
    void observe(Runnable next){observer=next;changed();}
    void removeObserver(Runnable previous){if(observer==previous)observer=null;}
    private void changed(){if(observer!=null)observer.run();}
    File profile(){return new File(context.getFilesDir(),"profile.yaml");}
    void restore(){
        if(loaded||busy)return;loaded=true;if(!profile().exists())return;
        busy=true;changed();coordinator.submit(()->{try{
            JSONObject result=Core.ready?Core.metadata:Core.prepare(context,new String(Files.readAllBytes(profile().toPath()),StandardCharsets.UTF_8));
            main.post(()->{setNodes(result);busy=false;info="上次测速结果 · 可重新测速";changed();});
        }catch(Exception e){main.post(()->{busy=false;info="配置恢复失败，请重新导入";changed();});}});
    }
    private void setNodes(JSONObject result){
        state.nodes.clear();JSONArray arr=result.optJSONArray("nodes");
        if(arr!=null)for(int i=0;i<arr.length();i++){JSONObject n=arr.optJSONObject(i);if(n!=null)state.nodes.add(new NodeState.Node(n.optString("name"),n.optString("type"),prefs.getInt("delay:"+n.optString("name"),0)));}
        state.manual=prefs.getString("manual",null);state.automaticHold=prefs.getString("automaticHold",null);applyChoice();
    }
    void choose(String name){state.manual=name;prefs.edit().putString("manual",name).apply();applyChoice();changed();}
    void automatic(){state.manual=null;state.automaticHold=null;prefs.edit().remove("manual").remove("automaticHold").apply();applyChoice();changed();}
    void applyChoice(){String selected=state.selected();if(selected!=null&&Core.ready){if(Core.select(selected)){prefs.edit().putString("selected",selected).apply();if(NodeVpnService.connected)NodeVpnService.message="VPN 已连接 · "+selected;}else info="节点选择失败，请重新导入或测速";}else prefs.edit().remove("selected").apply();}
    void importFile(Uri uri){
        if(busy||NodeVpnService.connected||NodeVpnService.starting||NodeVpnService.stopping)return;
        busy=true;info="正在导入…";changed();coordinator.submit(()->{try{
            byte[] bytes;
            try(InputStream in=context.getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[8192];int n,total=0;while((n=in.read(b))!=-1){total+=n;if(total>8*1024*1024)throw new Exception();out.write(b,0,n);}bytes=out.toByteArray();
            }
            ProfileTransaction.replace(profile().toPath(),bytes,
                raw->Core.validate(context,raw),raw->Core.prepare(context,new String(raw,StandardCharsets.UTF_8)));
            prefs.edit().clear().commit();
            JSONObject valid=Core.metadata;
            main.post(()->{setNodes(valid);busy=false;info="导入成功，正在测速";changed();measure();});
        }catch(Exception e){main.post(()->{busy=false;info="导入未完成，请检查文件或存储空间；原配置未主动删除";changed();});}});
    }
    void measure(){
        if(busy||state.nodes.isEmpty())return;
        busy=true;measuring=true;long generation=++measureGeneration;
        long batchId=Core.beginBatch();nativeBatch=batchId;
        state.automaticHold=prefs.getString("selected",null);
        info="正在测速…";changed();
        List<String> names=new ArrayList<>();Map<String,NodeState.Node> byName=new HashMap<>();
        for(NodeState.Node n:state.nodes){names.add(n.name);byName.put(n.name,n);}
        measureTask=coordinator.submit(()->{
            try{
                boolean completed=LatencyBatch.run(names,16,name->Core.measure(batchId,name),(name,delay,done,total)->main.post(()->{
                    if(generation!=measureGeneration)return;
                    byName.get(name).delay=delay;
                    info="正在测速… "+done+" / "+total;
                    if(done==1||done%4==0||done==total)changed();
                }));
                main.post(()->{
                    if(generation!=measureGeneration)return;
                    persistDelays();busy=false;measuring=false;
                    if(completed){state.automaticHold=null;prefs.edit().remove("automaticHold").apply();}
                    else retainAppliedSelection();
                    applyChoice();info=completed?(state.selected()==null?"未测得可用节点，请检查网络后重试":"测速完成 · 超时节点置底"):"测速已停止 · 保留已完成结果";changed();
                });
            }catch(Exception e){main.post(()->{if(generation!=measureGeneration)return;persistDelays();busy=false;measuring=false;retainAppliedSelection();info="测速中断，已保留结果";changed();});}
            finally{Core.cancelBatch(batchId);}
        });
    }
    private void persistDelays(){SharedPreferences.Editor edit=prefs.edit();for(NodeState.Node n:state.nodes)edit.putInt("delay:"+n.name,n.delay);edit.apply();}
    private void retainAppliedSelection(){state.automaticHold=prefs.getString("selected",null);if(state.automaticHold!=null)prefs.edit().putString("automaticHold",state.automaticHold).apply();}
    void stopMeasure(){
        if(!measuring)return;
        ++measureGeneration;
        Core.cancelBatch(nativeBatch);
        if(measureTask!=null)measureTask.cancel(true);
        persistDelays();retainAppliedSelection();measuring=false;busy=false;
        if(prefs.getString("selected",null)==null)applyChoice();
        info="测速已停止 · 保留已完成结果和当前节点";changed();
    }
}
