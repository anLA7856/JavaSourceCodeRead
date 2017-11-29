package anla.study.sourceCode.Concurrent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Objects;
import java.util.function.IntBinaryOperator;
import java.util.function.IntUnaryOperator;
import sun.reflect.CallerSensitive;
import sun.reflect.Reflection;

/**
 * 允许一个已经定义的类里面的某一个volatile int型变量的原子更新。
 * 
 * 注意只能够原子更新里面的某一个int型的变量。
 * 
 * 思路是通过反射获取变量，为一个updater，然后进行更新。
 * 
 */
public abstract class AtomicIntegerFieldUpdater<T> {
    /**
     * 创建一个updater，
     * tclass：包含类的名称
     * fieldName：字段名字
     */
    @CallerSensitive
    public static <U> AtomicIntegerFieldUpdater<U> newUpdater(Class<U> tclass,
                                                              String fieldName) {
        return new AtomicIntegerFieldUpdaterImpl<U>
            (tclass, fieldName, Reflection.getCallerClass());
    }

    /**
     * Protected do-nothing constructor for use by subclasses.
     */
    protected AtomicIntegerFieldUpdater() {
    }

    /**
     * obj，含有需要改变volatile变量的对象实例
     */
    public abstract boolean compareAndSet(T obj, int expect, int update);

    /**
     * 
     * 弱类型的，保证原子性，不保证可见性。也就是会少了store load重排序。
     */
    public abstract boolean weakCompareAndSet(T obj, int expect, int update);

    /**
     * 设置值
     */
    public abstract void set(T obj, int newValue);

    /**
     * lazy型设置值。
     * @since 1.6
     */
    public abstract void lazySet(T obj, int newValue);

    /**
     * 获得
     */
    public abstract int get(T obj);

    /**
     * 获得并且设置。
     */
    public int getAndSet(T obj, int newValue) {
        int prev;
        do {
            prev = get(obj);
        } while (!compareAndSet(obj, prev, newValue));
        return prev;
    }

    /**
     * 获得并且加1
     */
    public int getAndIncrement(T obj) {
        int prev, next;
        do {
            prev = get(obj);
            next = prev + 1;
        } while (!compareAndSet(obj, prev, next));
        return prev;
    }

    /**
     * Atomically decrements by one the current value of the field of the
     * given object managed by this updater.
     *
     * @param obj An object whose field to get and set
     * @return the previous value
     */
    public int getAndDecrement(T obj) {
        int prev, next;
        do {
            prev = get(obj);
            next = prev - 1;
        } while (!compareAndSet(obj, prev, next));
        return prev;
    }

    /**
     * Atomically adds the given value to the current value of the field of
     * the given object managed by this updater.
     *
     * @param obj An object whose field to get and set
     * @param delta the value to add
     * @return the previous value
     */
    public int getAndAdd(T obj, int delta) {
        int prev, next;
        do {
            prev = get(obj);
            next = prev + delta;
        } while (!compareAndSet(obj, prev, next));
        return prev;
    }

    /**
     * Atomically increments by one the current value of the field of the
     * given object managed by this updater.
     *
     * @param obj An object whose field to get and set
     * @return the updated value
     */
    public int incrementAndGet(T obj) {
        int prev, next;
        do {
            prev = get(obj);
            next = prev + 1;
        } while (!compareAndSet(obj, prev, next));
        return next;
    }

    /**
     * Atomically decrements by one the current value of the field of the
     * given object managed by this updater.
     *
     * @param obj An object whose field to get and set
     * @return the updated value
     */
    public int decrementAndGet(T obj) {
        int prev, next;
        do {
            prev = get(obj);
            next = prev - 1;
        } while (!compareAndSet(obj, prev, next));
        return next;
    }

    /**
     * Atomically adds the given value to the current value of the field of
     * the given object managed by this updater.
     *
     * @param obj An object whose field to get and set
     * @param delta the value to add
     * @return the updated value
     */
    public int addAndGet(T obj, int delta) {
        int prev, next;
        do {
            prev = get(obj);
            next = prev + delta;
        } while (!compareAndSet(obj, prev, next));
        return next;
    }

