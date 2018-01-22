package anla.study.sourceCode.Concurrent;


import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.*;

/**
 * 
 * 每一个线程的插入必须和另一个线程去除对应。
 * 
 * 没有多余的外部空间，不能peek，因为插入成功的条件是去除成功。可以理解为那一刻。
 * 
 * 不能呢个iterator，因为没有任何东西来iterator。
 * 
 * 不允许null元素。
 *  
 *Synchronous queue和CSP，Ada管道很相似
 *
 *用于无连接的设计很耗。
 *
 *支持公平性等待和非公平性等待。
 *
 * 
 * @since 1.5
 * @author Doug Lea and Bill Scherer and Michael Scott
 * @param <E> the type of elements held in this collection
 */
public class SynchronousQueue<E> extends AbstractQueue<E>
    implements BlockingQueue<E>, java.io.Serializable {
    private static final long serialVersionUID = -3223113410248163686L;

    /*
     * 
     * fifo维持着更高的吞吐量。
     * 
     * 
     * This class implements extensions of the dual stack and dual
     * queue algorithms described in "Nonblocking Concurrent Objects
     * with Condition Synchronization", by W. N. Scherer III and
     * M. L. Scott.  18th Annual Conf. on Distributed Computing,
     * Oct. 2004 (see also
     * http://www.cs.rochester.edu/u/scott/synchronization/pseudocode/duals.html).
     * The (Lifo) stack is used for non-fair mode, and the (Fifo)
     * queue for fair mode. The performance of the two is generally
     * similar. Fifo usually supports higher throughput under
     * contention but Lifo maintains higher thread locality in common
     * applications.
     *
     * A dual queue (and similarly stack) is one that at any given
     * time either holds "data" -- items provided by put operations,
     * or "requests" -- slots representing take operations, or is
     * empty. A call to "fulfill" (i.e., a call requesting an item
     * from a queue holding data or vice versa) dequeues a
     * complementary node.  The most interesting feature of these
     * queues is that any operation can figure out which mode the
     * queue is in, and act accordingly without needing locks.
     *
     * Both the queue and stack extend abstract class Transferer
     * defining the single method transfer that does a put or a
     * take. These are unified into a single method because in dual
     * data structures, the put and take operations are symmetrical,
     * so nearly all code can be combined. The resulting transfer
     * methods are on the long side, but are easier to follow than
     * they would be if broken up into nearly-duplicated parts.
     *
     * The queue and stack data structures share many conceptual
     * similarities but very few concrete details. For simplicity,
     * they are kept distinct so that they can later evolve
     * separately.
     *
     * The algorithms here differ from the versions in the above paper
     * in extending them for use in synchronous queues, as well as
     * dealing with cancellation. The main differences include:
     *
     *  1. The original algorithms used bit-marked pointers, but
     *     the ones here use mode bits in nodes, leading to a number
     *     of further adaptations.
     *  2. SynchronousQueues must block threads waiting to become
     *     fulfilled.
     *  3. Support for cancellation via timeout and interrupts,
     *     including cleaning out cancelled nodes/threads
     *     from lists to avoid garbage retention and memory depletion.
     *
     * Blocking is mainly accomplished using LockSupport park/unpark,
     * except that nodes that appear to be the next ones to become
     * fulfilled first spin a bit (on multiprocessors only). On very
     * busy synchronous queues, spinning can dramatically improve
     * throughput. And on less busy ones, the amount of spinning is
     * small enough not to be noticeable.
     *
     * Cleaning is done in different ways in queues vs stacks.  For
     * queues, we can almost always remove a node immediately in O(1)
     * time (modulo retries for consistency checks) when it is
     * cancelled. But if it may be pinned as the current tail, it must
     * wait until some subsequent cancellation. For stacks, we need a
     * potentially O(n) traversal to be sure that we can remove the
     * node, but this can run concurrently with other threads
     * accessing the stack.
     *
     * While garbage collection takes care of most node reclamation
     * issues that otherwise complicate nonblocking algorithms, care
     * is taken to "forget" references to data, other nodes, and
     * threads that might be held on to long-term by blocked
     * threads. In cases where setting to null would otherwise
     * conflict with main algorithms, this is done by changing a
     * node's link to now point to the node itself. This doesn't arise
     * much for Stack nodes (because blocked threads do not hang on to
     * old head pointers), but references in Queue nodes must be
     * aggressively forgotten to avoid reachability of everything any
     * node has ever referred to since arrival.
     */

    /**
     * Shared internal API for dual stacks and queues.
     * 
     * 外部方法，为双向stacks和queues。也就是栈和队列的服transferer方法。
     * 里面只有一个方法。
     */
    abstract static class Transferer<E> {
        /**
         * Performs a put or take.
         *
         * @param e if non-null, the item to be handed to a consumer;
         *          if null, requests that transfer return an item
         *          offered by producer.
         * @param timed if this operation should timeout
         * @param nanos the timeout, in nanoseconds
         * @return if non-null, the item provided or received; if null,
         *         the operation failed due to timeout or interrupt --
         *         the caller can distinguish which of these occurred
         *         by checking Thread.interrupted.
         */
        abstract E transfer(E e, boolean timed, long nanos);
    }

    /** The number of CPUs, for spin control 
     * cpu的数量
     * */
    static final int NCPUS = Runtime.getRuntime().availableProcessors();

    /**
     * The number of times to spin before blocking in timed waits.
     * The value is empirically derived -- it works well across a
     * variety of processors and OSes. Empirically, the best value
     * seems not to vary with number of CPUs (beyond 2) so is just
     * a constant.
     * 
     * 最大自旋次数。当blocking或者等待时候。
     */
    static final int maxTimedSpins = (NCPUS < 2) ? 0 : 32;

    /**
     * The number of times to spin before blocking in untimed waits.
     * This is greater than timed value because untimed waits spin
     * faster since they don't need to check times on each spin.
     * 
     * 当unitmed waits时候的自旋次数，这个比上一个maxTimedSpins长一些。
     * 因为和它相关的操作更加块。
     */
    static final int maxUntimedSpins = maxTimedSpins * 16;

    /**
     * The number of nanoseconds for which it is faster to spin
     * rather than to use timed park. A rough estimate suffices.
     * 
     * Threshold的自旋时间。
     */
    static final long spinForTimeoutThreshold = 1000L;

    /** Dual stack 
     * 
     * 双向的栈，栈的原理是LIFO，后进先出。
     * */
    static final class TransferStack<E> extends Transferer<E> {
        /*
         * This extends Scherer-Scott dual stack algorithm, differing,
         * among other ways, by using "covering" nodes rather than
         * bit-marked pointers: Fulfilling operations push on marker
         * nodes (with FULFILLING bit set in mode) to reserve a spot
         * to match a waiting node.
         */

        /* Modes for SNodes, ORed together in node fields */
        /** Node represents an unfulfilled consumer
         * 
         *  代表未完成的消费者
         *  */
        static final int REQUEST    = 0;
        /** Node represents an unfulfilled producer
         * 代表未完成的生产者
         *  */
        static final int DATA       = 1;
        /** Node is fulfilling another unfulfilled DATA or REQUEST
         * 代表另一个未完成的生产者或者消费者。
         *  */
        static final int FULFILLING = 2;

        /** Returns true if m has fulfilling bit set. 
         * 返回true说明已经完成位设置。
         * */
        static boolean isFulfilling(int m) { return (m & FULFILLING) != 0; }

        /** Node class for TransferStacks. 
         * 栈的node，stack的node。
         * */
        static final class SNode {
            volatile SNode next;        // next node in stack    下一个节点。
            volatile SNode match;       // the node matched to this    和本节点配对的节点。
            volatile Thread waiter;     // to control park/unpark    当前线程，也就是需要被pack或者unpack的节点。
            Object item;                // data; or null for REQUESTs     //传输的数据。
            int mode;                  //模式。？
            // Note: item and mode fields don't need to be volatile
            // since they are always written before, and read after,
            // other volatile/atomic operations.

            /**
             * 构造方法
             * @param item
             */
            SNode(Object item) {
                this.item = item;
            }

            /**
             * 把val接入到cmp后面一个节点。
             * @param cmp
             * @param val
             * @return
             */
            boolean casNext(SNode cmp, SNode val) {
                return cmp == next &&
                    UNSAFE.compareAndSwapObject(this, nextOffset, cmp, val);
            }

            /**
             * Tries to match node s to this node, if so, waking up thread.
             * Fulfillers call tryMatch to identify their waiters.
             * Waiters block until they have been matched.
             *
             * @param s the node to match
             * @return true if successfully matched to s
             * 
             * 尝试去匹配某个Snode s，如果是这个节点，就唤醒它，
             */
            boolean tryMatch(SNode s) {
                if (match == null &&
                    UNSAFE.compareAndSwapObject(this, matchOffset, null, s)) {           //如果当前match还没有被匹配占坑，那么就把s来占坑。
                    Thread w = waiter;                                      //waiter估计不是当前节点。
                    if (w != null) {    // waiters need at most one unpark
                        waiter = null;                   //每人等待，
                        LockSupport.unpark(w);            //唤醒
                    }
                    return true;
                }
                return match == s;
            }

            /**
             * Tries to cancel a wait by matching node to itself.
             * 
             * 尝试取消，就是把match换为自己。自己匹配自己，。前提是match为null。
             * 也就是说，如果已经匹配了，那么这个方法不能取消。
             */
            void tryCancel() {
                UNSAFE.compareAndSwapObject(this, matchOffset, null, this);
            }

            /**
             * 和上一个方法对比，则可以知道，判断match是不是自己。
             * @return
             */
            boolean isCancelled() {
                return match == this;
            }

            // Unsafe mechanics
            private static final sun.misc.Unsafe UNSAFE;
            private static final long matchOffset;
            private static final long nextOffset;

            static {
                try {
                    UNSAFE = sun.misc.Unsafe.getUnsafe();
                    Class<?> k = SNode.class;
                    matchOffset = UNSAFE.objectFieldOffset
                        (k.getDeclaredField("match"));
                    nextOffset = UNSAFE.objectFieldOffset
                        (k.getDeclaredField("next"));
                } catch (Exception e) {
                    throw new Error(e);
                }
            }
        }

        /** The head (top) of the stack
         * SNode的头节点。
         *  */
        volatile SNode head;

        
        /**
         * 替换头节点。
         * @param h
         * @param nh
         * @return
         */
        boolean casHead(SNode h, SNode nh) {
            return h == head &&
                UNSAFE.compareAndSwapObject(this, headOffset, h, nh);
        }

        /**
         * Creates or resets fields of a node. Called only from transfer
         * where the node to push on stack is lazily created and
         * reused when possible to help reduce intervals between reads
         * and CASes of head and to avoid surges of garbage when CASes
         * to push nodes fail due to contention.
         * 
         * 构造方法，穿件一个snode。
         * 
         * 把next查到s的后面，e和mode都是s的
         */
        static SNode snode(SNode s, Object e, SNode next, int mode) {
            if (s == null) s = new SNode(e);
            s.mode = mode;
            s.next = next;
            return s;
        }

        /**
         * Puts or takes an item.
         * 
         * 传送或者获取一个值。
         */
        @SuppressWarnings("unchecked")
        E transfer(E e, boolean timed, long nanos) {
            /*
             * Basic algorithm is to loop trying one of three actions:
             *
             * 1. If apparently empty or already containing nodes of same
             *    mode, try to push node on stack and wait for a match,
             *    returning it, or null if cancelled.
             *    
             *    如果为empty，那么就压入栈，并且等待
             *
             * 2. If apparently containing node of complementary mode,
             *    try to push a fulfilling node on to stack, match
             *    with corresponding waiting node, pop both from
             *    stack, and return matched item. The matching or
             *    unlinking might not actually be necessary because of
             *    other threads performing action 3:
             *    刚好是互补的元素，如果已经存在元素，那么就压入一个fulfilling状态，即待完成节点进去。
             *    
             *
             * 3. If top of stack already holds another fulfilling node,
             *    help it out by doing its match and/or pop
             *    operations, and then continue. The code for helping
             *    is essentially the same as for fulfilling, except
             *    that it doesn't return the item.
             *    如果是相同mode的元素。那么就帮助这个元素去pop
             *    
             *    
             */

            SNode s = null; // constructed/reused as needed
            int mode = (e == null) ? REQUEST : DATA;            //获取mode，看是拿还是取。

            for (;;) {                                  //自旋。
                SNode h = head;
                if (h == null || h.mode == mode) {  // empty or same-mode    //空或者相同的模式。
                    if (timed && nanos <= 0) {      // can't wait    不能等待，块超时了。
                        if (h != null && h.isCancelled())                 //取消把。
                            casHead(h, h.next);     // pop cancelled node    //弹出头节点。
                        else
                            return null;
                    } else if (casHead(h, s = snode(s, e, h, mode))) {        //否则，那么就在头节点后面插入这个节点。
                        SNode m = awaitFulfill(s, timed, nanos);            //创建一个，等待中未完成的节点m。
                        if (m == s) {               // wait was cancelled       //如果已经被取消了，由上面可知，自己指向自己时候就是取消。
                            clean(s);
                            return null;
                        }
                        if ((h = head) != null && h.next == s)    //如果head不为null，并且s就是head后面这个，
                            casHead(h, s.next);     // help s's fulfiller        //帮忙释放掉s，也就是把s.next插入到h后面。
                        return (E) ((mode == REQUEST) ? m.item : s.item);            //返回相应的item。
                    }
                } else if (!isFulfilling(h.mode)) { // try to fulfill                //不是互补的模式，是另外一宗
                    if (h.isCancelled())            // already cancelled    //如果h取消了，那么就换下一个。
                        casHead(h, h.next);         // pop and retry
                    else if (casHead(h, s=snode(s, e, h, FULFILLING|mode))) {           //否则，那么就创建一个s，相应模式
                        for (;;) { // loop until matched or waiters disappear                //自旋，直到配对。
                            SNode m = s.next;       // m is s's match
                            if (m == null) {        // all waiters are gone      //没有等待的了，那么就退出循环。
                                casHead(s, null);   // pop fulfill node
                                s = null;           // use new node next time
                                break;              // restart main loop
                            }
                            SNode mn = m.next;                                 //s.next的next.
                            if (m.tryMatch(s)) {                               //用m的下一个用配对s
                                casHead(s, mn);     // pop both s and m      //配对成功，那么就弹出m和s，因为head是没有用的，一次弹出两个。
                                return (E) ((mode == REQUEST) ? m.item : s.item);           //返回相应元素。
                            } else                  // lost match
                                s.casNext(m, mn);   // help unlink               //帮助去连接。就算连接上了也不会出问题。
                        }
                    }
                } else {                            // help a fulfiller           //匹配上了。
                    SNode m = h.next;               // m is h's match
                    if (m == null)                  // waiter is gone       //栈里面没有任何等待者了。
                        casHead(h, null);           // pop fulfilling node
                    else {
                        SNode mn = m.next;           //看看m的下一个
                        if (m.tryMatch(h))          // help match        //匹配上了
                            casHead(h, mn);         // pop both h and m    //一次弹出2个。
                        else                        // lost match
                            h.casNext(m, mn);       // help unlink    
                    }
                }
            }
        }

        /**
         * Spins/blocks until node s is matched by a fulfill operation.
         *
         * @param s the waiting node
         * @param timed true if timed wait
         * @param nanos timeout value
         * @return matched node, or s if cancelled
         * 
         * 如果一个node处于fulfill操作，也就是未完成的等待操作时候，就是这个方法去阻塞或者等待。
         */
        SNode awaitFulfill(SNode s, boolean timed, long nanos) {
            /*
             * When a node/thread is about to block, it sets its waiter
             * field and then rechecks state at least one more time
             * before actually parking, thus covering race vs
             * fulfiller noticing that waiter is non-null so should be
             * woken.
             *
             * When invoked by nodes that appear at the point of call
             * to be at the head of the stack, calls to park are
             * preceded by spins to avoid blocking when producers and
             * consumers are arriving very close in time.  This can
             * happen enough to bother only on multiprocessors.
             *
             * The order of checks for returning out of main loop
             * reflects fact that interrupts have precedence over
             * normal returns, which have precedence over
             * timeouts. (So, on timeout, one last check for match is
             * done before giving up.) Except that calls from untimed
             * SynchronousQueue.{poll/offer} don't check interrupts
             * and don't wait at all, so are trapped in transfer
             * method rather than calling awaitFulfill.
             */
            final long deadline = timed ? System.nanoTime() + nanos : 0L;         //获得超时时间
            Thread w = Thread.currentThread();           //获得当前线程
            int spins = (shouldSpin(s) ?
                         (timed ? maxTimedSpins : maxUntimedSpins) : 0);                   //判断是否需要自旋，以及自旋的次数。注意有timed这个boolean变量。
            for (;;) {                            //自旋操作。
                if (w.isInterrupted())             //当，当前线程中断时候，就取消。
                    s.tryCancel();
                SNode m = s.match;                //获取它的match。
                if (m != null)                 //如果m匹配到了，那么返回m
                    return m;
                if (timed) {              //再次检测一次timed。
                    nanos = deadline - System.nanoTime();
                    if (nanos <= 0L) {
                        s.tryCancel();
                        continue;
                    }
                }
                if (spins > 0)                //再次检查spins。
                    spins = shouldSpin(s) ? (spins-1) : 0;
                else if (s.waiter == null)
                    s.waiter = w; // establish waiter so can park next iter
                else if (!timed)            //如果没有timed，那么直接park当前线程。
                    LockSupport.park(this);
                else if (nanos > spinForTimeoutThreshold)      //带有nanos超时时间的park。
                    LockSupport.parkNanos(this, nanos);
            }
        }

        /**
         * Returns true if node s is at head or there is an active
         * fulfiller.
         */
        boolean shouldSpin(SNode s) {        //判断s节点是否应该spin。
            SNode h = head;
            //当h==s时候，
            //当h为null，也就是为空队列，s是第一个
            //看h是否完成了位设置，完成了就可以了
            return (h == s || h == null || isFulfilling(h.mode));     
        }

        /**
         * Unlinks s from the stack.
         * 
         * 从stack里面解除s。
         */
        void clean(SNode s) {
            s.item = null;   // forget item   把item和waiter都置空。
            s.waiter = null; // forget thread

            /*
             * At worst we may need to traverse entire stack to unlink
             * s. If there are multiple concurrent calls to clean, we
             * might not see s if another thread has already removed
             * it. But we can stop when we see any node known to
             * follow s. We use s.next unless it too is cancelled, in
             * which case we try the node one past. We don't check any
             * further because we don't want to doubly traverse just to
             * find sentinel.
             */

            SNode past = s.next;         //获得下一个SNode。
            if (past != null && past.isCancelled())       //如果past被cancell了，那么就再past一个。
                past = past.next;

            // Absorb cancelled nodes at head
            SNode p;                  //从头节点开始清除。
            while ((p = head) != null && p != past && p.isCancelled())
                casHead(p, p.next);                //做的事就是，把头节点，链接到下一个节点，节点不能为cancelled。

            // Unsplice embedded nodes      把植入的节点断链。
            while (p != null && p != past) {
                SNode n = p.next;                     //在去链接头节点以后的节点，同样也不能为null。
                if (n != null && n.isCancelled())
                    p.casNext(n, n.next);
                else
                    p = n;
            }
        }

        // Unsafe mechanics
        private static final sun.misc.Unsafe UNSAFE;
        private static final long headOffset;
        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();
                Class<?> k = TransferStack.class;
                headOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("head"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    /** Dual Queue 
     * 
     * 队列，也就是先进先出。
     * */
    static final class TransferQueue<E> extends Transferer<E> {
        /*
         * This extends Scherer-Scott dual queue algorithm, differing,
         * among other ways, by using modes within nodes rather than
         * marked pointers. The algorithm is a little simpler than
         * that for stacks because fulfillers do not need explicit
         * nodes, and matching is done by CAS'ing QNode.item field
         * from non-null to null (for put) or vice versa (for take).
         */

        /** Node class for TransferQueue.
         * 用于存储的QNode的节点。
         *  */
        static final class QNode {
            volatile QNode next;          // next node in queue          //一个next
            volatile Object item;         // CAS'ed to or from null   一个item
            volatile Thread waiter;       // to control park/unpark       一个waiter
            final boolean isData;               //一个isDate，判断是否为数据。

            /**
             * 构造方法
             * @param item
             * @param isData
             */
            QNode(Object item, boolean isData) {
                this.item = item;
                this.isData = isData;
            }

            /**
             * 替换下一个节点。
             * @param cmp
             * @param val
             * @return
             */
            boolean casNext(QNode cmp, QNode val) {
                return next == cmp &&
                    UNSAFE.compareAndSwapObject(this, nextOffset, cmp, val);
            }

            /**
             * 替换item
             * @param cmp
             * @param val
             * @return
             */
            boolean casItem(Object cmp, Object val) {
                return item == cmp &&
                    UNSAFE.compareAndSwapObject(this, itemOffset, cmp, val);
            }

            /**
             * Tries to cancel by CAS'ing ref to this as item.
             * 
             * 也是把item设为自己。，前一个是把match设为自己。
             */
            void tryCancel(Object cmp) {
                UNSAFE.compareAndSwapObject(this, itemOffset, cmp, this);
            }

            boolean isCancelled() {
                return item == this;
            }

            /**
             * Returns true if this node is known to be off the queue
             * because its next pointer has been forgotten due to
             * an advanceHead operation.
             * 
             * 判断是否，这个几点已经删除了。也就是自己指向自己。
             */
            boolean isOffList() {
                return next == this;
            }

            // Unsafe mechanics
            private static final sun.misc.Unsafe UNSAFE;
            private static final long itemOffset;
            private static final long nextOffset;

            static {
                try {
                    UNSAFE = sun.misc.Unsafe.getUnsafe();
                    Class<?> k = QNode.class;
                    itemOffset = UNSAFE.objectFieldOffset
                        (k.getDeclaredField("item"));
                    nextOffset = UNSAFE.objectFieldOffset
                        (k.getDeclaredField("next"));
                } catch (Exception e) {
                    throw new Error(e);
                }
            }
        }

        /** Head of queue 
         * 头节点
         * */
        transient volatile QNode head;
        /** Tail of queue
         * 尾节点。
         *  */
        transient volatile QNode tail;
        /**
         * Reference to a cancelled node that might not yet have been
         * unlinked from queue because it was the last inserted node
         * when it was cancelled.
         * 
         * 保存一个刚刚被清除的节点。
         * 可能已经断开连接了，但是是最后一个插入的。
         */
        transient volatile QNode cleanMe;

        TransferQueue() {
            QNode h = new QNode(null, false); // initialize to dummy node.此时head节点为这个，并且把head指向它。不是data类型。
            head = h;
            tail = h;
        }

        /**
         * Tries to cas nh as new head; if successful, unlink
         * old head's next node to avoid garbage retention.
         * 
         * 尝试把nh作为新的head节点。
         */
        void advanceHead(QNode h, QNode nh) {
            if (h == head &&
                UNSAFE.compareAndSwapObject(this, headOffset, h, nh))
                h.next = h; // forget old next  自己指向自己。
        }

        /**
         * Tries to cas nt as new tail.
         * 
         * 尝试替换tail，把nt来替换tail。
         */
        void advanceTail(QNode t, QNode nt) {
            if (tail == t)
                UNSAFE.compareAndSwapObject(this, tailOffset, t, nt);
        }

        /**
         * Tries to CAS cleanMe slot.
         * 
         * 来替换cleanMe这个节点。
         */
        boolean casCleanMe(QNode cmp, QNode val) {
            return cleanMe == cmp &&
                UNSAFE.compareAndSwapObject(this, cleanMeOffset, cmp, val);
        }

        /**
         * Puts or takes an item.
         * 
         * transfer方法。
         */
        @SuppressWarnings("unchecked")
        E transfer(E e, boolean timed, long nanos) {
            /* Basic algorithm is to loop trying to take either of
             * two actions:
             *
             * 1. If queue apparently empty or holding same-mode nodes,
             *    try to add node to queue of waiters, wait to be
             *    fulfilled (or cancelled) and return matching item.
             *
             * 2. If queue apparently contains waiting items, and this
             *    call is of complementary mode, try to fulfill by CAS'ing
             *    item field of waiting node and dequeuing it, and then
             *    returning matching item.
             *
             * In each case, along the way, check for and try to help
             * advance head and tail on behalf of other stalled/slow
             * threads.
             *
             * The loop starts off with a null check guarding against
             * seeing uninitialized head or tail values. This never
             * happens in current SynchronousQueue, but could if
             * callers held non-volatile/final ref to the
             * transferer. The check is here anyway because it places
             * null checks at top of loop, which is usually faster
             * than having them implicitly interspersed.
             */

            QNode s = null; // constructed/reused as needed     
            boolean isData = (e != null);            //判断是否为数据，

            for (;;) {                           //自旋操作
                QNode t = tail;                     //分别获取tail和head。
                QNode h = head;
                if (t == null || h == null)         // saw uninitialized value
                    continue;                       // spin        如果t和h都为null，则自旋。

                if (h == t || t.isData == isData) { // empty or same-mode           //为null，或者是同样的提供者，比如都是生产者。
                    QNode tn = t.next;
                    if (t != tail)                  // inconsistent read               //不一致读，
                        continue;
                    if (tn != null) {               // lagging tail                //发现有新的tail，那么就替换，
                        advanceTail(t, tn);
                        continue;
                    }
                    if (timed && nanos <= 0)        // can't wait                 //超时了不能等待了。
                        return null;
                    if (s == null)                             //如果s为null，那么初始化它。
                        s = new QNode(e, isData);
                    if (!t.casNext(null, s))        // failed to link in        尝试把s插入到t节点的next。              
                        continue;

                    advanceTail(t, s);              // swing tail and wait          //把s插入到t后面。
                    Object x = awaitFulfill(s, e, timed, nanos);               //阻塞
                    if (x == s) {                   // wait was cancelled        如果发现被取消了，那么就调用clean方法。
                        clean(t, s);
                        return null;
                    }

                    if (!s.isOffList()) {           // not already unlinked             //如果s还在list。
                        advanceHead(t, s);          // unlink if head                 //把s来替换t，从队头插入嘛。
                        if (x != null)              // and forget fields         头节点，需要牺牲。       
                            s.item = s;
                        s.waiter = null;
                    }
                    return (x != null) ? (E)x : e;                //最终返回，x或者，e。

                } else {                            // complementary-mode      互补的模式，刚好遇到了。     
                    QNode m = h.next;               // node to fulfill          获得h的next。
                    if (t != tail || m == null || h != head)
                        continue;                   // inconsistent read         不一致读。

                    Object x = m.item;                                //获取m的item
                    if (isData == (x != null) ||    // m already fulfilled    m已经完成了。      
                        x == m ||                   // m cancelled     m已经取消
                        !m.casItem(x, e)) {         // lost CAS        CAS失败
                        advanceHead(h, m);          // dequeue and retry
                        continue;
                    }

                    advanceHead(h, m);              // successfully fulfilled     //成功的完成了。
                    LockSupport.unpark(m.waiter);                         //唤醒m的waiter。
                    return (x != null) ? (E)x : e;                        //返回。
                }
            }
        }

        /**
         * Spins/blocks until node s is fulfilled.
         *
         * @param s the waiting node
         * @param e the comparison value for checking match
         * @param timed true if timed wait
         * @param nanos timeout value
         * @return matched item, or s if cancelled
         * 
         * 自旋或者block。直到node节点fulfilled，完成。
         */
        Object awaitFulfill(QNode s, E e, boolean timed, long nanos) {
            /* Same idea as TransferStack.awaitFulfill */
            final long deadline = timed ? System.nanoTime() + nanos : 0L;    //获取超时时间
            Thread w = Thread.currentThread();            //获取当前线程。
            int spins = ((head.next == s) ?
                         (timed ? maxTimedSpins : maxUntimedSpins) : 0);            //判断spins的值
            for (;;) {                                    //自旋
                if (w.isInterrupted())                       //如果w当前线程中断，则尝试取消e。
                    s.tryCancel(e);
                Object x = s.item;
             // 如果线程进行了阻塞 -> 唤醒或者中断了，那么x != e 肯定成立，直接返回当前节点即可
                if (x != e)                      //返回x。      
                    return x;
                if (timed) {                     //超时判断
                    nanos = deadline - System.nanoTime();
                    if (nanos <= 0L) {
                        s.tryCancel(e);
                        continue;
                    }
                }
                if (spins > 0)                  //自减自旋次数。
                    --spins;
                else if (s.waiter == null)                      //设置waiter
                    s.waiter = w;
                else if (!timed)                               //阻塞当前线程。
                    LockSupport.park(this);
                else if (nanos > spinForTimeoutThreshold)           //带有超时的阻塞。
                    LockSupport.parkNanos(this, nanos);
            }
        }

        /**
         * Gets rid of cancelled node s with original predecessor pred.
         * 
         * 清理某个节点s。
         */
        void clean(QNode pred, QNode s) {
            s.waiter = null; // forget thread    //清理工作。
            /*
             * 最后一个插入的节点可能无法被删除，这个时候就需要一个cleanMe节点，用来表示前一个被删除的节点，
             * 
             * At any given time, exactly one node on list cannot be
             * deleted -- the last inserted node. To accommodate this,
             * if we cannot delete s, we save its predecessor as
             * "cleanMe", deleting the previously saved version
             * first. At least one of node s or the node previously
             * saved can always be deleted, so this always terminates.
             */
            while (pred.next == s) { // Return early if already unlinked        确保s是pred的下一个
                QNode h = head;
                QNode hn = h.next;   // Absorb cancelled first node as head            //获取head的第一个节点。
                if (hn != null && hn.isCancelled()) {            //如果hn已经被删除，那么需要把head往后面移动一个。
                    advanceHead(h, hn);
                    continue;
                }
                QNode t = tail;      // Ensure consistent read for tail       用来确保一致性。
                if (t == h)               //说明队列为null。
                    return;
                QNode tn = t.next;
                if (t != tail)                   //不一致读。
                    continue;
                if (tn != null) {                           //把tn替换为tail。
                    advanceTail(t, tn);
                    continue;
                }
                if (s != t) {        // If not tail, try to unsplice     //如果s不是尾节点，则需要把s替换。
                    QNode sn = s.next;
                    if (sn == s || pred.casNext(s, sn))
                        return;
                }
                QNode dp = cleanMe;                     //用来清除刚刚加入的节点。
                if (dp != null) {    // Try unlinking previous cancelled node
                    QNode d = dp.next;                   //d是最后一个，如果有
                    QNode dn;
                    if (d == null ||               // d is gone or   d为null
                        d == dp ||                 // d is off list or  
                        !d.isCancelled() ||        // d not cancelled or   d没有被删除。
                        (d != t &&                 // d not tail and     d不为tail。
                         (dn = d.next) != null &&  //   has successor
                         dn != d &&                //   that is on list
                         dp.casNext(d, dn)))       // d unspliced       //接触t，满足前面的答案。
                        casCleanMe(dp, null);
                    if (dp == pred)
                        return;      // s is already saved node
                } else if (casCleanMe(null, pred))       
                    return;          // Postpone cleaning s
            }
        }

        private static final sun.misc.Unsafe UNSAFE;
        private static final long headOffset;
        private static final long tailOffset;
        private static final long cleanMeOffset;
        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();
                Class<?> k = TransferQueue.class;
                headOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("head"));
                tailOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("tail"));
                cleanMeOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("cleanMe"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    /**
     * The transferer. Set only in constructor, but cannot be declared
     * as final without further complicating serialization.  Since
     * this is accessed only at most once per public method, there
     * isn't a noticeable performance penalty for using volatile
     * instead of final here.
     * 
     * 一个父类的transferer
     */
    private transient volatile Transferer<E> transferer;

    /**
     * Creates a {@code SynchronousQueue} with nonfair access policy.
     * 
     * 默认为非公平。aaaaaa
     */
    public SynchronousQueue() {
        this(false);
    }

    /**
     * Creates a {@code SynchronousQueue} with the specified fairness policy.
     *
     * @param fair if true, waiting threads contend in FIFO order for
     *        access; otherwise the order is unspecified.
     */
    public SynchronousQueue(boolean fair) {
        transferer = fair ? new TransferQueue<E>() : new TransferStack<E>();
    }

    /**
     * Adds the specified element to this queue, waiting if necessary for
     * another thread to receive it.
     *
     * @throws InterruptedException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     * 
     * 插入，调用transferer.transfer的方法。
     * 插入特定元素。
     */
    public void put(E e) throws InterruptedException {
        if (e == null) throw new NullPointerException();
        if (transferer.transfer(e, false, 0) == null) {
            Thread.interrupted();
            throw new InterruptedException();
        }
    }

    /**
     * Inserts the specified element into this queue, waiting if necessary
     * up to the specified wait time for another thread to receive it.
     *
     * @return {@code true} if successful, or {@code false} if the
     *         specified waiting time elapses before a consumer appears
     * @throws InterruptedException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     * 
     * 插入特定元素。
     */
    public boolean offer(E e, long timeout, TimeUnit unit)
        throws InterruptedException {
        if (e == null) throw new NullPointerException();
        if (transferer.transfer(e, true, unit.toNanos(timeout)) != null)
            return true;
        if (!Thread.interrupted())
            return false;
        throw new InterruptedException();
    }

    /**
     * Inserts the specified element into this queue, if another thread is
     * waiting to receive it.
     *
     * @param e the element to add
     * @return {@code true} if the element was added to this queue, else
     *         {@code false}
     * @throws NullPointerException if the specified element is null
     * 
     * 插入特定元素。
     */
    public boolean offer(E e) {
        if (e == null) throw new NullPointerException();
        return transferer.transfer(e, true, 0) != null;
    }

    /**
     * Retrieves and removes the head of this queue, waiting if necessary
     * for another thread to insert it.
     *
     * @return the head of this queue
     * @throws InterruptedException {@inheritDoc}
     * 
     * 获取特定元素。
     * 
     * 获取不到就报错。
     */
    public E take() throws InterruptedException {
        E e = transferer.transfer(null, false, 0);
        if (e != null)
            return e;
        Thread.interrupted();
        throw new InterruptedException();
    }

    /**
     * Retrieves and removes the head of this queue, waiting
     * if necessary up to the specified wait time, for another thread
     * to insert it.
     *
     * @return the head of this queue, or {@code null} if the
     *         specified waiting time elapses before an element is present
     * @throws InterruptedException {@inheritDoc}
     * 
     * 获取特定的队列第一个元素。
     */
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        E e = transferer.transfer(null, true, unit.toNanos(timeout));
        if (e != null || !Thread.interrupted())
            return e;
        throw new InterruptedException();
    }

    /**
     * Retrieves and removes the head of this queue, if another thread
     * is currently making an element available.
     *
     * @return the head of this queue, or {@code null} if no
     *         element is available
     *         
     * 返回第一个元素。
     */
    public E poll() {
        return transferer.transfer(null, true, 0);
    }

    /**
     * Always returns {@code true}.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @return {@code true}
     * 
     * 永远为null
     */
    public boolean isEmpty() {
        return true;
    }

    /**
     * Always returns zero.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @return zero
     * 
     * 永远为0
     */
    public int size() {
        return 0;
    }

    /**
     * Always returns zero.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @return zero
     * 
     * 0
     */
    public int remainingCapacity() {
        return 0;
    }

    /**
     * Does nothing.
     * A {@code SynchronousQueue} has no internal capacity.
     * 本来就是空的。
     */
    public void clear() {
    }

    /**
     * Always returns {@code false}.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @param o the element
     * @return {@code false}
     * 
     * 永远不存在
     */
    public boolean contains(Object o) {
        return false;
    }

    /**
     * Always returns {@code false}.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @param o the element to remove
     * @return {@code false}
     * 
     * 永远删除失败。
     */
    public boolean remove(Object o) {
        return false;
    }

    /**
     * Returns {@code false} unless the given collection is empty.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @param c the collection
     * @return {@code false} unless given collection is empty
     * 
     * false
     */
    public boolean containsAll(Collection<?> c) {
        return c.isEmpty();
    }

    /**
     * Always returns {@code false}.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @param c the collection
     * @return {@code false}
     * 
     * 删除所有。
     */
    public boolean removeAll(Collection<?> c) {
        return false;
    }

    /**
     * Always returns {@code false}.
     * A {@code SynchronousQueue} has no internal capacity.
     *
     * @param c the collection
     * @return {@code false}
     */
    public boolean retainAll(Collection<?> c) {
        return false;
    }

    /**
     * Always returns {@code null}.
     * A {@code SynchronousQueue} does not return elements
     * unless actively waited on.
     *
     * @return {@code null}
     * 
     * 返回null。
     */
    public E peek() {
        return null;
    }

    /**
     * Returns an empty iterator in which {@code hasNext} always returns
     * {@code false}.
     *
     * @return an empty iterator
     * 
     * 返回一个空iterator。
     * 
     * emptyiterator。
     */
    public Iterator<E> iterator() {
        return Collections.emptyIterator();
    }

    /**
     * Returns an empty spliterator in which calls to
     * {@link java.util.Spliterator#trySplit()} always return {@code null}.
     *
     * @return an empty spliterator
     * @since 1.8
     */
    public Spliterator<E> spliterator() {
        return Spliterators.emptySpliterator();
    }

    /**
     * Returns a zero-length array.
     * @return a zero-length array
     */
    public Object[] toArray() {
        return new Object[0];
    }

    /**
     * Sets the zeroeth element of the specified array to {@code null}
     * (if the array has non-zero length) and returns it.
     *
     * @param a the array
     * @return the specified array
     * @throws NullPointerException if the specified array is null
     */
    public <T> T[] toArray(T[] a) {
        if (a.length > 0)
            a[0] = null;
        return a;
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c) {
        if (c == null)
            throw new NullPointerException();
        if (c == this)
            throw new IllegalArgumentException();
        int n = 0;
        for (E e; (e = poll()) != null;) {
            c.add(e);
            ++n;
        }
        return n;
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c, int maxElements) {
        if (c == null)
            throw new NullPointerException();
        if (c == this)
            throw new IllegalArgumentException();
        int n = 0;
        for (E e; n < maxElements && (e = poll()) != null;) {
            c.add(e);
            ++n;
        }
        return n;
    }

    /*
     * To cope with serialization strategy in the 1.5 version of
     * SynchronousQueue, we declare some unused classes and fields
     * that exist solely to enable serializability across versions.
     * These fields are never used, so are initialized only if this
     * object is ever serialized or deserialized.
     */

    @SuppressWarnings("serial")
    static class WaitQueue implements java.io.Serializable { }
    static class LifoWaitQueue extends WaitQueue {
        private static final long serialVersionUID = -3633113410248163686L;
    }
    static class FifoWaitQueue extends WaitQueue {
        private static final long serialVersionUID = -3623113410248163686L;
    }
    private ReentrantLock qlock;                         //用到了reentrantlock。   就下面两个序列化方法用到。
    private WaitQueue waitingProducers;
    private WaitQueue waitingConsumers;

    /**
     * Saves this queue to a stream (that is, serializes it).
     * @param s the stream
     * @throws java.io.IOException if an I/O error occurs
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {
        boolean fair = transferer instanceof TransferQueue;
        if (fair) {
            qlock = new ReentrantLock(true);
            waitingProducers = new FifoWaitQueue();
            waitingConsumers = new FifoWaitQueue();
        }
        else {
            qlock = new ReentrantLock();
            waitingProducers = new LifoWaitQueue();
            waitingConsumers = new LifoWaitQueue();
        }
        s.defaultWriteObject();
    }

    /**
     * Reconstitutes this queue from a stream (that is, deserializes it).
     * @param s the stream
     * @throws ClassNotFoundException if the class of a serialized object
     *         could not be found
     * @throws java.io.IOException if an I/O error occurs
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        if (waitingProducers instanceof FifoWaitQueue)
            transferer = new TransferQueue<E>();
        else
            transferer = new TransferStack<E>();
    }

    // Unsafe mechanics
    static long objectFieldOffset(sun.misc.Unsafe UNSAFE,
                                  String field, Class<?> klazz) {
        try {
            return UNSAFE.objectFieldOffset(klazz.getDeclaredField(field));
        } catch (NoSuchFieldException e) {
            // Convert Exception to corresponding Error
            NoSuchFieldError error = new NoSuchFieldError(field);
            error.initCause(e);
            throw error;
        }
    }

}
