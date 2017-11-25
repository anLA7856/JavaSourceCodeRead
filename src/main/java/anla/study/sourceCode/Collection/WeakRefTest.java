package anla.study.sourceCode.Collection;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

/**
 * 	弱引用测试结果
 * 	创建的弱引用为：java.lang.ref.WeakReference@15db9742
	Before GC: Weak Get= I am MyObject
	After GC: Weak Get= null
	MyObject's finalize called
	删除的弱引用为：java.lang.ref.WeakReference@15db9742  but获取弱引用的对象obj.get()=null
 * @author anla7856
 *
 */
public class WeakRefTest {
	private static ReferenceQueue<MyObject> weakQueue = new ReferenceQueue<MyObject>();

	public static class MyObject {

		@Override
		protected void finalize() throws Throwable {
			super.finalize();
			System.out.println("MyObject's finalize called");
		}
		@Override
		public String toString() {
			return "I am MyObject";
		}
	}

	public static class CheckRefQueue implements Runnable {
		Reference<MyObject> obj = null;

		public void run() {
			try {
				obj = (Reference<MyObject>) weakQueue.remove();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if (obj != null) {
				System.out.println("删除的弱引用为：" + obj + "  but获取弱引用的对象obj.get()="
						+ obj.get());
			}
		}
	}

	public static void main(String[] args) {
		MyObject object = new MyObject();
		Reference<MyObject> weakRef = new WeakReference<MyObject>(object,
				weakQueue);
		System.out.println("创建的弱引用为：" + weakRef);
		new Thread(new CheckRefQueue()).start();

		object = null;
		System.out.println("Before GC: Weak Get= " + weakRef.get());
		System.gc();
		System.out.println("After GC: Weak Get= " + weakRef.get());
	}
}
