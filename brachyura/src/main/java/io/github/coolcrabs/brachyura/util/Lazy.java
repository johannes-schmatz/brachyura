package io.github.coolcrabs.brachyura.util;

import java.util.Objects;
import java.util.function.Supplier;

// Based on https://dzone.com/articles/be-lazy-with-java-8
// Modified to take the supplier in the constructor
public final class Lazy<T> implements Supplier<T> {

    private volatile boolean isThere = false;
    private volatile T value;
    private final Supplier<T> supplier;

    public Lazy(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    @Override
    public T get() {
        final boolean there = isThere; // Just one volatile read
        //final T result = value;

        return there ? value : maybeCompute(); // again just one volatile read
        //return result == null ? maybeCompute() : result;
    }

    private synchronized T maybeCompute() {
        if (!isThere) {
            value = supplier.get();
            isThere = true;
        }
        return value;
    }

}
