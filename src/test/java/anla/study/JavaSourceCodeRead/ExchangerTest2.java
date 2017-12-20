package anla.study.JavaSourceCodeRead;

import java.util.concurrent.Exchanger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 交换，顾名思义，会交换两者的值
 * @author anla7856
 *
 */
public class ExchangerTest2 {
	private static volatile boolean isDone = false;

	static class ExchangerProducer implements Runnable {
		private Exchanger<Integer> exchanger;
		private static int data = 1;
		ExchangerProducer(Exchanger<Integer> exchanger) {
			this.exchanger = exchanger;
		}
		public void run() {
			try {
				data = 1;
				System.out.println("producer before: " + data);
				data = exchanger.exchange(data);
				System.out.println("producer after: " + data);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

		}
	}

	static class ExchangerConsumer implements Runnable {
		private Exchanger<Integer> exchanger;
		private static int data = 0;
		ExchangerConsumer(Exchanger<Integer> exchanger) {
			this.exchanger = exchanger;
		}
		public void run() {
			data = 0;
			System.out.println("consumer before : " + data);
			try {
				data = exchanger.exchange(data);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			System.out.println("consumer after : " + data);
		}
	}

	public static void main(String[] args) {
		ExecutorService exec = Executors.newCachedThreadPool();
		Exchanger<Integer> exchanger = new Exchanger<Integer>();
		new Thread(new ExchangerConsumer(exchanger)).start();
		new Thread(new ExchangerProducer(exchanger)).start();
	}
}