package cn.hqq.simplenode;

import java.nio.file.*;

/** Persist validated bytes before changing the active core; rollback on activation failure. */
public final class ProfileTransaction {
    public interface Step { void run(byte[] bytes)throws Exception; }
    public static void replace(Path target,byte[] next,Step validate,Step activate)throws Exception {
        byte[] old=Files.exists(target)?Files.readAllBytes(target):null;
        validate.run(next);
        Path pending=Files.createTempFile(target.getParent(),"profile-",".pending");
        boolean committed=false;
        try {
            Files.write(pending,next);
            Files.move(pending,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            committed=true;
            try {activate.run(next);}
            catch(Exception failure){
                if(old!=null){Files.write(pending,old);Files.move(pending,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);activate.run(old);}
                else Files.deleteIfExists(target);
                throw new Exception("导入激活失败，已恢复原配置");
            }
        } finally {Files.deleteIfExists(pending);}
    }
}
