package anla.study.sourceCode.Concurrent;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;




/**
 * 内存一致性，装入数据，在另一个线程获取数据之前。
 * 不会出现ABA问题。
 */
public class Exchanger<V> {

    /**
     * The byte distance (as a shift value) between any two used slots
     * in the arena.  1 << ASHIFT should be at least cacheline size.
     * 
     * 字节长度。
     */
    private static final int ASHIFT = 7;

    /**
     * The maximum supported arena index. The maximum allocatable
     * arena size is MMASK + 1. Must be a power of two minus one, less
     * than (1<<(31-ASHIFT)). The cap of 255 (0xff) more than suffices
     * for the expected scaling limits of the main algorithms.
     * 
     * arena的最大长度。
     * aren的size必须是2的倍数，并且小于1<<24.
     */
    private static final int MMASK = 0xff;

    /**
     * Unit for sequence/version bits of bound field. Each successful
     * change to the bound also adds SEQ.
     * 
     * 顺序单元，每个成功的change，都要加上SEQ数值
     */
    private static final int SEQ = MMASK + 1;

    /** The number of CPUs, for sizing and spin control 
     * cpu数量
     * */
    private static final int NCPU = Runtime.getRuntime().availableProcessors();

    /**
     * The maximum slot index of the arena: The number of slots that
     * can in principle hold all threads without contention, or at
     * most the maximum indexable value.
     * 
     * arena里面最大的slot数量。
     */
    static final int FULL = (NCPU >= (MMASK << 1)) ? MMASK : NCPU >>> 1;

    /**
     * The bound for spins while waiting for a match. The actual
     * number of iterations will on average be about twice this value
     * due to randomization. Note: Spinning is disabled when NCPU==1.
     * 
     * 
     * 自旋次数。
     * 
     * 当cpu为1时候，不自旋。
     */
    private static final int SPINS = 1 << 10;

    /**
     * Value representing null arguments/returns from public
     * methods. Needed because the API originally didn't disallow null
     * arguments, which it should have.
     * 
     * 代表着null的object。
     */
    private static final Object NULL_ITEM = new Object();

    /**
     * Sentinel value returned by internal exchange methods upon
     * timeout, to avoid need for separate timed versions of these
     * methods.
     * 
     * 一旦超时，就返回这个。
     */
    private static final Object TIMED_OUT = new Object();

    /**
     * Nodes hold partially exchanged data, plus other per-thread
     * bookkeeping. Padded via @sun.misc.Contended to reduce memory
     * contention.
     * 
     * 防止伪共享。
     * 对node是
     */
    @sun.misc.Contended static final class Node {
    	/**
    	 * node在arena数组里面的索引
    	 */
        int index;              // Arena index 索引
        int bound;              // Last recorded value of Exchanger.bound 最后的exchanger的记录值。
        int collides;           // Number of CAS failures at current bound  如果CAS失败，就冲突
        int hash;               // Pseudo-random for spins  伪随机数的自旋，用于设定自旋次数。
        /**
         * 自己的资源
         */
        Object item;            // This thread's current item  线程的当前对象
        /**
         * 对方的资源
         */
        volatile Object match;  // Item provided by releasing thread  被释放线程提供的对象唉嗯
        volatile Thread parked; // Set to this thread when parked, else null 当park时候，就把当前线程设置进去，否则为null。  
    }

    /** The corresponding thread local class 
     * ThreadLocal对象，里面放Node。
     * */
    static final class Participant extends ThreadLocal<Node> {
        public Node initialValue() { return new Node(); }
    }

    /**
     * Per-thread state
     * 
     * 线程状态。
     */
    private final Participant participant;

    /**
     * Elimination array; null until enabled (within slotExchange).
     * Element accesses use emulation of volatile gets and CAS.
     * 
     * valatile方式，并且CAS方式更改。
     */
    private volatile Node[] arena;

    /**
     * Slot used until contention detected.
     * 
     * 开始用slot，知道冲突了就更改。
     */
    private volatile Node slot;

    /**
     * The index of the largest valid arena position, OR'ed with SEQ
     * number in high bits, incremented on each update.  The initial
     * update from 0 to SEQ is used to ensure that the arena array is
     * constructed only once.
     * 
     * 
     * arena最大的位置，
     * 范围是0,SEQ，来保证arena每个数组只初始化一次。
     */
    private volatile int bound;

