package anla.study.sourceCode.Collection;

import java.lang.ref.WeakReference;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;


/**
 * 1、基于map接口，是一种弱键相连
 * 2、如果没有平常使用，WeakHashMap里面的实体会自动回收
 * 3、支持 null值和null键。和HashMap有些相似
 * 4、fast-fail的
 * 5、不允许重复
 * 有同样的初始容量和加载因子。不是同步的。
 * 给定key映射的存在不会阻止该实体由垃圾收集器被丢弃
 * 
 * WeakHashMap类的行为部分取决于垃圾收集器的操作，因此几个熟悉的（但不是必需的）
 * Map不变量不适用于此类。因为垃圾收集器可能随时丢弃key，所以 WeakHashMap可能表现
 * 
 * 得像未知线程默默地删除条目一样。特别是，即使您在WeakHashMap实例上进行同步，
 * 也没有调用其mutator方法，size方法可以随着时间返回较小的值，因为isEmpty方法返回false，
 * 然后返回true，因为containsKey方法返回真，后来为假对于给定的关键，对于 GET方法返回一个值，
 * 对于给定的关键，但后来返回 空值，对于认沽方法返回 空值和删除方法返回 假的，
 * 以前似乎是在一个关键地图，以及密钥集，值集合和条目集的连续检查，以产生连续较小数量的元素。
 *
 * fast-fail的
 *

 */
