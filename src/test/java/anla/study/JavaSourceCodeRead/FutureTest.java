package anla.study.JavaSourceCodeRead;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class FutureTest {
	public static void main(String[] args) {
		CallableThreadDemo ctd = new CallableThreadDemo();         //一个Callable的线程
		FutureTask<Integer> result = new FutureTask<Integer>(ctd);  // 以Callable方式，需要FutureTask实现类的支持，用于接收运算结果
		new Thread(result).start();                       //放到Thread里面执行。
		try {
			Integer sum = result.get(); // 获取前面callable的执行结果。
			System.out.println(sum);
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
	}
}
/**
 * @author anla7856
 * 实现Callable接口，返回Integer,返回计算的1到50的和
 */
class CallableThreadDemo implements Callable<Integer> {
	@Override
	public Integer call() throws Exception {
		int sum = 0;
		for (int i = 1; i <= 50; i++) 
			sum += i;
		return sum;
	}
}


