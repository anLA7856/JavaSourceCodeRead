package anla.study.JavaSourceCodeRead;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class ExchangerTest6 {
	static ReentrantLock lock = new ReentrantLock();

	static class Producer implements Runnable {

		// 锁。

		// 生产者、消费者交换的数据结构
		private List<String> buffer;

		// 步生产者和消费者的交换对象
		private Exchanger<List<String>> exchanger;

		Producer(List<String> buffer, Exchanger<List<String>> exchanger) {
			this.buffer = buffer;
			this.exchanger = exchanger;
		}

		public void run() {
			for (int i = 1; i < 5; i++) {
				try {

					System.out.println("生产者第" + i + "次提供");
					exchanger.exchange(buffer);

				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	static class Consumer implements Runnable {
		private List<String> buffer;

		private final Exchanger<List<String>> exchanger;

		public Consumer(List<String> buffer, Exchanger<List<String>> exchanger) {
			this.buffer = buffer;
			this.exchanger = exchanger;
		}

		public void run() {
			for (int i = 1; i < 5; i++) {
				// 调用exchange()与消费者进行数据交换
				try {
					lock.lock();
					buffer = exchanger.exchange(buffer);
					System.out.println("消费者第" + i + "次提取");

				} catch (InterruptedException e) {
					e.printStackTrace();
				} finally {
					lock.unlock();
				}

			}
		}
	}

	public static void main(String[] args) throws Exception {
		List<String> buffer1 = new ArrayList<String>();
		List<String> buffer2 = new ArrayList<String>();

		Exchanger<List<String>> exchanger = new Exchanger<List<String>>();

		Thread producerThread = new Thread(new Producer(buffer1, exchanger));
		Thread consumerThread = new Thread(new Consumer(buffer2, exchanger));

		producerThread.start();

		consumerThread.start();
	}
}