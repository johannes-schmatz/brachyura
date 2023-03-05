package io.github.coolcrabs.brachyura.quilt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import java.util.zip.ZipFile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import io.github.coolcrabs.brachyura.dependency.JavaJarDependency;
import io.github.coolcrabs.brachyura.exception.UnknownJsonException;
import io.github.coolcrabs.brachyura.fabric.AccessWidenerRemapper;
import io.github.coolcrabs.brachyura.fabric.AccessWidenerRemapper.AccessWidenerCollector;
import io.github.coolcrabs.brachyura.fabric.FabricContext;
import io.github.coolcrabs.brachyura.mappings.Namespaces;
import io.github.coolcrabs.brachyura.mappings.tinyremapper.MetaInfFixer;
import io.github.coolcrabs.brachyura.mappings.tinyremapper.RemapperProcessor;
import io.github.coolcrabs.brachyura.mappings.tinyremapper.TrWrapper;
import io.github.coolcrabs.brachyura.maven.MavenId;
import io.github.coolcrabs.brachyura.processing.*;
import io.github.coolcrabs.brachyura.processing.sinks.ZipProcessingSink;
import io.github.coolcrabs.brachyura.processing.sources.ZipProcessingSource;
import io.github.coolcrabs.brachyura.util.*;

public abstract class QuiltContext extends FabricContext {
    @Override
    public ProcessorChain resourcesProcessingChain(List<JavaJarDependency> jij) {
        Path fmjgen = getLocalBrachyuraPath().resolve("fmjgen");
        if (Files.exists(fmjgen)) PathUtil.deleteDirectory(fmjgen);
        List<Pair<String, Supplier<InputStream>>> jij2 = new ArrayList<>();
        for (JavaJarDependency mod : jij) {
                try {
                    try (ZipFile f = new ZipFile(mod.jar.toFile())) {
                        if (f.getEntry("fabric.mod.json") == null && f.getEntry("quilt.mod.json") == null) {
                            ByteArrayOutputStreamEx out = new ByteArrayOutputStreamEx();
                            try (
                                ZipProcessingSource source = new ZipProcessingSource(mod.jar);
                                ZipProcessingSink sink = new ZipProcessingSink(out)
                            ) {
                                ProcessorChain.of(
                                        new FmjGenerator(
                                                Collections.singletonMap(source, mod.mavenId)
                                        )
                                ).apply(sink, source);
                            }
                            jij2.add(new Pair<>(
                                    mod.jar.getFileName().toString(),
                                    out::toIs
                            ));
                        } else {
                            jij2.add(new Pair<>(
                                    mod.jar.getFileName().toString(),
                                    () -> PathUtil.inputStream(mod.jar)
                            ));
                        }
                    }
                } catch (Exception e) {
                    throw Util.sneak(e);
                }
        }
        return ProcessorChain.of(
                QmjRefmapApplier.INSTANCE,
                new QmjJijApplier(jij2),
                new AccessWidenerRemapper(mappings.get(), mappings.get().getNamespaceId(Namespaces.INTERMEDIARY),
                        QuiltAwCollector.INSTANCE)
        );
    }

    @Override
    public Path remappedModsRootPath() {
        return getLocalBrachyuraPath().resolve("quiltdeps");
    }

    @Override
    public byte remappedModsLogicVersion() {
        return 2;
    }

    @Override
    public ProcessorChain modRemapChainOverrideOnlyIfYouOverrideRemappedModsRootPathAndLogicVersion(TrWrapper trw, List<Path> cp, Map<ProcessingSource, MavenId> c) {
        return ProcessorChain.of(
            new RemapperProcessor(trw, cp),
            new MetaInfFixer(trw),
            JijRemover.INSTANCE,
            new AccessWidenerRemapper(mappings.get(), mappings.get().getNamespaceId(Namespaces.NAMED), QuiltAwCollector.INSTANCE),
            new FmjGenerator(c)
        );
    }

    public enum JijRemover implements Processor {
        INSTANCE;

