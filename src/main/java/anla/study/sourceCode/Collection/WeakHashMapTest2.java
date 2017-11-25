package anla.study.sourceCode.Collection;

import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

public class WeakHashMapTest2 {
	public static void main(String[] args) throws InterruptedException {
		WeakHashMap<String, String> map = new WeakHashMap<String, String>();
		for(int i = 0;i < 5;i++){
			map.put(new String("字符串"+i), new String("串串"+i));
		}
		System.gc();
		TimeUnit.SECONDS.sleep(2);
		System.out.println(map.size());
		map.put(new String("字符串"+6), new String("串串"+6));
	}
}
