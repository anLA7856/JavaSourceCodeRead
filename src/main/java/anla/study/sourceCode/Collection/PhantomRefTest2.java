package anla.study.sourceCode.Collection;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 虚引用测试，虽然一旦gc可以被清除，但是要两次后才会被清除。，因为他在finalize续了一次命
 * 	创建的虚引用为：java.lang.ref.PhantomReference@15db9742
	第1次gc
	MyObject's finalize called
	第2次gc
	clean resource:Some Resources
	删除的虚引用为：java.lang.ref.PhantomReference@15db9742  but获取虚引用的对象obj.get()=null
 * @author anla7856
 *
 */
public class PhantomRefTest2 {
	private static ReferenceQueue<MyObject> phanQueue = new ReferenceQueue<MyObject>();
	private static Map<Reference<MyObject>, String> map = new HashMap<Reference<MyObject>, String>();

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
				obj = (Reference<MyObject>) phanQueue.remove();
				Object value = map.get(obj);
				System.out.println("clean resource:" + value);
				map.remove(obj);

				System.out.println("删除的虚引用为：" + obj + "  but获取虚引用的对象obj.get()="
						+ obj.get());
				System.exit(0);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public static void main(String[] args) throws InterruptedException {
		MyObject object = new MyObject();
		Reference<MyObject> phanRef = new PhantomReference<MyObject>(object,
				phanQueue);
		System.out.println("创建的虚引用为：" + phanRef);
		new Thread(new CheckRefQueue()).start();
		map.put(phanRef, "Some Resources");

		object = null;
		TimeUnit.SECONDS.sleep(1);
		int i = 1;
		while (true) {
			System.out.println("第" + i++ + "次gc");
			System.gc();
			TimeUnit.SECONDS.sleep(1);
		}
	}
}