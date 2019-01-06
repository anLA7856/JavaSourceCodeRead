/**
 * @user junbao
 * @time 18-5-21 下午10:45
 */
package anla.study.JavaSourceCodeRead;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicStampedReference;

public class ABAProblem {
    private static AtomicInteger atomicInt = new AtomicInteger(100);
    private static AtomicStampedReference atomicStampedRef = new AtomicStampedReference(100, 0);

    public static void main(String[] args) throws InterruptedException {
        Thread intT1 = new Thread(new Runnable() {
            public void run() {
                atomicInt.compareAndSet(100, 200);
                atomicInt.compareAndSet(200, 100);
            }
        });

        Thread intT2 = new Thread(new Runnable() {
            public void run() {
                try {
                    // 休眠1s，等待intT1进行完CAS操作
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                }
                boolean c3 = atomicInt.compareAndSet(100, 200);
                System.out.println(c3); // 结果为true
            }
        });

        intT1.start();
        intT2.start();
        intT1.join();
        intT2.join();

        Thread refT1 = new Thread(new Runnable() {
            public void run() {
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                }
                // 进行CAS操作，同时版本号加1
                atomicStampedRef.compareAndSet(100, 200, atomicStampedRef.getStamp(), atomicStampedRef.getStamp() + 1);
                atomicStampedRef.compareAndSet(200, 100, atomicStampedRef.getStamp(), atomicStampedRef.getStamp() + 1);
            }
        });

        Thread refT2 = new Thread(new Runnable() {
            public void run() {
                // 获取最初版本号
                int stamp = atomicStampedRef.getStamp();
                try {
                    TimeUnit.SECONDS.sleep(2);
                } catch (InterruptedException e) {
                }
                // 由于版本号已经由refT1增加了，所以预期版本号失败导致更新失败。
                boolean c3 = atomicStampedRef.compareAndSet(100, 200, stamp, stamp + 1);
                System.out.println(c3); // 结果为false
            }
        });
        refT1.start();
        refT2.start();
    }
}