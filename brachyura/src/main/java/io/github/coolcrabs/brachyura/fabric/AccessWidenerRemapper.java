package io.github.coolcrabs.brachyura.fabric;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.github.coolcrabs.accesswidener.AccessWidenerReader;
import io.github.coolcrabs.accesswidener.AccessWidenerWriter;
import io.github.coolcrabs.brachyura.processing.*;
import io.github.coolcrabs.brachyura.util.ByteArrayOutputStreamEx;
import io.github.coolcrabs.brachyura.util.GsonUtil;
import net.fabricmc.mappingio.tree.MappingTree;

public class AccessWidenerRemapper implements Processor {
    final MappingTree mappings;
    final int namespace;
    final AccessWidenerCollector awCollector;

    public AccessWidenerRemapper(MappingTree mappings, int namespace, AccessWidenerCollector awCollector) {
        this.mappings = mappings;
        this.namespace = namespace;
        this.awCollector = awCollector;
    }

    @FunctionalInterface
    public interface AccessWidenerCollector {
        List<ProcessingId> collect(ProcessingCollector inputs) throws IOException;
    }

    public enum FabricAwCollector implements AccessWidenerCollector {
        INSTANCE;

        @Override
        public List<ProcessingId> collect(ProcessingCollector inputs) throws IOException {
            ArrayList<ProcessingId> result = new ArrayList<>();
            for (ProcessingEntry entry : inputs.getByPath("fabric.mod.json")) {
                JsonObject fabricModJson = GsonUtil.fromJson(entry, new Gson());
                JsonElement aw0 = fabricModJson.get("accessWidener");
                if (aw0 != null) {
                    result.add(new ProcessingId(aw0.getAsString(), entry.id.source));
                }
            }
            return result;
        }
    }

    @Override
    public void process(ProcessingCollector inputs, ProcessingSink sink) throws IOException {
        for (ProcessingId awid : awCollector.collect(inputs)) {
            ProcessingEntry aw = inputs.removeById(awid);

            ByteArrayOutputStreamEx out = new ByteArrayOutputStreamEx();
            try (
                BufferedReader in = new BufferedReader(new InputStreamReader(aw.in.get()));
                OutputStreamWriter w = new OutputStreamWriter(out)
            ) {
                AccessWidenerNamespaceChanger nc = new AccessWidenerNamespaceChanger(new AccessWidenerWriter(w), mappings, namespace, aw.id.path);
                new AccessWidenerReader(nc).read(in);
            }
            sink.sink(out::toIs, aw.id);
        }
        inputs.sinkRemaining(sink);
    }
}
