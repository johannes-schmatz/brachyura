package io.github.coolcrabs.brachyura.util;

import java.util.Map;
import java.util.Objects;

public class Pair<A, B> implements Map.Entry<A, B> {
	private final A left;
	private final B right;
	public Pair(A left, B right) {
		this.left = left;
		this.right = right;
	}

	@Override
	public A getKey() {
		return left;
	}

	@Override
	public B getValue() {
		return right;
	}

	@Override
	public B setValue(Object B) {
		throw new UnsupportedOperationException("Pair doesn't support setValue()");
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Pair<?, ?> pair = (Pair<?, ?>) o;
		return Objects.equals(left, pair.left) && Objects.equals(right, pair.right);
	}

	@Override
	public int hashCode() {
		return Objects.hash(left, right);
	}

	@Override
	public String toString() {
		return "Pair{" +
				"left=" + left +
				", right=" + right +
				'}';
	}
}
