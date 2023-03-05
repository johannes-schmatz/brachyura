package io.github.coolcrabs.brachyura.processing;

import java.io.IOException;

/**
 * Example for the identity processor:
 * <pre>{@code
 * public class CustomProcessor implements Processor {
 *     @Override
 *     public void process(ProcessingCollector inputs, ProcessingSink sink) throws IOException {
 *         inputs.sinkRemaining(sink);
 *     }
 * }
 * }</pre>
 */
@FunctionalInterface
public interface Processor {
    void process(ProcessingCollector inputs, ProcessingSink sink) throws IOException;
}
