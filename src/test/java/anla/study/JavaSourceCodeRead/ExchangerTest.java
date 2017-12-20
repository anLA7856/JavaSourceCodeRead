package anla.study.JavaSourceCodeRead;

import java.util.concurrent.Exchanger;

public class ExchangerTest {

	static class Producer implements Runnable {
		private String buffer;
		private Exchanger<String> exchanger;
		Producer(String buffer, Exchanger<String> exchanger) {
			this.buffer = buffer;
			this.exchanger = exchanger;
		}
		public void run() {
			for (int i = 1; i < 5; i++) {
				try {
					System.out.println("生产者第" + i + "次生产");
					exchanger.exchange(buffer);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	static class Consumer implements Runnable {
		private String buffer;
		private final Exchanger<String> exchanger;
		public Consumer(String buffer, Exchanger<String> exchanger) {
			this.buffer = buffer;
			this.exchanger = exchanger;
		}
		public void run() {
			for (int i = 1; i < 5; i++) {
				// 调用exchange()与消费者进行数据交换
				try {
					buffer = exchanger.exchange(buffer);
					System.out.println("消费者第" + i + "次消费");
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public static void main(String[] args) throws Exception {
		String buffer1 = new String();
		String buffer2 = new String();
		Exchanger<String> exchanger = new Exchanger<String>();
		Thread producerThread = new Thread(new Producer(buffer1, exchanger));
		Thread consumerThread = new Thread(new Consumer(buffer2, exchanger));

		producerThread.start();
		consumerThread.start();
	}
}