        @Override
        public void process(ProcessingCollector inputs, ProcessingSink sink) throws IOException {
            for (ProcessingEntry e : inputs.map.values()) {
                boolean fmj = "fabric.mod.json".equals(e.id.path);
                boolean qmj = "quilt.mod.json".equals(e.id.path);
                if (fmj || qmj) {
                    Gson gson = new GsonBuilder().setPrettyPrinting().setLenient().create();
                    JsonObject modJson;
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(e.in.get(), StandardCharsets.UTF_8))) {
                        modJson = gson.fromJson(reader, JsonObject.class);
                    }
                    if (fmj) {
                        modJson.remove("jars");
                    }
                    if (qmj) {
                        modJson.getAsJsonObject("quilt_loader").remove("jars");
                    }
                    sink.sink(() -> GsonUtil.toIs(modJson, gson), e.id);
                } else {
                    sink.sink(e.in, e.id);
                }
            }
        }
    }

    public enum QuiltAwCollector implements AccessWidenerCollector {
        INSTANCE;

        @Override
        public List<ProcessingId> collect(ProcessingCollector inputs) throws IOException {
            // TODO: is there a cleaner way for this?

            ArrayList<ProcessingId> result = new ArrayList<>();
            // Prefer a mod's qmj otherwise use fmj
            HashMap<ProcessingSource, ProcessingEntry> mjs = new HashMap<>();
            for (ProcessingEntry entry : inputs.getByPath("fabric.mod.json")) {
                mjs.put(entry.id.source, entry);
            }

            for (ProcessingEntry entry : inputs.getByPath("quilt.mod.json")) {
                mjs.put(entry.id.source, entry);
            }

            for (ProcessingEntry e : mjs.values()) {
                boolean fmj = "fabric.mod.json".equals(e.id.path);
                boolean qmj = "quilt.mod.json".equals(e.id.path);
                if (fmj || qmj) {
                    JsonObject modJson = GsonUtil.fromJson(e, new Gson());
                    JsonElement aw0 = null;
                    if (fmj) aw0 = modJson.get("accessWidener");
                    if (qmj) aw0 = modJson.get("access_widener");
                    if (aw0 != null) {
                        result.add(new ProcessingId(aw0.getAsString(), e.id.source));
                    }
                }
            }
            return result;
        }
    }

    public enum QmjRefmapApplier implements Processor {
        INSTANCE;

        @Override
        public void process(ProcessingCollector inputs, ProcessingSink sink) throws IOException {
            ProcessingEntry qmj = inputs.getByPath("quilt.mod.json").iterator().next();
            if (qmj != null) {
                Gson gson = new GsonBuilder().setPrettyPrinting().setLenient().create();
                List<String> mixinjs = new ArrayList<>();
                JsonObject quiltModJson;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(qmj.in.get(), StandardCharsets.UTF_8))) {
                    quiltModJson = gson.fromJson(reader, JsonObject.class);
                }
                JsonElement m = quiltModJson.get("mixin"); // Not "mixins"
                if (m instanceof JsonArray) {
                    JsonArray mixins = m.getAsJsonArray();
                    for (JsonElement a : mixins) {
                        if (a.isJsonPrimitive()) {
                            mixinjs.add(a.getAsString());
                        } else {
                            throw new UnknownJsonException(a.toString());
                        }
                    }
                } else if (m instanceof JsonPrimitive) {
                    mixinjs.add(m.getAsString());
                } else if (m != null) {
                    throw new UnknownJsonException(m.toString());
                }
                for (String mixin : mixinjs) {
                    ProcessingEntry entry = inputs.removeByPath(mixin).iterator().next();
                    JsonObject mixinJson;
                    try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(entry.in.get(), StandardCharsets.UTF_8))) {
                        mixinJson = gson.fromJson(bufferedReader, JsonObject.class);
                    }
                    if (mixinJson.get("refmap") == null) {
                        mixinJson.addProperty("refmap", quiltModJson.getAsJsonObject("quilt_loader").get("id").getAsString() + "-refmap.json");
                    }
                    sink.sink(() -> GsonUtil.toIs(mixinJson, gson), entry.id);
                }
            }
            inputs.sinkRemaining(sink);
        }
    }

    public static class QmjJijApplier implements Processor {
        public final List<Pair<String, Supplier<InputStream>>> jij;

        public QmjJijApplier(List<Pair<String, Supplier<InputStream>>> jij) {
            this.jij = jij;
        }

        @Override
        public void process(ProcessingCollector inputs, ProcessingSink sink) throws IOException {
            if (!jij.isEmpty()) {
                Gson gson = new GsonBuilder().setPrettyPrinting().setLenient().create();
                for (ProcessingEntry entry : inputs.removeByPath("quilt.mod.json")) {
                    JsonObject quiltModJson = GsonUtil.fromJson(entry, gson);

                    JsonArray jars = new JsonArray();
                    quiltModJson.getAsJsonObject("quilt_loader").add("jars", jars);

                    Set<String> used = new HashSet<>();
                    for (Pair<String, Supplier<InputStream>> jar : jij) {
                        String path = "META-INF/jars/" + jar.getKey();
                        int a = 0;
                        while (used.contains(path)) {
                            path = "META-INF/jars/" + a + jar.getKey();
                            a++;
                        }
                        jars.add(path);
                        used.add(path);
                        sink.sink(jar.getValue(), new ProcessingId(path, entry.id.source));
                    }
                    sink.sink(() -> GsonUtil.toIs(quiltModJson, gson), entry.id);
                }
            }
            inputs.sinkRemaining(sink);
            /*
            for (ProcessingEntry e : inputs.map.values()) {
                if (!jij.isEmpty() && "quilt.mod.json".equals(e.id.path)) {

                    JsonObject quiltModJson;
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(e.in.get(), StandardCharsets.UTF_8))) {
                        quiltModJson = gson.fromJson(reader, JsonObject.class);
                    }
                    JsonArray jars = new JsonArray();
                    quiltModJson.getAsJsonObject("quilt_loader").add("jars", jars);
                    Set<String> used = new HashSet<>();
                    for (Pair<String, Supplier<InputStream>> jar : jij) {
                        String path = "META-INF/jars/" + jar.getKey();
                        int a = 0;
                        while (used.contains(path)) {
                            path = "META-INF/jars/" + a + jar.getKey();
                            a++;
                        }
                        jars.add(path);
                        used.add(path);
                        sink.sink(jar.getValue(), new ProcessingId(path, e.id.source));
                    }
                    sink.sink(() -> GsonUtil.toIs(quiltModJson, gson), e.id);
                } else {
                    sink.sink(e.in, e.id);
                }
            }*/
        }
    }
}
