package io.github.coolcrabs.brachyura.processing;

import java.io.InputStream;
import java.util.function.Supplier;

@FunctionalInterface
public interface ProcessingSink {
    void sink(Supplier<InputStream> in, ProcessingId id);
}
