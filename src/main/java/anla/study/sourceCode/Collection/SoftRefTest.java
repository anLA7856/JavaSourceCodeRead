package anla.study.sourceCode.Collection;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
/**
 * 测试软引用
 * output：
 * 	After GC: Soft Get= I am MyObject
	分配大块内存
	MyObject's finalize called
	Object for SoftReference is null
 * @author anla7856
 *
 */
public class SoftRefTest {
	private static ReferenceQueue<MyObject> softQueue = new ReferenceQueue<MyObject>();

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
				obj = (Reference<MyObject>) softQueue.remove();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if (obj != null) {
				System.out.println("Object for SoftReference is " + obj.get());
			}

		}

	}

	public static void main(String[] args) {
		MyObject object = new MyObject();
		SoftReference<MyObject> softRef = new SoftReference<MyObject>(object,
				softQueue);
		new Thread(new CheckRefQueue()).start();

		object = null; // 删除强引用
		System.gc();
		System.out.println("After GC: Soft Get= " + softRef.get());
		System.out.println("分配大块内存");
		byte[] b = new byte[5 * 1024 * 928];
		System.out.println("After new byte[]:Soft Get= " + softRef.get());
		System.gc();
	}
}