    /**
     * Exchange function when arenas enabled. See above for explanation.
     *
     * @param item the (non-null) item to exchange
     * @param timed true if the wait is timed
     * @param ns if timed, the maximum wait time, else 0L
     * @return the other thread's item; or null if interrupted; or
     * TIMED_OUT if timed and timed out
     * 
     * 
     * 
     * 当是启用了arenas的时候，的更换方法。保存above。
     * 也就是并发大时候，把slot换为数组操作。
     */
    private final Object arenaExchange(Object item, boolean timed, long ns) {
        Node[] a = arena;   //本地获取arena
        Node p = participant.get();      //获取当前线程的node节点。
        for (int i = p.index;;) {                      // 获得p在arena的索引
            int b, m, c; long j;                       //j是偏移量
            Node q = (Node)U.getObjectVolatile(a, j = (i << ASHIFT) + ABASE);   //CAS方式从数组a里面获取q
            if (q != null && U.compareAndSwapObject(a, j, q, null)) {         //q不为null，就去跟它交换，并且置null
                Object v = q.item;                     // 获取它的item
                q.match = item;                   //把自己的item给他
                Thread w = q.parked;               //获取w并且唤醒它。
                if (w != null)
                    U.unpark(w);
                return v;
            }
            else if (i <= (m = (b = bound) & MMASK) && q == null) {
            	//q为null，就说明这个位置没人，我就占这儿。
                p.item = item;                         // 自己要等待嘛，所以把自己的node节点的item，放入传入的item
                if (U.compareAndSwapObject(a, j, null, p)) {       //CAS方式，把p更换null。即尝试去占坑
                    long end = (timed && m == 0) ? System.nanoTime() + ns : 0L;   //如果有，获取end时间
                    Thread t = Thread.currentThread(); // wait  获取当前线程
                    for (int h = p.hash, spins = SPINS;;) {  //自旋操作
                        Object v = p.match;
                        if (v != null) {             
                        	//p的match不为null，说明自旋时候找到了配对的对方。需要做的就是把东西带走，坑置空，腾出位置
                            U.putOrderedObject(p, MATCH, null); //清空一些信息
                            p.item = null;             // clear for next use
                            p.hash = h;
                            return v;
                        }
                        else if (spins > 0) {
                        	//伪随机发，有经验的去将当前线程挂起，设定自旋
                            h ^= h << 1; h ^= h >>> 3; h ^= h << 10; // xorshift
                            if (h == 0)                // initialize hash
                                h = SPINS | (int)t.getId();
                            else if (h < 0 &&          // approx 50% true
                                     (--spins & ((SPINS >>> 1) - 1)) == 0)
                                Thread.yield();        // 睡眠一会
                        }
                        else if (U.getObjectVolatile(a, j) != p)
                            spins = SPINS;       // 如果不是自己，则继续自旋。
                        else if (!t.isInterrupted() && m == 0 &&
                                 (!timed ||
                                  (ns = end - System.nanoTime()) > 0L)) {
                        	
                        	//等了多次没等到，到时间了，那就挂起。免得浪费资源
                            U.putObject(t, BLOCKER, this); // emulate LockSupport
                            p.parked = t;              // minimize window
                            if (U.getObjectVolatile(a, j) == p)
                                U.park(false, ns);
                            p.parked = null;
                            U.putObject(t, BLOCKER, null);
                        }
                        else if (U.getObjectVolatile(a, j) == p &&
                                 U.compareAndSwapObject(a, j, p, null)) {
                        	//当前位置j仍然是p，并且成功把p换为了null。也就是放弃，并重新找个位置开始
                            if (m != 0)                // try to shrink
                                U.compareAndSwapInt(this, BOUND, b, b + SEQ - 1);
                            p.item = null;
                            p.hash = h;
                            i = p.index >>>= 1;        // 减半，
                            if (Thread.interrupted())
                                return null;
                            if (timed && m == 0 && ns <= 0L)  //超时返回空
                                return TIMED_OUT;
                            break;                     // expired; restart 重新开始
                        }
                    }
                }
                else
                    p.item = null;                     // 没有占坑成功，那么就不换。
            }
            else {
            	//需要的这个index，有人
                if (p.bound != b) {                    // stale; reset 重置
                    p.bound = b;
                    p.collides = 0;
                    i = (i != m || m == 0) ? m : m - 1;
                }
                else if ((c = p.collides) < m || m == FULL ||
                         !U.compareAndSwapInt(this, BOUND, b, b + SEQ + 1)) {
                	//CAS失败，增加冲突值。
                    p.collides = c + 1;
                    i = (i == 0) ? m : i - 1;          // cyclically traverse
                }
                else
                    i = m + 1;                         // grow
                p.index = i;
            }
        }
    }