    /**
     * Atomically updates the field of the given object managed by this updater
     * with the results of applying the given function, returning the previous
     * value. The function should be side-effect-free, since it may be
     * re-applied when attempted updates fail due to contention among threads.
     *
     * @param obj An object whose field to get and set
     * @param updateFunction a side-effect-free function
     * @return the previous value
     * @since 1.8
     */
    public final int getAndUpdate(T obj, IntUnaryOperator updateFunction) {
        int prev, next;
        do {
            prev = get(obj);
            next = updateFunction.applyAsInt(prev);
        } while (!compareAndSet(obj, prev, next));
        return prev;
    }

    /**
     * Atomically updates the field of the given object managed by this updater
     * with the results of applying the given function, returning the updated
     * value. The function should be side-effect-free, since it may be
     * re-applied when attempted updates fail due to contention among threads.
     *
     * @param obj An object whose field to get and set
     * @param updateFunction a side-effect-free function
     * @return the updated value
     * @since 1.8
     */
    public final int updateAndGet(T obj, IntUnaryOperator updateFunction) {
        int prev, next;
        do {
            prev = get(obj);
            next = updateFunction.applyAsInt(prev);
        } while (!compareAndSet(obj, prev, next));
        return next;
    }

    /**
     * Atomically updates the field of the given object managed by this
     * updater with the results of applying the given function to the
     * current and given values, returning the previous value. The
     * function should be side-effect-free, since it may be re-applied
     * when attempted updates fail due to contention among threads.  The
     * function is applied with the current value as its first argument,
     * and the given update as the second argument.
     *
     * @param obj An object whose field to get and set
     * @param x the update value
     * @param accumulatorFunction a side-effect-free function of two arguments
     * @return the previous value
     * @since 1.8
     */
    public final int getAndAccumulate(T obj, int x,
                                      IntBinaryOperator accumulatorFunction) {
        int prev, next;
        do {
            prev = get(obj);
            next = accumulatorFunction.applyAsInt(prev, x);
        } while (!compareAndSet(obj, prev, next));
        return prev;
    }

    /**
     * Atomically updates the field of the given object managed by this
     * updater with the results of applying the given function to the
     * current and given values, returning the updated value. The
     * function should be side-effect-free, since it may be re-applied
     * when attempted updates fail due to contention among threads.  The
     * function is applied with the current value as its first argument,
     * and the given update as the second argument.
     *
     * @param obj An object whose field to get and set
     * @param x the update value
     * @param accumulatorFunction a side-effect-free function of two arguments
     * @return the updated value
     * @since 1.8
     */
    public final int accumulateAndGet(T obj, int x,
                                      IntBinaryOperator accumulatorFunction) {
        int prev, next;
        do {
            prev = get(obj);
            next = accumulatorFunction.applyAsInt(prev, x);
        } while (!compareAndSet(obj, prev, next));
        return next;
    }

