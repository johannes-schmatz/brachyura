package io.github.coolcrabs.brachyura.processing;

import java.io.IOException;
import java.util.*;

import io.github.coolcrabs.brachyura.util.Util;

public class ProcessorChain {
    public static ProcessorChain of(Processor... processors) {
        return new ProcessorChain(Arrays.asList(processors));
    }

    public static ProcessorChain ofExisting(ProcessorChain existing, Processor... processors) {
        ProcessorChain processorChain = new ProcessorChain(existing.processors);
        processorChain.processors.addAll(Arrays.asList(processors));
        return processorChain;
    }

    public final List<Processor> processors;

    public ProcessorChain(List<Processor> processors) {
        this.processors = processors;
    }

    public void apply(ProcessingSink out, ProcessingSource... in) {
        apply(out, Arrays.asList(in));
    }

    public void apply(ProcessingSink out, Iterable<? extends ProcessingSource> in) {
        try {
            ProcessingCollector c = new ProcessingCollector();
            for (ProcessingSource s : in) {
                s.getInputs(c);
            }

            for (Processor processor : processors) {
                ProcessingCollector c2 = new ProcessingCollector();

                processor.process(c, c2);

                c = c2;
            }

            c.sinkRemaining(out);
        } catch (IOException e) {
            Util.sneak(e);
        }
    }
}
