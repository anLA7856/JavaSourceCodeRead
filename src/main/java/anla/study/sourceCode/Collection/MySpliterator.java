package anla.study.sourceCode.Collection;

import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;

public class MySpliterator {
	public static void main(String[] args) {
		List<Character> arrs = new ArrayList<Character>();
		for (int i = 97; i < 122; i++) {
			arrs.add((char) i);
		}
		
		// 此时结果：spliterator1:index=0，fence=-1
				//由源码可知，-1也就是代表集合长度，即25
		Spliterator<Character> spliterator1 = arrs.spliterator();

		// 此时结果：spliterator2:index=0，fence=12
		//    	   spliterator1:index=12，fence=25,
		Spliterator<Character> spliterator2 = spliterator1.trySplit();
		
		// 此时结果：spliterator3：index=12，fence=18
		// 而	   spliterator1：index=18，fence=25
		Spliterator<Character> spliterator3 = spliterator1.trySplit();

		// 此时结果：spliterator4：index=18，fence=21,
		//		   spliterator1：index=21，fence=25,
		Spliterator<Character> spliterator4 = spliterator1.trySplit();
	
		
	}
}