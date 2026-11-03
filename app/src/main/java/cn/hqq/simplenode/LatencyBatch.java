package cn.hqq.simplenode;

import java.util.*;
import java.util.concurrent.*;

/** Bounded parallel probes, reported in completion order rather than submission order. */
public final class LatencyBatch {
    public interface Probe {int measure(String name)throws Exception;}
    public interface ResultListener {void result(String name,int delay,int completed,int total);}
    private static final class Result {
        final String name;final int delay;
        Result(String name,int delay){this.name=name;this.delay=delay;}
    }
    public static boolean run(List<String> names,int parallelism,Probe probe,ResultListener listener){
        ExecutorService pool=Executors.newFixedThreadPool(parallelism);
        CompletionService<Result> completed=new ExecutorCompletionService<>(pool);
        try {
            for(String name:names){if(Thread.currentThread().isInterrupted())return false;completed.submit(()->{try{return new Result(name,probe.measure(name));}catch(Exception e){return new Result(name,-1);}});}
            for(int done=1;done<=names.size();done++){
                try {Result r=completed.take().get();listener.result(r.name,r.delay,done,names.size());}
                catch(InterruptedException e){Thread.currentThread().interrupt();return false;}
                catch(ExecutionException e){throw new IllegalStateException("节点测速任务异常",e);}
            }
            return true;
        }finally{pool.shutdownNow();}
    }
}
