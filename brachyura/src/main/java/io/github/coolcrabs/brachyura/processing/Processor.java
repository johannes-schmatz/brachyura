package io.github.coolcrabs.brachyura.processing;

import java.io.IOException;
import java.util.Collection;

/**
 * Example for the identity processor:
 * <pre>{@code
 * public class CustomProcessor implements Processor {
 *     @Override
 *     public void process(Collection<ProcessingEntry> inputs, ProcessingSink sink) throws IOException {
 *         for (ProcessingEntry entry : inputs) {
 *             sink.sink(entry.in, entry.id);
 *         }
 *     }
 * }
 * }</pre>
 */
@FunctionalInterface
public interface Processor {
    void process(Collection<ProcessingEntry> inputs, ProcessingSink sink) throws IOException;
}