    /**
     * Exchange function used until arenas enabled. See above for explanation.
     *
     * @param item the item to exchange
     * @param timed true if the wait is timed
     * @param ns if timed, the maximum wait time, else 0L
     * @return the other thread's item; or null if either the arena
     * was enabled or the thread was interrupted before completion; or
     * TIMED_OUT if timed and timed out
     * 
     * 当没有冲突时候，也就是只有slot来交换数据的时候。
     */
    private final Object slotExchange(Object item, boolean timed, long ns) {
        Node p = participant.get();   //获取当前线程私有的node
        Thread t = Thread.currentThread();   //当前线程
        if (t.isInterrupted()) // preserve interrupt status so caller can recheck  如果已经中断了。
            return null;

        for (Node q;;) {
            if ((q = slot) != null) {    //slot不为null时候，有人已经占了坑
                if (U.compareAndSwapObject(this, SLOT, q, null)) {  //null去替换q。也就是把这个slot置空，因为我来找你交换了啊，所以不用站这里了
                    Object v = q.item;   //记录相关slot里面线程所持有的数据。
                    q.match = item;    //我把你的也获取到。
                    Thread w = q.parked;  //交换完东西，唤醒你。
                    if (w != null)
                        U.unpark(w);
                    return v;
                }
                // create arena on contention, but continue until slot null，
                //如果走到这一步，就说明CAS失败了，判断是否需要用arena数组来支持。
                if (NCPU > 1 && bound == 0 &&      
                    U.compareAndSwapInt(this, BOUND, 0, SEQ))     //用SEQ去替换0
                    arena = new Node[(FULL + 2) << ASHIFT];     //初始化arena数组
            }
            else if (arena != null)
                return null; // caller must reroute to arenaExchange   //slot为null，但是arena不为空，那么就退出去执行arenaExchange方法。
            else {
            	//slot为null，arena也为null，那么就说明现在没有线程到，当前线程是第一个到的，所以把p也就是threadLocal里面东西存到slot里面。
                p.item = item;
                if (U.compareAndSwapObject(this, SLOT, null, p))
                    break;
                p.item = null;
            }
        }

        // await release 等待去释放。
        int h = p.hash;
        long end = timed ? System.nanoTime() + ns : 0L;       //如果设定有超时获取时间。
        int spins = (NCPU > 1) ? SPINS : 1;     //设定自旋，如果是单核则次数为1
        Object v;
        while ((v = p.match) == null) {
        	//p为当前线程的node，v即对方的资源为null，所以没有来，我就自旋等会。
            if (spins > 0) {
            	//选择一个自旋次数
                h ^= h << 1; h ^= h >>> 3; h ^= h << 10;
                if (h == 0)
                    h = SPINS | (int)t.getId();
                else if (h < 0 && (--spins & ((SPINS >>> 1) - 1)) == 0)
                	//休息一会
                    Thread.yield();
            }
            else if (slot != p)
            	//这个slot不是自己，被别人抢走了。
                spins = SPINS;
            else if (!t.isInterrupted() && arena == null &&
                     (!timed || (ns = end - System.nanoTime()) > 0L)) {
            	//没有中断，且没有超时，那么你就park吧。
            	//park过程。
                U.putObject(t, BLOCKER, this);
                p.parked = t;
                if (slot == p)
                    U.park(false, ns);
                p.parked = null;
                U.putObject(t, BLOCKER, null);
            }
            else if (U.compareAndSwapObject(this, SLOT, p, null)) {   
            	//成功把slot置空，那么就跳出循环，此时要么返回超时，要么返回空。
                v = timed && ns <= 0L && !t.isInterrupted() ? TIMED_OUT : null;
                break;
            }
        }
        //CAS防止重排序法，把match设为null，因为什么也没拿到，拿到不会走着条路。
        U.putOrderedObject(p, MATCH, null);
        p.item = null;
        p.hash = h;
        return v;
    }

    /**
     * Creates a new Exchanger.
     * 构造方法，同时会有一个新的Participant。
     * 
     */
    public Exchanger() {
        participant = new Participant();
    }

