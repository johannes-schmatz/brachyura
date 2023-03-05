package io.github.coolcrabs.brachyura.mappings.tinyremapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.coolcrabs.brachyura.mappings.tinyremapper.TinyRemapperHelper.JarType;
import io.github.coolcrabs.brachyura.processing.ProcessingEntry;
import io.github.coolcrabs.brachyura.processing.ProcessingId;
import io.github.coolcrabs.brachyura.processing.ProcessingSink;
import io.github.coolcrabs.brachyura.processing.ProcessingSource;
import io.github.coolcrabs.brachyura.processing.Processor;
import net.fabricmc.tinyremapper.InputTag;

//TODO update when tr finally doesn't require paths for sources
public class RemapperProcessor implements Processor {
    public final TrWrapper remapper;
    public final List<Path> classpath;

    public RemapperProcessor(TrWrapper remapper, List<Path> classpath) {
        this.remapper = remapper;
        this.classpath = classpath;
    }

    @Override
    public void process(Collection<ProcessingEntry> inputs, ProcessingSink sink) throws IOException {
        for (Path j : classpath) {
            TinyRemapperHelper.readJar(remapper, j, JarType.CLASSPATH);
        }
        HashMap<ProcessingSource, InputTag> tags = new HashMap<>();
        for (ProcessingEntry e : inputs) {
            tags.computeIfAbsent(e.id.source, k -> remapper.createInputTag());
        }
        for (ProcessingEntry entry : inputs) {
            if (entry.id.path.endsWith(".class")) {
                remapper.readInputs(tags.get(entry.id.source), entry);
            } else {
                sink.sink(entry.in, entry.id);
            }
        }
        for (Map.Entry<ProcessingSource, InputTag> entry : tags.entrySet()) {
            remapper.apply((path, bytes) -> {
                    sink.sink(
                            () -> new ByteArrayInputStream(bytes), new ProcessingId(path + ".class", entry.getKey())
                    );
                },
                entry.getValue());
        }
    }


}
