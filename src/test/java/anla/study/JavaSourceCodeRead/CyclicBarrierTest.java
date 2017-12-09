package anla.study.JavaSourceCodeRead;

import java.util.concurrent.CyclicBarrier;

public class CyclicBarrierTest {
	private static CyclicBarrier cyclicBarrier;

	static class CyclicBarrierThread extends Thread {
		public void run() {
			System.out.println(Thread.currentThread().getName() + "我走完了run了");
			// 等待
			try {
				cyclicBarrier.await();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static void main(String[] args) {
		cyclicBarrier = new CyclicBarrier(3, new Runnable() {
			public void run() {
				System.out.println("大家都走完了");
			}
		});

		for (int i = 0; i < 3; i++) {
			new CyclicBarrierThread().start();
		}
	}
}