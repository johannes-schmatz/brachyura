package io.github.coolcrabs.brachyura.fabric;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashMap;

import io.github.coolcrabs.brachyura.processing.ProcessingCollector;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.tinylog.Logger;

import io.github.coolcrabs.accesswidener.AccessWidener;
import io.github.coolcrabs.accesswidener.AccessWidenerClassVisitor;
import io.github.coolcrabs.brachyura.processing.HashableProcessor;
import io.github.coolcrabs.brachyura.processing.ProcessingEntry;
import io.github.coolcrabs.brachyura.processing.ProcessingSink;

public class AccessWidenerProcessor implements HashableProcessor {
    final AccessWidener aw;

    public AccessWidenerProcessor(AccessWidener aw) {
        this.aw = aw;
    }

    @Override
    public void process(ProcessingCollector inputs, ProcessingSink sink) throws IOException {
        HashMap<String, ProcessingEntry> processedEntries = new HashMap<>();
        for (String cls : aw.clsMap.keySet()) {
            process(cls, inputs, processedEntries, true);
            while (cls.contains("$")) {
                cls = cls.substring(0, cls.lastIndexOf("$"));
                process(cls, inputs, processedEntries, false);
            }
        }
        inputs.sinkRemaining(sink);
        for (ProcessingEntry e : processedEntries.values()) {
            sink.sink(e.in, e.id);
        }
    }

    void process(String cls, ProcessingCollector entries, HashMap<String, ProcessingEntry> processedEntries, boolean softhard) throws IOException {
        String file = cls + ".class";
        ProcessingEntry e = entries.removeByPath(file).iterator().next();
        if (e == null) {
            if (softhard && !processedEntries.containsKey(file)) Logger.warn("Unable to access class to widen {}", cls);
            return;
        }
        ClassWriter w = new ClassWriter(0);
        try (InputStream is = e.in.get()) {
            new ClassReader(is).accept(new AccessWidenerClassVisitor(Opcodes.ASM9, w, aw), 0);
        }
        byte[] bytes = w.toByteArray();
        processedEntries.put(file, new ProcessingEntry(() -> new ByteArrayInputStream(bytes), e.id));
    }

    @Override
    public void hash(MessageDigest md) {
        md.update((byte) 1); // version
        AccessWidenerHasher.hash(md, aw::accept);
    }
    
}
