import cn.hqq.simplenode.LatencyBatch;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class LatencyBatchTest {
    public static void main(String[] args){
        CountDownLatch releaseSlow=new CountDownLatch(1);AtomicInteger active=new AtomicInteger(),peak=new AtomicInteger();
        List<String> order=new ArrayList<>();List<Integer> delays=new ArrayList<>();
        List<String> names=new ArrayList<>(Arrays.asList("slow","fast","fails"));for(int i=0;i<30;i++)names.add("node"+i);
        LatencyBatch.run(names,16,name->{
            int count=active.incrementAndGet();peak.accumulateAndGet(count,Math::max);
            try {if(name.equals("slow")&&!releaseSlow.await(2,TimeUnit.SECONDS))throw new AssertionError("blocked on submission order");if(name.equals("fails"))throw new Exception();return 10;}
            finally{active.decrementAndGet();}
        },(name,delay,done,total)->{order.add(name);delays.add(delay);if(!name.equals("slow"))releaseSlow.countDown();if(done!=order.size()||total!=names.size())throw new AssertionError();});
        if(order.get(0).equals("slow")||order.size()!=names.size()||peak.get()>16||peak.get()<2||delays.get(order.indexOf("fails"))!=-1)throw new AssertionError();
        System.out.println("PASS: completion-order progress, bounded 16-way concurrency, failed probe reporting");
        CountDownLatch entered=new CountDownLatch(1);AtomicBoolean outcome=new AtomicBoolean(true);
        Thread runner=new Thread(()->outcome.set(LatencyBatch.run(Arrays.asList("blocked"),1,name->{entered.countDown();new CountDownLatch(1).await();return 1;},(n,d,c,t)->{})));
        runner.start();
        try{if(!entered.await(1,TimeUnit.SECONDS))throw new AssertionError();runner.interrupt();runner.join(1000);}catch(InterruptedException e){throw new AssertionError(e);}
        if(runner.isAlive()||outcome.get())throw new AssertionError("interruption reported as normal completion");
        System.out.println("PASS: stop returns promptly and is not reported as completed");
    }
}