    /**
     * 等待另一个线程到达这个交换点。除非中断interrupt。
     * 
     * 然后就获得另一个线程的数据。
     * 
     * Waits for another thread to arrive at this exchange point (unless
     * the current thread is {@linkplain Thread#interrupt interrupted}),
     * and then transfers the given object to it, receiving its object
     * in return.
     *
     * <p>If another thread is already waiting at the exchange point then
     * it is resumed for thread scheduling purposes and receives the object
     * passed in by the current thread.  The current thread returns immediately,
     * receiving the object passed to the exchange by that other thread.
     *
     * <p>If no other thread is already waiting at the exchange then the
     * current thread is disabled for thread scheduling purposes and lies
     * dormant until one of two things happens:
     * <ul>
     * <li>Some other thread enters the exchange; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread.
     * </ul>
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting
     * for the exchange,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * @param x the object to exchange
     * @return the object provided by the other thread
     * @throws InterruptedException if the current thread was
     *         interrupted while waiting
     */
    @SuppressWarnings("unchecked")
    public V exchange(V x) throws InterruptedException {
        Object v;
        Object item = (x == null) ? NULL_ITEM : x; // translate null args 判断x是否为null。
        if ((arena != null ||
             (v = slotExchange(item, false, 0L)) == null) &&
            ((Thread.interrupted() || // disambiguates null return
              (v = arenaExchange(item, false, 0L)) == null)))
            throw new InterruptedException();
        return (v == NULL_ITEM) ? null : (V)v;
    }

    /**
     * 
     * 带有超时时间的exchange方法。
     * Waits for another thread to arrive at this exchange point (unless
     * the current thread is {@linkplain Thread#interrupt interrupted} or
     * the specified waiting time elapses), and then transfers the given
     * object to it, receiving its object in return.
     *
     * <p>If another thread is already waiting at the exchange point then
     * it is resumed for thread scheduling purposes and receives the object
     * passed in by the current thread.  The current thread returns immediately,
     * receiving the object passed to the exchange by that other thread.
     *
     * <p>If no other thread is already waiting at the exchange then the
     * current thread is disabled for thread scheduling purposes and lies
     * dormant until one of three things happens:
     * <ul>
     * <li>Some other thread enters the exchange; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread; or
     * <li>The specified waiting time elapses.
     * </ul>
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting
     * for the exchange,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * <p>If the specified waiting time elapses then {@link
     * TimeoutException} is thrown.  If the time is less than or equal
     * to zero, the method will not wait at all.
     *
     * @param x the object to exchange
     * @param timeout the maximum time to wait
     * @param unit the time unit of the {@code timeout} argument
     * @return the object provided by the other thread
     * @throws InterruptedException if the current thread was
     *         interrupted while waiting
     * @throws TimeoutException if the specified waiting time elapses
     *         before another thread enters the exchange
     */
    @SuppressWarnings("unchecked")
    public V exchange(V x, long timeout, TimeUnit unit)
        throws InterruptedException, TimeoutException {
        Object v;
        Object item = (x == null) ? NULL_ITEM : x;
        long ns = unit.toNanos(timeout);
        if ((arena != null ||
             (v = slotExchange(item, true, ns)) == null) &&
            ((Thread.interrupted() ||
              (v = arenaExchange(item, true, ns)) == null)))
            throw new InterruptedException();
        if (v == TIMED_OUT)
            throw new TimeoutException();
        return (v == NULL_ITEM) ? null : (V)v;
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe U;
    private static final long BOUND;
    private static final long SLOT;
    private static final long MATCH;
    private static final long BLOCKER;
    private static final int ABASE;
    static {
        int s;
        try {
            U = sun.misc.Unsafe.getUnsafe();
            Class<?> ek = Exchanger.class;
            Class<?> nk = Node.class;
            Class<?> ak = Node[].class;
            Class<?> tk = Thread.class;
            BOUND = U.objectFieldOffset
                (ek.getDeclaredField("bound"));
            SLOT = U.objectFieldOffset
                (ek.getDeclaredField("slot"));
            MATCH = U.objectFieldOffset
                (nk.getDeclaredField("match"));
            BLOCKER = U.objectFieldOffset
                (tk.getDeclaredField("parkBlocker"));
            s = U.arrayIndexScale(ak);
            // ABASE absorbs padding in front of element 0
            ABASE = U.arrayBaseOffset(ak) + (1 << ASHIFT);

        } catch (Exception e) {
            throw new Error(e);
        }
        if ((s & (s-1)) != 0 || s > (1 << ASHIFT))
            throw new Error("Unsupported array scale");
    }

}