    /**
     * 标准的hotspot用本征函数的实现。
     * 私有final的。
     * Standard hotspot implementation using intrinsics.
     */
    private static final class AtomicIntegerFieldUpdaterImpl<T>
        extends AtomicIntegerFieldUpdater<T> {
        private static final sun.misc.Unsafe U = sun.misc.Unsafe.getUnsafe();
        private final long offset;
        /**
         * if field is protected, the subclass constructing updater, else
         * the same as tclass
         */
        private final Class<?> cclass;
        /** class holding the field */
        private final Class<T> tclass;

        AtomicIntegerFieldUpdaterImpl(final Class<T> tclass,
                                      final String fieldName,
                                      final Class<?> caller) {
            final Field field;
            final int modifiers;
            try {
                field = AccessController.doPrivileged(
                    new PrivilegedExceptionAction<Field>() {
                        public Field run() throws NoSuchFieldException {
                            return tclass.getDeclaredField(fieldName);
                        }
                    });
                modifiers = field.getModifiers();
                sun.reflect.misc.ReflectUtil.ensureMemberAccess(
                    caller, tclass, null, modifiers);
                ClassLoader cl = tclass.getClassLoader();
                ClassLoader ccl = caller.getClassLoader();
                if ((ccl != null) && (ccl != cl) &&
                    ((cl == null) || !isAncestor(cl, ccl))) {
                    sun.reflect.misc.ReflectUtil.checkPackageAccess(tclass);
                }
            } catch (PrivilegedActionException pae) {
                throw new RuntimeException(pae.getException());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }

            if (field.getType() != int.class)
                throw new IllegalArgumentException("Must be integer type");

            if (!Modifier.isVolatile(modifiers))
                throw new IllegalArgumentException("Must be volatile type");

            // Access to protected field members is restricted to receivers only
            // of the accessing class, or one of its subclasses, and the
            // accessing class must in turn be a subclass (or package sibling)
            // of the protected member's defining class.
            // If the updater refers to a protected field of a declaring class
            // outside the current package, the receiver argument will be
            // narrowed to the type of the accessing class.
            this.cclass = (Modifier.isProtected(modifiers) &&
                           tclass.isAssignableFrom(caller) &&
                           !isSamePackage(tclass, caller))
                          ? caller : tclass;
            this.tclass = tclass;
            this.offset = U.objectFieldOffset(field);
        }

        /**
         * 判断second是否为first的父类
         * 
         * Returns true if the second classloader can be found in the first
         * classloader's delegation chain.
         * Equivalent to the inaccessible: first.isAncestor(second).
         */
        private static boolean isAncestor(ClassLoader first, ClassLoader second) {
            ClassLoader acl = first;
            do {
                acl = acl.getParent();
                if (second == acl) {
                    return true;
                }
            } while (acl != null);
            return false;
        }

        /**
         * 
         * 判断是否是在同一个包下面
         * 
         * Returns true if the two classes have the same class loader and
         * package qualifier
         */
        private static boolean isSamePackage(Class<?> class1, Class<?> class2) {
            return class1.getClassLoader() == class2.getClassLoader()
                   && Objects.equals(getPackageName(class1), getPackageName(class2));
        }

        
        /**
         * 获得包名
         * @param cls
         * @return
         */
        private static String getPackageName(Class<?> cls) {
            String cn = cls.getName();
            int dot = cn.lastIndexOf('.');
            return (dot != -1) ? cn.substring(0, dot) : "";
        }

        /**
         * 判断obj是否为cclass的一个实例。
         * 
         * Checks that target argument is instance of cclass.  On
         * failure, throws cause.
         */
        private final void accessCheck(T obj) {
            if (!cclass.isInstance(obj))
                throwAccessCheckException(obj);
        }

        /**
         * Throws access exception if accessCheck failed due to
         * protected access, else ClassCastException.
         */
        private final void throwAccessCheckException(T obj) {
            if (cclass == tclass)
                throw new ClassCastException();
            else
                throw new RuntimeException(
                    new IllegalAccessException(
                        "Class " +
                        cclass.getName() +
                        " can not access a protected member of class " +
                        tclass.getName() +
                        " using an instance of " +
                        obj.getClass().getName()));
        }

        public final boolean compareAndSet(T obj, int expect, int update) {
            accessCheck(obj);
            return U.compareAndSwapInt(obj, offset, expect, update);
        }

        public final boolean weakCompareAndSet(T obj, int expect, int update) {
            accessCheck(obj);
            return U.compareAndSwapInt(obj, offset, expect, update);
        }

        public final void set(T obj, int newValue) {
            accessCheck(obj);
            U.putIntVolatile(obj, offset, newValue);
        }

        public final void lazySet(T obj, int newValue) {
            accessCheck(obj);
            U.putOrderedInt(obj, offset, newValue);
        }

        public final int get(T obj) {
            accessCheck(obj);
            return U.getIntVolatile(obj, offset);
        }

        public final int getAndSet(T obj, int newValue) {
            accessCheck(obj);
            return U.getAndSetInt(obj, offset, newValue);
        }

        public final int getAndAdd(T obj, int delta) {
            accessCheck(obj);
            return U.getAndAddInt(obj, offset, delta);
        }

        public final int getAndIncrement(T obj) {
            return getAndAdd(obj, 1);
        }

        public final int getAndDecrement(T obj) {
            return getAndAdd(obj, -1);
        }

        public final int incrementAndGet(T obj) {
            return getAndAdd(obj, 1) + 1;
        }

        public final int decrementAndGet(T obj) {
            return getAndAdd(obj, -1) - 1;
        }

        public final int addAndGet(T obj, int delta) {
            return getAndAdd(obj, delta) + delta;
        }

    }
}
