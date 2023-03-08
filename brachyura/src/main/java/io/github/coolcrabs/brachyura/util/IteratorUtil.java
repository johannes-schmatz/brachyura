package io.github.coolcrabs.brachyura.util;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public class IteratorUtil {
	public static <E> Iterator<E> concat(Iterator<? extends E> a, Iterator<? extends E> b) {
		return new Iterator<E>() {
			@Override
			public boolean hasNext() {
				return a.hasNext() || b.hasNext();
			}

			@Override
			public E next() {
				if (a.hasNext()) return a.next();
				if (b.hasNext()) return b.next();
				throw new NoSuchElementException("Neither a or b have another one!");
			}
		};
	}
	public static <E> Iterable<? extends E> concat(Iterable<? extends E> a, Iterable<? extends E> b) {
		return () -> concat(a.iterator(), b.iterator());
	}

	public static <E> Iterable<? extends E> concat(Stream<? extends E> a, Iterable<? extends E> b) {
		return () -> concat(a.iterator(), b.iterator());
	}
}
