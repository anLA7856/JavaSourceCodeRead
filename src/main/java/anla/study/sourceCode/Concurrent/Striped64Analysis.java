package anla.study.sourceCode.Concurrent;

import java.util.function.LongBinaryOperator;
import java.util.function.DoubleBinaryOperator;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 
 * 包水平上的类，作为父类，为所支持的类提供动态64位分割之城。
 * 
 */
@SuppressWarnings("serial")
abstract class Striped64 extends Number {


	/**
	 * 用@sun.misc.Contended来杜绝为共享。用来保存冲突时需要增加的格子。cell
	 * CAS方式。
	 */
	@sun.misc.Contended
	static final class Cell {
		volatile long value;

		Cell(long x) {
			value = x;
		}

		/**
		 * CAS更新cell里面的val。
		 * @param cmp
		 * @param val
		 * @return
		 */
		final boolean cas(long cmp, long val) {
			return UNSAFE.compareAndSwapLong(this, valueOffset, cmp, val);
		}

		// Unsafe mechanics
		private static final sun.misc.Unsafe UNSAFE;
		private static final long valueOffset;
		static {
			try {
				UNSAFE = sun.misc.Unsafe.getUnsafe();
				Class<?> ak = Cell.class;
				valueOffset = UNSAFE.objectFieldOffset(ak.getDeclaredField("value"));
			} catch (Exception e) {
				throw new Error(e);
			}
		}
	}

	/** Number of CPUS, to place bound on table size
	 * cpu的个数，绑定的table。 */
	static final int NCPU = Runtime.getRuntime().availableProcessors();

	/**
	 * Table of cells. When non-null, size is a power of 2.
	 */
	transient volatile Cell[] cells;

	/**
	 * Base value, used mainly when there is no contention, but also as a
	 * fallback during table initialization races. Updated via CAS.
	 * 基础的值。不冲突下直接在base上增加。
	 * 通过CAS更改。
	 */
	transient volatile long base;

	/**
	 * Spinlock (locked via CAS) used when resizing and/or creating Cells.
	 * 判断cells是否有线程在使用的变量，通过CAS去锁定。
	 */
	transient volatile int cellsBusy;

	/**
	 * Package-private default constructor
	 */
	Striped64() {
	}

	/**
	 * CASes the base field.
	 * 
	 * CAS方式去存储base的值。
	 */
	final boolean casBase(long cmp, long val) {
		return UNSAFE.compareAndSwapLong(this, BASE, cmp, val);
	}

	/**
	 * CASes the cellsBusy field from 0 to 1 to acquire lock.
	 * 
	 * 存储cells，把cellsBusy从0转化为1，借此来获取锁。
	 */
	final boolean casCellsBusy() {
		return UNSAFE.compareAndSwapInt(this, CELLSBUSY, 0, 1);
	}

	/**
	 * Returns the probe value for the current thread. Duplicated from
	 * ThreadLocalRandom because of packaging restrictions.
	 * 
	 * 返回当前线程的probe值。
	 * 
	 */
	static final int getProbe() {
		return UNSAFE.getInt(Thread.currentThread(), PROBE);
	}

	/**
	 * 
	 * 进行异或运算probe，并存储。
	 * 
	 */
	static final int advanceProbe(int probe) {
		probe ^= probe << 13; // xorshift
		probe ^= probe >>> 17;
		probe ^= probe << 5;
		UNSAFE.putInt(Thread.currentThread(), PROBE, probe);
		return probe;
	}

	/**
	 * 里面可以重新改变table大小，或者创建新的cells。
	 * @param x
	 *            the value
	 * @param fn
	 *            the update function, or null for add (this convention avoids
	 *            the need for an extra field or function in LongAdder).
	 * @param wasUncontended
	 *            false if CAS failed before call
	 */
	final void longAccumulate(long x, LongBinaryOperator fn, boolean wasUncontended) {
		int h;
		if ((h = getProbe()) == 0) {
			//如果当前线程没有初始化，就初始化下当前线程。
			ThreadLocalRandom.current(); // force initialization
			h = getProbe();
			wasUncontended = true;
		}
		boolean collide = false; // True if last slot nonempty
		for (;;) {
			Cell[] as;
			Cell a;
			int n;
			long v;
			if ((as = cells) != null && (n = as.length) > 0) {
				//cell有值。
				if ((a = as[(n - 1) & h]) == null) {
					//进入这个方法，就说明这个位置没线程，所以你可以进来。进来后再看能不能获取到cell锁。
					if (cellsBusy == 0) { // Try to attach new Cell
						//新建一个cell，并且尝试加进去
						Cell r = new Cell(x); // Optimistically create
						if (cellsBusy == 0 && casCellsBusy()) {
							boolean created = false;
							try { // Recheck under lock
								Cell[] rs;
								int m, j;
								if ((rs = cells) != null && (m = rs.length) > 0 && rs[j = (m - 1) & h] == null) {
									rs[j] = r;
									created = true;
								}
							} finally {
								cellsBusy = 0;
							}
							if (created)
								break;
							continue; // Slot is now non-empty
						}
					}
					collide = false;  
				} else if (!wasUncontended) // CAS already known to fail
					wasUncontended = true; // Continue after rehash
				else if (a.cas(v = a.value, ((fn == null) ? v + x : fn.applyAsLong(v, x))))
					break;
				else if (n >= NCPU || cells != as)
					collide = false; // At max size or stale
				else if (!collide)
					collide = true;
				else if (cellsBusy == 0 && casCellsBusy()) {
					try {
						//扩容操作。
						if (cells == as) { // Expand table unless stale
							Cell[] rs = new Cell[n << 1];
							for (int i = 0; i < n; ++i)
								rs[i] = as[i];
							cells = rs;
						}
					} finally {
						cellsBusy = 0;
					}
					collide = false;
					continue; // Retry with expanded table
				}
				h = advanceProbe(h);
			} else if (cellsBusy == 0 && cells == as && casCellsBusy()) {
				//这是cell初始化的过程
				//直接修改base不成功，所以来修改cells做文章。
				//cell为null，但是cellsBusy=0，但是有，加入一个cell中。
				boolean init = false;
				try { // Initialize table
					if (cells == as) {
						Cell[] rs = new Cell[2];             //最开始cells的大小为2
						rs[h & 1] = new Cell(x);             //给要增加的x，做一个cell的坑。
						cells = rs;
						init = true;
					}
				} finally {
					//释放锁
					cellsBusy = 0;
				}
				if (init)
					break;
			} else if (casBase(v = base, ((fn == null) ? v + x : fn.applyAsLong(v, x))))
				
				//cell为null并且cellsBusy为1，也就是说，现在有人用cells，我就去尝试更新base吧，接用CAS这个base来实现
				break; // Fall back on using base
		}
	}

