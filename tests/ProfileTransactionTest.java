import cn.hqq.simplenode.ProfileTransaction;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public class ProfileTransactionTest {
    static byte[] b(String value){return value.getBytes(StandardCharsets.UTF_8);}
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("simple-node-import-");Path target=dir.resolve("profile");Files.write(target,b("old"));
        AtomicInteger activated=new AtomicInteger();
        try{ProfileTransaction.replace(target,b("new"),raw->{throw new Exception();},raw->activated.incrementAndGet());throw new AssertionError();}catch(Exception expected){}
        check(new String(Files.readAllBytes(target)).equals("old")&&activated.get()==0);
        Path blocked=dir.resolve("not-a-directory");Files.write(blocked,b("file"));
        try{ProfileTransaction.replace(blocked.resolve("profile"),b("new"),raw->{},raw->activated.incrementAndGet());throw new AssertionError();}catch(Exception expected){}
        check(activated.get()==0);
        try{ProfileTransaction.replace(target,b("new"),raw->{},raw->{if(new String(raw).equals("new"))throw new Exception();activated.incrementAndGet();});throw new AssertionError();}catch(Exception expected){}
        check(new String(Files.readAllBytes(target)).equals("old")&&activated.get()==1);
        ProfileTransaction.replace(target,b("new"),raw->{},raw->activated.incrementAndGet());
        check(new String(Files.readAllBytes(target)).equals("new"));
        Files.delete(target);Files.delete(blocked);Files.delete(dir);
        System.out.println("PASS: failed validation/write never activates, activation rollback, successful replacement");
    }
}
