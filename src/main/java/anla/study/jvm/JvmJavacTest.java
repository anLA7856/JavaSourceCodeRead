/**
 * @user anLA7856
 * @time 18-4-21 上午10:06
 */
package anla.study.jvm;

public class JvmJavacTest {
    private static final int MAX_COUNT=1000;
    private static int count=0;
    public int bar() throws Exception{
        if(++count >= MAX_COUNT){
            count=0;
            throw new Exception("count overflow");
        }
        return count;
    }

}
