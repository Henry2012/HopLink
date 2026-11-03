package cn.hqq.simplenode;

import android.content.Context;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.nio.file.Files;
import org.json.JSONObject;

final class Core {
    static { System.loadLibrary("simplenode"); }
    static volatile boolean ready;
    static volatile JSONObject metadata;
    private static native byte[] prepareBytes(byte[] home,byte[] raw);
    private static native byte[] validateBytes(byte[] home,byte[] raw);
    static native long beginBatch();
    static native void cancelBatch(long batch);
    private static native int measureBytes(long batch,byte[] name);
    private static native boolean selectBytes(byte[] name);
    static native boolean startTun(int fd);
    static native void stopTun();
    private static byte[] utf8(String s){return s.getBytes(StandardCharsets.UTF_8);}
    static JSONObject prepare(Context ctx,String raw)throws Exception {
        JSONObject result=new JSONObject(new String(prepareBytes(utf8(ctx.getFilesDir().getPath()),utf8(raw)),StandardCharsets.UTF_8));
        if(!result.getBoolean("ok"))throw new Exception(result.optString("error","配置加载失败"));
        metadata=result;ready=true; return result;
    }
    static void validate(Context ctx,byte[] raw)throws Exception {
        JSONObject result=new JSONObject(new String(validateBytes(utf8(ctx.getFilesDir().getPath()),raw),StandardCharsets.UTF_8));
        if(!result.getBoolean("ok"))throw new Exception("节点配置无效；未替换原配置");
    }
    static synchronized void ensure(Context ctx)throws Exception {
        if(!ready)prepare(ctx,new String(Files.readAllBytes(new File(ctx.getFilesDir(),"profile.yaml").toPath()),StandardCharsets.UTF_8));
    }
    static int measure(long batch,String name){return measureBytes(batch,utf8(name));}
    static boolean select(String name){return selectBytes(utf8(name));}
}
