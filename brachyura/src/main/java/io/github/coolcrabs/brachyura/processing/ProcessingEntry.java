package io.github.coolcrabs.brachyura.processing;

import java.io.InputStream;
import java.util.Objects;
import java.util.function.Supplier;

public final class ProcessingEntry {
    public final Supplier<InputStream> in;
    public final ProcessingId id;

    public ProcessingEntry(Supplier<InputStream> in, ProcessingId id) {
        this.in = in;
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProcessingEntry that = (ProcessingEntry) o;
        return in.equals(that.in) && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(in, id);
    }

    @Override
    public String toString() {
        return "ProcessingEntry{" +
                "in=" + in +
                ", id=" + id +
                '}';
    }
}
