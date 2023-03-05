package io.github.coolcrabs.brachyura.mappings.tinyremapper;

import io.github.coolcrabs.brachyura.processing.ProcessingEntry;
import net.fabricmc.tinyremapper.InputTag;
import net.fabricmc.tinyremapper.TinyRemapper;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;

public class TrWrapper implements AutoCloseable {
    private final FileSystem fs = TrUtil.createBruhFileSystem();
    private final TinyRemapper tr;
    
    public TrWrapper(TinyRemapper.Builder b) {
        tr = b.build();
    }

    @Override
    public void close() {
        tr.finish();
    }

    public InputTag createInputTag() {
        return tr.createInputTag();
    }

    public void readInputs(InputTag tag, ProcessingEntry entry) {
        tr.readInputs(tag, TrUtil.createBruhPath(fs, entry));
    }

    public void apply(BiConsumer<String, byte[]> consumer, InputTag tag) {
        tr.apply(consumer, tag);
    }

    public void readInputs(InputTag tag, List<Path> paths) {
        tr.readInputs(tag, paths.toArray(new Path[0]));
    }

    public void readClassPath(List<Path> paths) {
        tr.readClassPath(paths.toArray(new Path[0]));
    }

    public String mapFullyQualifiedClassName(String name) {
        return tr.getEnvironment().getRemapper().map(name.replace('.', '/')).replace('/', '.');
    }
}
