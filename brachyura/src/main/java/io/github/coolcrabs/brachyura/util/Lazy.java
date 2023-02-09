package io.github.coolcrabs.brachyura.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ForkJoinTask;
import java.util.function.Supplier;

// Based on https://dzone.com/articles/be-lazy-with-java-8
// Modified to take the supplier in the constructor
public final class Lazy<T> implements Supplier<T> {

    private volatile boolean isThere = false;
    private volatile T value;
    private volatile ForkJoinTask<T> task;
    private final Supplier<T> supplier;
    private final String caller;

    public static <T> Lazy<T> of(Supplier<T> x) {
        if (x instanceof Lazy) return (Lazy<T>) x;
        return new Lazy<>(x, Util.getCaller());
    }

    public Lazy(Supplier<T> supplier) {
        this(supplier, Util.getCaller());
    }

    private Lazy(Supplier<T> supplier, String caller) {
        this.supplier = supplier;
        this.caller = caller;
    }

    @Override
    public String toString() {
        return super.toString() + "[caller=" + caller + "]";
    }

    @Override
    public T get() {
        final boolean there = isThere; // Just one volatile read
        //final T result = value;

        return there ? value : maybeCompute(); // again just one volatile read
        //return result == null ? maybeCompute() : result;
    }

    private synchronized T maybeCompute() {
        try {
            if (!isThere) {
                value = supplier.get();
                isThere = true;
            }
            return value;
        } catch (Throwable e) {
            throw new RuntimeException(toString(), e);
        }
    }

    public static <T> List<T> getParallel(Collection<Lazy<T>> things) {
        return getParallel(things.toArray(new Lazy[0]));
    }

    public static <T> List<T> getParallel(Lazy<T>... things) {
        if (true) {
            List<T> l = new ArrayList<>(things.length);
            for (Lazy<T> t : things) {
                l.add(t.get());
            }
            return l;
        }

        int n = things.length;

        int[] slots = new int[n];
        List<ForkJoinTask<T>> tasks = new ArrayList<>(n);
        List<T> results = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            Lazy<T> thing = things[i];

            T v = thing.value;
            boolean isThere = thing.isThere;

            if (isThere) {
                results.add(v);
            } else {
                ForkJoinTask<T> task = thing.task;
                if (task == null) {
                    // otherwise you could have a task who populates this Lazy and another Lazy calls this Lazy to get as well
                    synchronized (thing) {
                        if (thing.isThere) {
                            results.add(thing.value);
                            continue;
                        }
                        task = thing.task;
                        if (task == null) {
                            task = ForkJoinTask.adapt(thing.supplier::get);
                            task.fork();
                            thing.task = task;
                        }
                    }
                }
                results.add(null);
                slots[i] = i;
                tasks.add(task);
            }
        }
        for (int i = 0; i < n; i++) {
            results.set(slots[i], tasks.get(i).join());
        }
        return results;
    }
}