	/**
	 * Same as longAccumulate, but injecting long/double conversions in too many
	 * places to sensibly merge with long version, given the low-overhead
	 * requirements of this class. So must instead be maintained by
	 * copy/paste/adapt.
	 * 
	 * 和longAccumulate版本一样，
	 */
	final void doubleAccumulate(double x, DoubleBinaryOperator fn, boolean wasUncontended) {
		int h;
		if ((h = getProbe()) == 0) {
			ThreadLocalRandom.current(); // force initialization
			h = getProbe();
			wasUncontended = true;
		}
		boolean collide = false; // True if last slot nonempty
		for (;;) {
			Cell[] as;
			Cell a;
			int n;
			long v;
			if ((as = cells) != null && (n = as.length) > 0) {
				if ((a = as[(n - 1) & h]) == null) {
					if (cellsBusy == 0) { // Try to attach new Cell
						Cell r = new Cell(Double.doubleToRawLongBits(x));         //double转化为long后存储到cell里面。
						if (cellsBusy == 0 && casCellsBusy()) {
							boolean created = false;
							try { // Recheck under lock
								Cell[] rs;
								int m, j;
								if ((rs = cells) != null && (m = rs.length) > 0 && rs[j = (m - 1) & h] == null) {
									rs[j] = r;
									created = true;
								}
							} finally {
								cellsBusy = 0;
							}
							if (created)
								break;
							continue; // Slot is now non-empty
						}
					}
					collide = false;
				} else if (!wasUncontended) // CAS already known to fail
					wasUncontended = true; // Continue after rehash
				else if (a.cas(v = a.value, ((fn == null) ? Double.doubleToRawLongBits(Double.longBitsToDouble(v) + x)
						: Double.doubleToRawLongBits(fn.applyAsDouble(Double.longBitsToDouble(v), x)))))
					break;
				else if (n >= NCPU || cells != as)
					collide = false; // At max size or stale
				else if (!collide)
					collide = true;
				else if (cellsBusy == 0 && casCellsBusy()) {
					try {
						if (cells == as) { // Expand table unless stale
							Cell[] rs = new Cell[n << 1];
							for (int i = 0; i < n; ++i)
								rs[i] = as[i];
							cells = rs;
						}
					} finally {
						cellsBusy = 0;
					}
					collide = false;
					continue; // Retry with expanded table
				}
				h = advanceProbe(h);
			} else if (cellsBusy == 0 && cells == as && casCellsBusy()) {
				boolean init = false;
				try { // Initialize table
					if (cells == as) {
						Cell[] rs = new Cell[2];
						rs[h & 1] = new Cell(Double.doubleToRawLongBits(x));
						cells = rs;
						init = true;
					}
				} finally {
					cellsBusy = 0;
				}
				if (init)
					break;
			} else if (casBase(v = base, ((fn == null) ? Double.doubleToRawLongBits(Double.longBitsToDouble(v) + x)
					: Double.doubleToRawLongBits(fn.applyAsDouble(Double.longBitsToDouble(v), x)))))
				break; // Fall back on using base
		}
	}

	// Unsafe mechanics
	private static final sun.misc.Unsafe UNSAFE;
	private static final long BASE;
	private static final long CELLSBUSY;
	private static final long PROBE;
	static {
		try {
			UNSAFE = sun.misc.Unsafe.getUnsafe();
			Class<?> sk = Striped64.class;
			BASE = UNSAFE.objectFieldOffset(sk.getDeclaredField("base"));
			CELLSBUSY = UNSAFE.objectFieldOffset(sk.getDeclaredField("cellsBusy"));
			Class<?> tk = Thread.class;
			PROBE = UNSAFE.objectFieldOffset(tk.getDeclaredField("threadLocalRandomProbe"));
		} catch (Exception e) {
			throw new Error(e);
		}
	}

}
