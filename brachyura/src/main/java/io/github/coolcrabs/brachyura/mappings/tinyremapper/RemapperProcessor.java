package io.github.coolcrabs.brachyura.mappings.tinyremapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import io.github.coolcrabs.brachyura.mappings.tinyremapper.TinyRemapperHelper.JarType;
import io.github.coolcrabs.brachyura.processing.*;
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
    public void process(ProcessingCollector inputs, ProcessingSink sink) throws IOException {
        for (Path j : classpath) {
            TinyRemapperHelper.readJar(remapper, j, JarType.CLASSPATH);
        }

        Map<ProcessingSource, InputTag> tags = new HashMap<>();

        for (ProcessingEntry entry : inputs) {
            if (entry.id.path.endsWith(".class")) {
                InputTag tag = tags.computeIfAbsent(entry.id.source, k -> remapper.createInputTag());
                remapper.readInputs(tag, entry);
            } else {
                sink.sink(entry.in, entry.id);
            }
        }

        for (Map.Entry<ProcessingSource, InputTag> entry : tags.entrySet()) {
            remapper.apply(
                    new SinkFileConsumer(sink, entry.getKey()),
                    entry.getValue()
            );
        }
    }


    public static class SinkFileConsumer implements BiConsumer<String, byte[]> {
        public final ProcessingSink sink;
        public final ProcessingSource source;
        public SinkFileConsumer(ProcessingSink sink, ProcessingSource source) {
            this.sink = sink;
            this.source = source;
        }

        @Override
        public void accept(String path, byte[] bytes) {
            sink.sink(
                    () -> new ByteArrayInputStream(bytes),
                    new ProcessingId(path + ".class", source)
            );
        }
    }
}
