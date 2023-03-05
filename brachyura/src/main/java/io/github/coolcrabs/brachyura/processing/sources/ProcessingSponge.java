package io.github.coolcrabs.brachyura.processing.sources;

import java.io.InputStream;
import java.util.HashMap;
import java.util.function.Supplier;

import io.github.coolcrabs.brachyura.processing.ProcessingEntry;
import io.github.coolcrabs.brachyura.processing.ProcessingId;
import io.github.coolcrabs.brachyura.processing.ProcessingSink;
import io.github.coolcrabs.brachyura.processing.ProcessingSource;

// Lol
/**
 * Collects inputs and makes this it's new source
 * You can then retrieve certain paths and/or use it as a source
 */
public class ProcessingSponge implements ProcessingSink, ProcessingSource {
    public final HashMap<String, ProcessingEntry> entries = new HashMap<>();

    @Override
    public void sink(Supplier<InputStream> in, ProcessingId id) {
        entries.put(id.path, new ProcessingEntry(in, id));
    }

    @Override
    public void getInputs(ProcessingSink sink) {
        for (ProcessingEntry e : entries.values()) {
            sink.sink(e.in, e.id);
        }
    }

    public ProcessingEntry popEntry(String path) {
        ProcessingEntry r = entries.get(path);
        if (r != null) {
            entries.remove(path);
        }
        return r;
    }
    
}
