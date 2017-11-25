package anla.study.sourceCode.Collection;

import java.util.Objects;
import java.util.function.Function;

/**
 * 代表这一个方法，能够接受参数，并且返回一个结果
 * @since 1.8
 */
@FunctionalInterface
public interface Function<T, R> {
	/**
	 * 将参数赋予给相应方法
	 * 
	 * @param t
	 * @return
	 */
	R apply(T t);

	/**
	 * 先执行参数，再执行调用者
	 */
	default <V> Function<V, R> compose(Function<? super V, ? extends T> before) {
		Objects.requireNonNull(before);
		return (V v) -> apply(before.apply(v));
	}
	/**
	 * 先执行调用者，再执行参数
	 * 
	 * @see #compose(Function)
	 */
	default <V> Function<T, V> andThen(Function<? super R, ? extends V> after) {
		Objects.requireNonNull(after);
		return (T t) -> after.apply(apply(t));
	}
	/**
	 * 返回当前正在执行的方法
     */
	static <T> Function<T, T> identity() {
		return t -> t;
	}
}