public class WeakHashMapTest<K,V>
    extends AbstractMap<K,V>
    implements Map<K,V> {

    /**
     * 初始容量
     */
    private static final int DEFAULT_INITIAL_CAPACITY = 16;

    /**
     * 最大容量
     */
    private static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * 默认的加载参数
     */
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /**
     * 用来装值
     */
    Entry<K,V>[] table;

    /**
     * 大小
     */
    private int size;

    /**
     * 当到达capacity * load factor，就需要设置
     */
    private int threshold;

    /**
     * loadFactor
     */
    private final float loadFactor;

    /**
     * 若引用队列
     */
    private final ReferenceQueue<Object> queue = newferenceQueue<>();

    /**
     * modCount
     *
     */
    int modCount;

    @SuppressWarnings("unchecked")
    private Entry<K,V>[] newTable(int n) {
        return (Entry<K,V>[]) new Entry<?,?>[n];
    }

    /**
     * Constructs a new, empty <tt>WeakHashMap</tt> with the given initial
     * capacity and the given load factor.
     *
     * @param  initialCapacity The initial capacity of the <tt>WeakHashMap</tt>
     * @param  loadFactor      The load factor of the <tt>WeakHashMap</tt>
     * @throws IllegalArgumentException if the initial capacity is negative,
     *         or if the load factor is nonpositive.
     */
    public WeakHashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0)
            throw new IllegalArgumentException("Illegal Initial Capacity: "+
                                               initialCapacity);
        if (initialCapacity > MAXIMUM_CAPACITY)
            initialCapacity = MAXIMUM_CAPACITY;

        if (loadFactor <= 0 || Float.isNaN(loadFactor))
            throw new IllegalArgumentException("Illegal Load factor: "+
                                               loadFactor);
        int capacity = 1;
        while (capacity < initialCapacity)
            capacity <<= 1;
        table = newTable(capacity);
        this.loadFactor = loadFactor;
        threshold = (int)(capacity * loadFactor);
    }

    /**
     * 构造方法
     */
    public WeakHashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Constructs a new, empty <tt>WeakHashMap</tt> with the default initial
     * capacity (16) and load factor (0.75).
     */
    public WeakHashMap() {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    /**
     * 从m中构造weakhashmap
     */
    public WeakHashMap(Map<? extends K, ? extends V> m) {
        this(Math.max((int) (m.size() / DEFAULT_LOAD_FACTOR) + 1,
                DEFAULT_INITIAL_CAPACITY)
             DEFAULT_LOAD_FACTOR);
        putAll(m);
    }

    // internal utilities

    /**
     * 全局工具，用于代表null。
     */
    private static final Object NULL_KEY = new Object();

    /**
     * 判断为null
     */
    private static Object maskNull(Object key) {
        return (key == null) ? NULL_KEY : key;
    }

    /**
     * 前一个方法的逆
     */
    static Object unmaskNull(Object key) {
        return (key == NULL_KEY) ? null : key;
    }

    /**
     * 判断equals时，可以用x=y，也可以equals。
     */
    private static boolean eq(Object x, Object y) {
        return x == y || x.equals(y);
    }

    /**
     * 计算hash值，利用hashcode，在使用混淆函数。，java7中计算方式
     */
    final int hash(Object k) {
        int h = k.hashCode();
        h ^= (h >>> 20) ^ (h >>> 12);
        return h ^ (h >>> 7) ^ (h >>> 4);
    }

    /**
     * 定位到哪一个键。和java8一样
     */
    private static int indexFor(int h, int length) {
        return h & (length-1);
    }

    /**
     * 从ReferenceQueue中取出过期的entry，从WeakHashMap找到对应的entry，逐一删除
     * 注意，只会把value置为null。
     */
    private void expungeStaleEntries() {
        for (Object x; (x = queue.poll()) != null; ) {
        	//遍历queue
            synchronized (queue) {
                @SuppressWarnings("unchecked")
                    Entry<K,V> e = (Entry<K,V>) x;
                int i = indexFor(e.hash, table.length);
                Entry<K,V> prev = table[i];
                Entry<K,V> p = prev;
                while (p != null) {
                	//遍历table[i]所在链表
                    Entry<K,V> next = p.next;
                    if (p == e) {
                    	//queue里面有e，那就删了。
                        if (prev == e)
                        	//e就是当前的p.next
                            table[i] = next;
                        else
                            prev.next = next;
                        // Must not null out e.next;
                        // stale entries may be in use by a HashIterator
                        //置为null，帮助gc。
                        e.value = null; // Help GC
                        //设置e的value，但是没看到设置e的key。
                        size--;
                        break;
                    }
                    prev = p;
                    p = next;
                }
            }
        }
    }

    /**
     * 获得table，执行以便清洗方法，执行一遍expungeStaleEntries
     */
    private Entry<K,V>[] getTable() {
        expungeStaleEntries();
        return table;
    }

    /**
     * 获得大小也执行
     */
    public int size() {
        if (size == 0)
            return 0;
        expungeStaleEntries();
        return size;
    }

    /**
     * 判空
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     *获取某一个值。
     *所以 在weakHashMap中，对于冲突部分，所以还是只用了链表进行连接。
     */
    public V get(Object key) {
        Object k = maskNull(key);
        int h = hash(k);
        Entry<K,V>[] tab = getTable();
        int index = indexFor(h, tab.length);
        Entry<K,V> e = tab[index];
        while (e != null) {
            if (e.hash == h && eq(k, e.get()))
                return e.value;
            e = e.next;
        }
        return null;
    }

    /**
     * 判断是否包含
     */
    public boolean containsKey(Object key) {
        return getEntry(key) != null;
    }

    /**
     * 算hash值，然后找
     */
    Entry<K,V> getEntry(Object key) {
        Object k = maskNull(key);
        int h = hash(k);
        Entry<K,V>[] tab = getTable();
        int index = indexFor(h, tab.length);
        Entry<K,V> e = tab[index];
        while (e != null && !(e.hash == h && eq(k, e.get())))
            e = e.next;
        return e;
    }

    /**
     * 向老集合中插入新值。如果有就会重复。
     */
    public V put(K key, V value) {
        Object k = maskNull(key);
        int h = hash(k);
        Entry<K,V>[] tab = getTable();
        int i = indexFor(h, tab.length);
        //找到table下标后，插入到链表中
        for (Entry<K,V> e = tab[i]; e != null; e = e.next) {
            if (h == e.hash && eq(k, e.get())) {
                V oldValue = e.value;
                if (value != oldValue)
                    e.value = value;
                return oldValue;
            }
        }

        modCount++;
        Entry<K,V> e = tab[i];
        //没丢入一个，都把一个引用队列丢进去
        tab[i] = new Entry<>(k, value, queue, h, e);
        if (++size >= threshold)
        	//扩容。后扩容，而不是想hashmap一样，先扩容。
            resize(tab.length * 2);
        return null;
    }

    /**
     *重新设置table的大小值，不能被外面的访问
     */
    void resize(int newCapacity) {
        Entry<K,V>[] oldTable = getTable();
        int oldCapacity = oldTable.length;
        if (oldCapacity == MAXIMUM_CAPACITY) {
            threshold = Integer.MAX_VALUE;
            return;
        }

        //重新设置值。
        Entry<K,V>[] newTable = newTable(newCapacity);
        transfer(oldTable, newTable);
        table = newTable;

        /*
         * If ignoring null elements and processing ref queue caused massive
         * shrinkage, then restore old table.  This should be rare, but avoids
         * unbounded expansion of garbage-filled tables.
         */
        if (size >= threshold / 2) {
        	//下次长度
            threshold = (int)(newCapacity * loadFactor);
        } else {
            expungeStaleEntries();
            //重新全部复制
            transfer(newTable, oldTable);
            table = oldTable;
        }
    }

    /**
     * 将所有src里面值放入dest里面。
     * @param src
     * @param dest
     */
    private void transfer(Entry<K,V>[] src, Entry<K,V>[] dest) {
        for (int j = 0; j < src.length; ++j) {
            Entry<K,V> e = src[j];
            src[j] = null;
            while (e != null) {
                Entry<K,V> next = e.next;
                Object key = e.get();
                if (key == null) {
                    e.next = null;  // Help GC
                    e.value = null; //  "   "
                    size--;
                } else {
                    int i = indexFor(e.hash, dest.length);
                    e.next = dest[i];
                    dest[i] = e;
                }
                e = next;
            }
        }
    }

    /**
     * 将m中所有值放入到当前map中。
     */
    public void putAll(Map<? extends K, ? extends V> m) {
        int numKeysToBeAdded = m.size();
        if (numKeysToBeAdded == 0)
            return;
         
        if (numKeysToBeAdded > threshold) {
            int targetCapacity = (int)(numKeysToBeAdded / loadFactor + 1);
            if (targetCapacity > MAXIMUM_CAPACITY)
                targetCapacity = MAXIMUM_CAPACITY;
            int newCapacity = table.length;
            while (newCapacity < targetCapacity)
                newCapacity <<= 1;
            if (newCapacity > table.length)
                resize(newCapacity);
        }

        for (Map.Entry<? extends K, ? extends V> e : m.entrySet())
            put(e.getKey(), e.getValue());
    }

    /**
     *删除某一个键。
     */
    public V remove(Object key) {
        Object k = maskNull(key);
        int h = hash(k);
        Entry<K,V>[] tab = getTable();
        int i = indexFor(h, tab.length);
        Entry<K,V> prev = tab[i];
        Entry<K,V> e = prev;

        while (e != null) {
            Entry<K,V> next = e.next;
            if (h == e.hash && eq(k, e.get())) {
                modCount++;
                size--;
                if (prev == e)
                    tab[i] = next;
                else
                    prev.next = next;
                return e.value;
            }
            prev = e;
            e = next;
        }

        return null;
    }

    /** 删除键值。 */
    boolean removeMapping(Object o) {
        if (!(o instanceof Map.Entry))
            return false;
        Entry<K,V>[] tab = getTable();
        Map.Entry<?,?> entry = (Map.Entry<?,?>)o;
        Object k = maskNull(entry.getKey());
        int h = hash(k);
        int i = indexFor(h, tab.length);
        Entry<K,V> prev = tab[i];
        Entry<K,V> e = prev;

        while (e != null) {
            Entry<K,V> next = e.next;
            if (h == e.hash && e.equals(entry)) {
                modCount++;
                size--;
                if (prev == e)
                    tab[i] = next;
                else
                    prev.next = next;
                return true;
            }
            prev = e;
            e = next;
        }

        return false;
    }

    /**
     * 清空，以及queue和table
     */
    public void clear() {
        // clear out ref queue. We don't need to expunge entries
        // since table is getting cleared.
        while (queue.poll() != null)
            ;

        modCount++;
        Arrays.fill(table, null);
        size = 0;

        // Allocation of array may have caused GC, which may have caused
        // additional entries to go stale.  Removing these entries from the
        // reference queue will make them eligible for reclamation.
        while (queue.poll() != null)
            ;
    }

    /**
     * 如果包含值，就ture
     */
    public boolean containsValue(Object value) {
        if (value==null)
            return containsNullValue();

        Entry<K,V>[] tab = getTable();
        for (int i = tab.length; i-- > 0;)
            for (Entry<K,V> e = tab[i]; e != null; e = e.next)
                if (value.equals(e.value))
                    return true;
        return false;
    }

    /**
     * 是否包含null值。
     */
    private boolean containsNullValue() {
        Entry<K,V>[] tab = getTable();
        for (int i = tab.length; i-- > 0;)
            for (Entry<K,V> e = tab[i]; e != null; e = e.next)
                if (e.value==null)
                    return true;
        return false;
    }

    /**
     * 继承自weakreference
     */
    private static class Entry<K,V> extends WeakReference<Object> implements Map.Entry<K,V> {
        V value;
        final int hash;
        Entry<K,V> next;

        /**
         * Creates new entry.
         */
        Entry(Object key, V value,
              ReferenceQueue<Object> queue,
              int hash, Entry<K,V> next) {
            super(key, queue);
            this.value = value;
            this.hash  = hash;
            this.next  = next;
        }

        @SuppressWarnings("unchecked")
        public K getKey() {
            return (K) WeakHashMap.unmaskNull(get());
        }

        public V getValue() {
            return value;
        }

        public V setValue(V newValue) {
            V oldValue = value;
            value = newValue;
            return oldValue;
        }

        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> e = (Map.Entry<?,?>)o;
            K k1 = getKey();
            Object k2 = e.getKey();
            if (k1 == k2 || (k1 != null && k1.equals(k2))) {
                V v1 = getValue();
                Object v2 = e.getValue();
                if (v1 == v2 || (v1 != null && v1.equals(v2)))
                    return true;
            }
            return false;
        }

        public int hashCode() {
            K k = getKey();
            V v = getValue();
            return Objects.hashCode(k) ^ Objects.hashCode(v);
        }

        public String toString() {
            return getKey() + "=" + getValue();
        }
    }

    /**
     * iterator.
     * @author anla7856
     *
     * @param <T>
     */
    private abstract class HashIterator<T> implements Iterator<T> {
        private int index;
        private Entry<K,V> entry;
        private Entry<K,V> lastReturned;
        private int expectedModCount = modCount;

        /**
         * Strong reference needed to avoid disappearance of key
         * between hasNext and next
         */
        private Object nextKey;

        /**
         * Strong reference needed to avoid disappearance of key
         * between nextEntry() and any use of the entry
         */
        private Object currentKey;

        HashIterator() {
            index = isEmpty() ? 0 : table.length;
        }

        public boolean hasNext() {
            Entry<K,V>[] t = table;

            while (nextKey == null) {
                Entry<K,V> e = entry;
                int i = index;
                while (e == null && i > 0)
                    e = t[--i];
                entry = e;
                index = i;
                if (e == null) {
                    currentKey = null;
                    return false;
                }
                nextKey = e.get(); // hold on to key in strong ref
                if (nextKey == null)
                    entry = entry.next;
            }
            return true;
        }

        /** The common parts of next() across different types of iterators */
        protected Entry<K,V> nextEntry() {
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            if (nextKey == null && !hasNext())
                throw new NoSuchElementException();

            lastReturned = entry;
            entry = entry.next;
            currentKey = nextKey;
            nextKey = null;
            return lastReturned;
        }

        public void remove() {
            if (lastReturned == null)
                throw new IllegalStateException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();

            WeakHashMap.this.remove(currentKey);
            expectedModCount = modCount;
            lastReturned = null;
            currentKey = null;
        }

    }

    private class ValueIterator extends HashIterator<V> {
        public V next() {
            return nextEntry().value;
        }
    }

    private class KeyIterator extends HashIterator<K> {
        public K next() {
            return nextEntry().getKey();
        }
    }

    private class EntryIterator extends HashIterator<Map.Entry<K,V>> {
        public Map.Entry<K,V> next() {
            return nextEntry();
        }
    }

    // Views
    //entrySet
    private transient Set<Map.Entry<K,V>> entrySet;

    /**
     * Returns a {@link Set} view of the keys contained in this map.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  If the map is modified
     * while an iteration over the set is in progress (except through
     * the iterator's own <tt>remove</tt> operation), the results of
     * the iteration are undefined.  The set supports element removal,
     * which removes the corresponding mapping from the map, via the
     * <tt>Iterator.remove</tt>, <tt>Set.remove</tt>,
     * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt>
     * operations.  It does not support the <tt>add</tt> or <tt>addAll</tt>
     * operations.
     */
    public Set<K> keySet() {
        Set<K> ks = keySet;
        if (ks == null) {
            ks = new KeySet();
            keySet = ks;
        }
        return ks;
    }

    private class KeySet extends AbstractSet<K> {
        public Iterator<K> iterator() {
            return new KeyIterator();
        }

        public int size() {
            return WeakHashMap.this.size();
        }

        public boolean contains(Object o) {
            return containsKey(o);
        }

        public boolean remove(Object o) {
            if (containsKey(o)) {
                WeakHashMap.this.remove(o);
                return true;
            }
            else
                return false;
        }

        public void clear() {
            WeakHashMap.this.clear();
        }

        public Spliterator<K> spliterator() {
            return new KeySpliterator<>(WeakHashMap.this, 0, -1, 0, 0);
        }
    }

    /**
     * 返回值。
     */
    public Collection<V> values() {
        Collection<V> vs = values;
        if (vs == null) {
            vs = new Values();
            values = vs;
        }
        return vs;
    }

    private class Values extends AbstractCollection<V> {
        public Iterator<V> iterator() {
            return new ValueIterator();
        }

        public int size() {
            return WeakHashMap.this.size();
        }

        public boolean contains(Object o) {
            return containsValue(o);
        }

        public void clear() {
            WeakHashMap.this.clear();
        }

        public Spliterator<V> spliterator() {
            return new ValueSpliterator<>(WeakHashMap.this, 0, -1, 0, 0);
        }
    }

    /**
     * 返回entryset
     */
    public Set<Map.Entry<K,V>> entrySet() {
        Set<Map.Entry<K,V>> es = entrySet;
        return es != null ? es : (entrySet = new EntrySet());
    }

    private class EntrySet extends AbstractSet<Map.Entry<K,V>> {
        public Iterator<Map.Entry<K,V>> iterator() {
            return new EntryIterator();
        }

        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> e = (Map.Entry<?,?>)o;
            Entry<K,V> candidate = getEntry(e.getKey());
            return candidate != null && candidate.equals(e);
        }

        public boolean remove(Object o) {
            return removeMapping(o);
        }

        public int size() {
            return WeakHashMap.this.size();
        }

        public void clear() {
            WeakHashMap.this.clear();
        }

        private List<Map.Entry<K,V>> deepCopy() {
            List<Map.Entry<K,V>> list = new ArrayList<>(size());
            for (Map.Entry<K,V> e : this)
                list.add(new AbstractMap.SimpleEntry<>(e));
            return list;
        }

        public Object[] toArray() {
            return deepCopy().toArray();
        }

        public <T> T[] toArray(T[] a) {
            return deepCopy().toArray(a);
        }

        public Spliterator<Map.Entry<K,V>> spliterator() {
            return new EntrySpliterator<>(WeakHashMap.this, 0, -1, 0, 0);
        }
    }

    /**
     * java8新出的forEach
     * @param action
     */
    @SuppressWarnings("unchecked")
    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        Objects.requireNonNull(action);
        int expectedModCount = modCount;

        Entry<K, V>[] tab = getTable();
        for (Entry<K, V> entry : tab) {
            while (entry != null) {
                Object key = entry.get();
                if (key != null) {
                    action.accept((K)WeakHashMap.unmaskNull(key), entry.value);
                }
                entry = entry.next;

                if (expectedModCount != modCount) {
                    throw new ConcurrentModificationException();
                }
            }
        }
    }

    /**
     * 替代所有，也就是运行所有
     * @param function
     */
    @SuppressWarnings("unchecked")
    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        Objects.requireNonNull(function);
        int expectedModCount = modCount;

        Entry<K, V>[] tab = getTable();;
        for (Entry<K, V> entry : tab) {
            while (entry != null) {
                Object key = entry.get();
                if (key != null) {
                    entry.value = function.apply((K)WeakHashMap.unmaskNull(key), entry.value);
                }
                entry = entry.next;

                if (expectedModCount != modCount) {
                    throw new ConcurrentModificationException();
                }
            }
        }
    }

    /**
     * Spliterator
     */
    static class WeakHashMapSpliterator<K,V> {
        final WeakHashMap<K,V> map;
        WeakHashMap.Entry<K,V> current; // current node
        int index;             // current index, modified on advance/split
        int fence;             // -1 until first use; then one past last index
        int est;               // size estimate
        int expectedModCount;  // for comodification checks

        WeakHashMapSpliterator(WeakHashMap<K,V> m, int origin,
                               int fence, int est,
                               int expectedModCount) {
            this.map = m;
            this.index = origin;
            this.fence = fence;
            this.est = est;
            this.expectedModCount = expectedModCount;
        }

        final int getFence() { // initialize fence and size on first use
            int hi;
            if ((hi = fence) < 0) {
                WeakHashMap<K,V> m = map;
                est = m.size();
                expectedModCount = m.modCount;
                hi = fence = m.table.length;
            }
            return hi;
        }

        public final long estimateSize() {
            getFence(); // force init
            return (long) est;
        }
    }

    static final class KeySpliterator<K,V>
        extends WeakHashMapSpliterator<K,V>
        implements Spliterator<K> {
        KeySpliterator(WeakHashMap<K,V> m, int origin, int fence, int est,
                       int expectedModCount) {
            super(m, origin, fence, est, expectedModCount);
        }

        public KeySpliterator<K,V> trySplit() {
            int hi = getFence(), lo = index, mid = (lo + hi) >>> 1;
            return (lo >= mid) ? null :
                new KeySpliterator<K,V>(map, lo, index = mid, est >>>= 1,
                                        expectedModCount);
        }

        public void forEachRemaining(Consumer<? super K> action) {
            int i, hi, mc;
            if (action == null)
                throw new NullPointerException();
            WeakHashMap<K,V> m = map;
            WeakHashMap.Entry<K,V>[] tab = m.table;
            if ((hi = fence) < 0) {
                mc = expectedModCount = m.modCount;
                hi = fence = tab.length;
            }
            else
                mc = expectedModCount;
            if (tab.length >= hi && (i = index) >= 0 &&
                (i < (index = hi) || current != null)) {
                WeakHashMap.Entry<K,V> p = current;
                current = null; // exhaust
                do {
                    if (p == null)
                    	//不遍历null地方
                        p = tab[i++];
                    else {
                    	//得到引用，也就是看你的引用有没有小时
                        Object x = p.get();
                        
                        p = p.next;
                        if (x != null) {
                            @SuppressWarnings("unchecked") K k =
                                (K) WeakHashMap.unmaskNull(x);
                            action.accept(k);
                        }
                    }
                } while (p != null || i < hi);
            }
            if (m.modCount != mc)
                throw new ConcurrentModificationException();
        }

        public boolean tryAdvance(Consumer<? super K> action) {
            int hi;
            if (action == null)
                throw new NullPointerException();
            WeakHashMap.Entry<K,V>[] tab = map.table;
            if (tab.length >= (hi = getFence()) && index >= 0) {
            	//遍历current开始的链表。
                while (current != null || index < hi) {
                    if (current == null)
                        current = tab[index++];
                    else {
                        Object x = current.get();
                        current = current.next;
                        if (x != null) {
                        	//只调查引用，而不管你k是否为null
                            @SuppressWarnings("unchecked") K k =
                                (K) WeakHashMap.unmaskNull(x);
                            action.accept(k);
                            if (map.modCount != expectedModCount)
                                throw new ConcurrentModificationException();
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        public int characteristics() {
            return Spliterator.DISTINCT;
        }
    }

    static final class ValueSpliterator<K,V>
        extends WeakHashMapSpliterator<K,V>
        implements Spliterator<V> {
        ValueSpliterator(WeakHashMap<K,V> m, int origin, int fence, int est,
                         int expectedModCount) {
            super(m, origin, fence, est, expectedModCount);
        }

        public ValueSpliterator<K,V> trySplit() {
            int hi = getFence(), lo = index, mid = (lo + hi) >>> 1;
            return (lo >= mid) ? null :
                new ValueSpliterator<K,V>(map, lo, index = mid, est >>>= 1,
                                          expectedModCount);
        }

        public void forEachRemaining(Consumer<? super V> action) {
            int i, hi, mc;
            if (action == null)
                throw new NullPointerException();
            WeakHashMap<K,V> m = map;
            WeakHashMap.Entry<K,V>[] tab = m.table;
            if ((hi = fence) < 0) {
                mc = expectedModCount = m.modCount;
                hi = fence = tab.length;
            }
            else
                mc = expectedModCount;
            if (tab.length >= hi && (i = index) >= 0 &&
                (i < (index = hi) || current != null)) {
                WeakHashMap.Entry<K,V> p = current;
                current = null; // exhaust
                do {
                    if (p == null)
                        p = tab[i++];
                    else {
                        Object x = p.get();
                        V v = p.value;
                        p = p.next;
                        if (x != null)
                        	//只判断引用是否为null，然后直接对v进行操作。
                            action.accept(v);
                    }
                } while (p != null || i < hi);
            }
            if (m.modCount != mc)
                throw new ConcurrentModificationException();
        }

        public boolean tryAdvance(Consumer<? super V> action) {
            int hi;
            if (action == null)
                throw new NullPointerException();
            WeakHashMap.Entry<K,V>[] tab = map.table;
            if (tab.length >= (hi = getFence()) && index >= 0) {
                while (current != null || index < hi) {
                    if (current == null)
                        current = tab[index++];
                    else {
                        Object x = current.get();
                        V v = current.value;
                        current = current.next;
                        if (x != null) {
                            action.accept(v);
                            if (map.modCount != expectedModCount)
                                throw new ConcurrentModificationException();
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        public int characteristics() {
            return 0;
        }
    }

    static final class EntrySpliterator<K,V>
        extends WeakHashMapSpliterator<K,V>
        implements Spliterator<Map.Entry<K,V>> {
        EntrySpliterator(WeakHashMap<K,V> m, int origin, int fence, int est,
                       int expectedModCount) {
            super(m, origin, fence, est, expectedModCount);
        }

        public EntrySpliterator<K,V> trySplit() {
            int hi = getFence(), lo = index, mid = (lo + hi) >>> 1;
            return (lo >= mid) ? null :
                new EntrySpliterator<K,V>(map, lo, index = mid, est >>>= 1,
                                          expectedModCount);
        }


        public void forEachRemaining(Consumer<? super Map.Entry<K, V>> action) {
            int i, hi, mc;
            if (action == null)
                throw new NullPointerException();
            WeakHashMap<K,V> m = map;
            WeakHashMap.Entry<K,V>[] tab = m.table;
            if ((hi = fence) < 0) {
                mc = expectedModCount = m.modCount;
                hi = fence = tab.length;
            }
            else
                mc = expectedModCount;
            if (tab.length >= hi && (i = index) >= 0 &&
                (i < (index = hi) || current != null)) {
                WeakHashMap.Entry<K,V> p = current;
                current = null; // exhaust
                do {
                    if (p == null)
                        p = tab[i++];
                    else {
                        Object x = p.get();
                        V v = p.value;
                        p = p.next;
                        if (x != null) {
                            @SuppressWarnings("unchecked") K k =
                                (K) WeakHashMap.unmaskNull(x);
                            action.accept
                                (new AbstractMap.SimpleImmutableEntry<K,V>(k, v));
                        }
                    }
                } while (p != null || i < hi);
            }
            if (m.modCount != mc)
                throw new ConcurrentModificationException();
        }

        public boolean tryAdvance(Consumer<? super Map.Entry<K,V>> action) {
            int hi;
            if (action == null)
                throw new NullPointerException();
            WeakHashMap.Entry<K,V>[] tab = map.table;
            if (tab.length >= (hi = getFence()) && index >= 0) {
                while (current != null || index < hi) {
                    if (current == null)
                        current = tab[index++];
                    else {
                        Object x = current.get();
                        V v = current.value;
                        current = current.next;
                        if (x != null) {
                            @SuppressWarnings("unchecked") K k =
                                (K) WeakHashMap.unmaskNull(x);
                            action.accept
                                (new AbstractMap.SimpleImmutableEntry<K,V>(k, v));
                            if (map.modCount != expectedModCount)
                                throw new ConcurrentModificationException();
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        public int characteristics() {
            return Spliterator.DISTINCT;
        }
    }

}
