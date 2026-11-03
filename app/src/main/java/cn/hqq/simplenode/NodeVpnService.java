package cn.hqq.simplenode;

import android.app.*;
import android.content.Intent;
import android.net.VpnService;
import android.os.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NodeVpnService extends VpnService {
    static volatile boolean connected,starting,stopping;
    static volatile String message="未连接";
    private volatile boolean stopRequested;
    private ParcelFileDescriptor tun;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean cleanupQueued=new AtomicBoolean();
    @Override public void onCreate(){
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("vpn","VPN 连接",NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null&&"STOP".equals(intent.getAction())){disconnect("未连接");return START_NOT_STICKY;}
        if(connected||starting||stopping)return START_NOT_STICKY;
        // A stopped VPN service can remain system-bound and later receive START again.
        cleanupQueued.set(false);stopRequested=false;
        Intent show=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,show,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stopPi=PendingIntent.getService(this,1,new Intent(this,NodeVpnService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification note=new Notification.Builder(this,"vpn").setContentTitle("简连 · 全局代理")
            .setContentText("点击查看节点和连接状态").setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentIntent(pi).setOngoing(true).addAction(new Notification.Action.Builder(null,"断开",stopPi).build()).build();
        startForeground(1,note);starting=true;message="正在连接…";changed();
        worker.submit(()->{
            try{
                Core.ensure(this);if(stopRequested)return;
                String selected=getSharedPreferences("state",0).getString("selected",null);
                if(selected==null||!Core.select(selected))throw new Exception();
                ParcelFileDescriptor next=new Builder().setSession("简连 · 全局代理").setMtu(1500)
                    .addAddress("172.19.0.1",30).addAddress("fdfe:dcba:9876::1",126)
                    .addDnsServer("198.18.0.2").addRoute("0.0.0.0",0).addRoute("::",0)
                    .addDisallowedApplication(getPackageName()).setBlocking(false).setMetered(false).establish();
                if(next==null)throw new Exception();
                if(stopRequested){next.close();return;}
                tun=next;
                if(!Core.startTun(next.getFd()))throw new Exception();
                if(stopRequested)return;
                connected=true;starting=false;message="VPN 已连接 · "+selected;changed();
            }catch(Exception e){if(!stopRequested)main.post(()->disconnect("连接失败，请检查节点或重新测速"));}
        });
        return START_NOT_STICKY;
    }
    private void changed(){sendBroadcast(new Intent("cn.hqq.simplenode.STATE").setPackage(getPackageName()));}
    private void disconnect(String endMessage){
        if(!cleanupQueued.compareAndSet(false,true))return;
        stopRequested=true;starting=false;stopping=true;message="正在断开…";changed();
        worker.submit(()->{
            try{Core.stopTun();}finally{
                try{if(tun!=null)tun.close();}catch(Exception ignored){}
                tun=null;
                main.post(()->{connected=false;stopping=false;message=endMessage;stopForeground(STOP_FOREGROUND_REMOVE);changed();stopSelf();});
            }
        });
    }
    @Override public void onRevoke(){disconnect("VPN 授权已撤销");}
    @Override public void onDestroy(){disconnect("未连接");worker.shutdown();super.onDestroy();}
}
