package anla.study.JavaSourceCodeRead;

public class JoinCountDownLatchTest {

	public static void main(String[] args) throws InterruptedException {

		Thread thread1 = new Thread(new Runnable() {
			public void run() {
				System.out.println("parser1 finish");
			}
		});
		Thread thread2 = new Thread(new Runnable() {
			public void run() {
				System.out.println("parser2 finish");
			}
		});
		thread1.start();
		thread2.start();
		thread1.join();
		thread2.join();
		System.out.println("all parser finish");
	}
}