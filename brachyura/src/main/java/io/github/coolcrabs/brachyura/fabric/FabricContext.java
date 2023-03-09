package io.github.coolcrabs.brachyura.fabric;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.github.coolcrabs.brachyura.mappings.tinyremapper.*;
import io.github.coolcrabs.brachyura.processing.*;
import io.github.coolcrabs.brachyura.processing.processor.TemplateApplier;
import io.github.coolcrabs.brachyura.util.*;
import org.jetbrains.annotations.Nullable;
import org.tinylog.Logger;

import io.github.coolcrabs.accesswidener.AccessWidener;
import io.github.coolcrabs.brachyura.decompiler.BrachyuraDecompiler;
import io.github.coolcrabs.brachyura.dependency.Dependency;
import io.github.coolcrabs.brachyura.dependency.JavaJarDependency;
import io.github.coolcrabs.brachyura.dependency.NativesJarDependency;
import io.github.coolcrabs.brachyura.exception.UnknownJsonException;
import io.github.coolcrabs.brachyura.fabric.AccessWidenerRemapper.FabricAwCollector;
import io.github.coolcrabs.brachyura.mappings.MappingHasher;
import io.github.coolcrabs.brachyura.mappings.MappingHelper;
import io.github.coolcrabs.brachyura.mappings.Namespaces;
import io.github.coolcrabs.brachyura.maven.Maven;
import io.github.coolcrabs.brachyura.maven.MavenId;
import io.github.coolcrabs.brachyura.minecraft.Minecraft;
import io.github.coolcrabs.brachyura.minecraft.VersionMeta;
import io.github.coolcrabs.brachyura.mixin.BrachyuraMixinCompileExtensions;
import io.github.coolcrabs.brachyura.processing.sinks.AtomicZipProcessingSink;
import io.github.coolcrabs.brachyura.processing.sinks.ZipProcessingSink;
import io.github.coolcrabs.brachyura.processing.sources.ProcessingSponge;
import io.github.coolcrabs.brachyura.processing.sources.ZipProcessingSource;
import io.github.coolcrabs.brachyura.project.java.BuildModule;
import io.github.coolcrabs.fabricmerge.JarMerger;
import io.github.coolmineman.trieharder.FindReplaceSourceRemapper;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

public abstract class FabricContext {
    public final Lazy<VersionMeta> versionMeta = Lazy.of(this::createMcVersion);
    public abstract VersionMeta createMcVersion();

    public final Lazy<MappingTree> mappings = Lazy.of(this::createMappings);
    public abstract MappingTree createMappings();

    public abstract FabricLoader getLoader();

    public final Lazy<List<ModDependency>> modDependencies = Lazy.of(() -> {
        ModDependencyCollector d = new ModDependencyCollector();
        getModDependencies(d);
        return d.dependencies;
    });
    public abstract void getModDependencies(ModDependencyCollector d);

    public abstract Path getContextRoot();

    public final Lazy<Optional<AccessWidener>> aw = Lazy.of(() -> Optional.ofNullable(createAw()));
    protected @Nullable AccessWidener createAw() {
        return null;
    }

    public @Nullable BrachyuraDecompiler decompiler() {
        return null;
    }

    public MappingTree createMojmap() {
        return createMojmap(intermediary.get(), versionMeta.get());
    }

    public static MappingTree createMojmap(MappingTree intermediary, VersionMeta meta) {
        try {
            MemoryMappingTree r = new MemoryMappingTree(true);
            intermediary.accept(r);
            Minecraft.getMojmap(meta).accept(r);
            MappingHelper.dropNullInNamespace(r, Namespaces.INTERMEDIARY);
            return r;
        } catch (IOException e) {
            throw Util.sneak(e);
        }
    }

    public Map<BuildModule, ProcessingSource> getRemappedClasses(BuildModule... modules) {
        HashMap<BuildModule, ProcessingSource> r = new HashMap<>();
        ArrayList<FabricModule> fmods = new ArrayList<>();
        for (BuildModule m : modules) {
            if (m instanceof FabricModule) {
                fmods.add((FabricModule)m);
            } else {
                r.put(m, m.compilationOutput.get());
            }
        }
        MemoryMappingTree compmappings = new MemoryMappingTree(true);
        try {
            mappings.get().accept(new MappingSourceNsSwitch(compmappings, Namespaces.NAMED));
            for (FabricModule fm : fmods) {
                MappingTree mm = fm.fabricCompilationResult.get().mixinMappings; 
                if (mm != null) mm.accept(compmappings);
            }
        } catch (IOException e) {
            throw Util.sneak(e);
        }
        ProcessingSponge thonk = new ProcessingSponge();
        HashMap<ProcessingId, ProcessingSponge> sm = new HashMap<>();
        for (FabricModule fm : fmods) {
            ProcessingSponge s = new ProcessingSponge();
            fm.compilationOutput.get().getInputs((in, id) -> {
                sm.put(id, s);
                thonk.sink(in, id);
            });
            r.put(fm, s);
        }
        try (TrWrapper trw = TrUtil.newRemapper().withMappings(new MappingTreeMappingProvider(compmappings, Namespaces.NAMED,
                Namespaces.INTERMEDIARY)).build()) {
            ProcessorChain.of(
                new RemapperProcessor(
                    trw,
                    getCompileDependencies()
                )
            ).apply(
                (in, id) -> sm.get(id).sink(in, id),
                thonk
            );
        }
        return r;
    }

    public static class FmjJijApplier implements Processor {
        final List<Pair<String, Supplier<InputStream>>> jij;

        public FmjJijApplier(List<Pair<String, Supplier<InputStream>>> jij) {
            this.jij = jij;
        }

        @Override
        public void process(ProcessingCollector inputs, ProcessingSink sink) throws IOException {
            if (!jij.isEmpty()) {
                Gson gson = new GsonBuilder().setPrettyPrinting().setLenient().create();
                for (ProcessingEntry entry : inputs.removeByPath("fabric.mod.json")) {
                    JsonObject fabricModJson = GsonUtil.fromJson(entry, gson);

                    JsonArray jars = new JsonArray();
                    fabricModJson.add("jars", jars);

                    Set<String> used = new HashSet<>();
                    for (Pair<String, Supplier<InputStream>> jar : jij) {
                        String path = "META-INF/jars/" + jar.getKey();
                        int a = 0;
                        while (used.contains(path)) {
                            path = "META-INF/jars/" + a + jar.getKey();
                            a++;
                        }

                        JsonObject o = new JsonObject();
                        o.addProperty("file", path);
                        jars.add(o);
                        used.add(path);
                        sink.sink(jar.getValue(), new ProcessingId(path, entry.id.source));
                    }

                    sink.sink(() -> GsonUtil.toIs(fabricModJson, gson), entry.id);
                }
            }
            inputs.sinkRemaining(sink);
        }
    }

    public enum FMJRefmapApplier implements Processor {
        INSTANCE;

        @Override
        public void process(ProcessingCollector inputs, ProcessingSink sink) throws IOException {
            ProcessingEntry fmj = inputs.getByPath("fabric.mod.json").iterator().next();
            if (fmj != null) {
                Gson gson = new GsonBuilder().setPrettyPrinting().setLenient().create();
                List<String> mixinjs = new ArrayList<>();
                JsonObject fabricModJson;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(fmj.in.get(), StandardCharsets.UTF_8))) {
                    fabricModJson = gson.fromJson(reader, JsonObject.class);
                }
                JsonElement m = fabricModJson.get("mixins");
                if (m instanceof JsonArray) {
                    JsonArray mixins = m.getAsJsonArray();
                    for (JsonElement a : mixins) {
                        if (a.isJsonPrimitive()) {
                            mixinjs.add(a.getAsString());
                        } else if (a.isJsonObject()) {
                            mixinjs.add(a.getAsJsonObject().get("config").getAsString());
                        } else {
                            throw new UnknownJsonException(a.toString());
                        }
                    }
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
                        mixinJson.addProperty("refmap", fabricModJson.get("id").getAsString() + "-refmap.json");
                    }
                    sink.sink(() -> GsonUtil.toIs(mixinJson, gson), entry.id);
                }
            }
            inputs.sinkRemaining(sink);
        }
    }

    public ProcessorChain resourcesProcessingChain(List<JavaJarDependency> jij) {
        Path fmjgen = getLocalBrachyuraPath().resolve("fmjgen");
        if (Files.exists(fmjgen)) PathUtil.deleteDirectory(fmjgen);
        List<Pair<String, Supplier<InputStream>>> jij2 = new ArrayList<>();
        for (JavaJarDependency mod : jij) {
                try {
                    try (ZipFile f = new ZipFile(mod.jar.toFile())) {
                        if (f.getEntry("fabric.mod.json") == null) {
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
                FMJRefmapApplier.INSTANCE,
                new FmjJijApplier(jij2),
                new AccessWidenerRemapper(mappings.get(), mappings.get().getNamespaceId(Namespaces.INTERMEDIARY),
                        FabricAwCollector.INSTANCE)
        );
    }

    public ProcessorChain templateResourceProcessingChain() {
        return ProcessorChain.of(new TemplateApplier(getTemplateMappingsForResources(), true));
    }

    public ProcessorChain templateSourcesProcessingChain() {
        return ProcessorChain.of(new TemplateApplier(getTemplateMappingsForSources(), false));
    }

    public Map<String, Lazy<String>> getTemplateMappingsForSources() {
        return Collections.emptyMap();
    }

    public Map<String, Lazy<String>> getTemplateMappingsForResources() {
        return Collections.emptyMap();
    }

    public static class ModDependencyCollector {
        public final List<ModDependency> dependencies = new ArrayList<>();

        public JavaJarDependency addMaven(String repo, MavenId id, ModDependencyFlag... flags) {
            return add(Maven.getMavenJarDep(repo, id), flags);
        }

        public JavaJarDependency add(JavaJarDependency jarDependency, ModDependencyFlag... flags) {
            if (flags.length == 0) throw new UnsupportedOperationException("Must have atleast one dependency flag");
            EnumSet<ModDependencyFlag> flags2 = EnumSet.of(flags[0], flags); // Bruh
            dependencies.add(new ModDependency(jarDependency, flags2));
            return jarDependency;
        }
    }

    public static class ModDependency {
        public final JavaJarDependency jarDependency;
        public final Set<ModDependencyFlag> flags;

        public ModDependency(JavaJarDependency jarDependency, Set<ModDependencyFlag> flags) {
            this.jarDependency = jarDependency;
            this.flags = flags;
        }
    }

    public enum ModDependencyFlag {
        COMPILE,
        RUNTIME
    }

    public Path remappedModsRootPath() {
        return getLocalBrachyuraPath().resolve("fabricdeps");
    }

    public byte remappedModsLogicVersion() {
        return 1;
    }

    public ProcessorChain modRemapChainOverrideOnlyIfYouOverrideRemappedModsRootPathAndLogicVersion(TrWrapper trw, List<Path> cp, Map<ProcessingSource, MavenId> c) {
        return ProcessorChain.of(
            new RemapperProcessor(trw, cp),
            new MetaInfFixer(trw),
            JijRemover.INSTANCE,
            new AccessWidenerRemapper(mappings.get(), mappings.get().getNamespaceId(Namespaces.NAMED), FabricAwCollector.INSTANCE),
            new FmjGenerator(c)
        );
    }

    public final Lazy<List<ModDependency>> remappedModDependencies = Lazy.of(this::createRemappedModDependencies);
    protected List<ModDependency> createRemappedModDependencies() {
        class RemapInfo {
            ModDependency source;
            ModDependency target;
        }
        try {
            List<ModDependency> unmapped = modDependencies.get();
            if (unmapped == null || unmapped.isEmpty()) return Collections.emptyList();
            List<RemapInfo> remapinfo = new ArrayList<>(unmapped.size());
            List<ModDependency> remapped = new ArrayList<>(unmapped.size());
            MessageDigest dephasher = MessageDigestUtil.messageDigest(MessageDigestUtil.SHA256);
            dephasher.update(remappedModsLogicVersion()); // Bump this if the logic changes
            for (ModDependency dep : unmapped) {
                hashDep(dephasher, dep);
            }
            for (JavaJarDependency dep : mcClasspath.get()) {
                hashDep(dephasher, dep);
            }
            dephasher.update(namedJar.get().mappingHash.getBytes(StandardCharsets.UTF_8));
            dephasher.update(intermediaryjar.get().mappingHash.getBytes(StandardCharsets.UTF_8));
            MessageDigestUtil.update(dephasher, TinyRemapperHelper.VERSION);
            String dephash = MessageDigestUtil.toHexHash(dephasher.digest());
            Path depdir = remappedModsRootPath();
            Path resultdir = depdir.resolve(dephash);
            for (ModDependency u : unmapped) {
                RemapInfo ri = new RemapInfo();
                remapinfo.add(ri);
                ri.source = u;
                ri.target = new ModDependency(
                    new JavaJarDependency(
                        resultdir.resolve(
                            u.jarDependency.jar.getFileName().toString()
                        ),
                        u.jarDependency.sourcesJar == null ? null : resultdir.resolve(u.jarDependency.jar.getFileName().toString().replace(".jar", "-sources.jar")),
                        u.jarDependency.mavenId
                    ),
                    u.flags
                );
                remapped.add(ri.target);
            }
            if (!Files.isDirectory(resultdir)) {
                if (Files.isDirectory(depdir)) {
                    PathUtil.deleteDirectoryChildren(depdir);
                }
                try (AtomicDirectory a = new AtomicDirectory(resultdir)) {
                    TrUtil.Builder tr = TrUtil.newRemapper()
                        .withMappings(new MappingTreeMappingProvider(mappings.get(), Namespaces.INTERMEDIARY, Namespaces.NAMED))
                        .renameInvalidLocals(false);
                    ArrayList<Path> cp = new ArrayList<>();
                    cp.add(intermediaryjar.get().jar);
                    for (JavaJarDependency dep : mcClasspath.get()) {
                        cp.add(dep.jar);
                    }
                    HashMap<ProcessingSource, ZipProcessingSink> b = new HashMap<>();
                    HashMap<ProcessingSource, MavenId> c = new HashMap<>();
                    try (CloseableArrayList toClose = new CloseableArrayList()) {
                        for (RemapInfo ri : remapinfo) {
                            // Make sure sink closes before source
                            ZipProcessingSink si = new ZipProcessingSink(a.tempPath.resolve(ri.target.jarDependency.jar.getFileName()));
                            toClose.add(si);
                            ZipProcessingSource s = new ZipProcessingSource(ri.source.jarDependency.jar);
                            toClose.add(s);
                            b.put(s, si);
                            c.put(s, ri.source.jarDependency.mavenId);
                        }
                        Logger.info("Remapping {} mods", b.size());
                        try (TrWrapper trw = tr.build()) {
                            modRemapChainOverrideOnlyIfYouOverrideRemappedModsRootPathAndLogicVersion(trw, cp, c).apply(
                                (in, id) -> b.get(id.source).sink(in, id),
                                b.keySet()
                            );
                        }
                    }
                    FindReplaceSourceRemapper sourceRemapper = new FindReplaceSourceRemapper(mappings.get(), mappings.get().getNamespaceId(Namespaces.INTERMEDIARY), mappings.get().getNamespaceId(Namespaces.NAMED));
                    for (ModDependency u : unmapped) { 
                        if (u.jarDependency.sourcesJar != null) {
                            Path target = a.tempPath.resolve(u.jarDependency.jar.getFileName().toString().replace(".jar", "-sources.jar"));
                            sourceRemapper.remapSourcesJar(u.jarDependency.sourcesJar, target);
                        }
                    }
                    a.commit();
                }
            }
            return remapped;
        } catch (Exception e) {
            throw Util.sneak(e);
        }
    }

    public void hashDep(MessageDigest md, ModDependency dep) {
        hashDep(md, dep.jarDependency);
        for (ModDependencyFlag flag : dep.flags) {
            MessageDigestUtil.update(md, flag.toString());
        }
    }

    public void hashDep(MessageDigest md, JavaJarDependency dep) {
        hashDepFile(md, dep.jar);
        MessageDigestUtil.update(md, (byte)(dep.sourcesJar == null ? 0 : 1));
        if (dep.sourcesJar != null) {
            MessageDigestUtil.update(md, (byte)0);
            hashDepFile(md, dep.sourcesJar);
        }
    }

    public void hashDepFile(MessageDigest md, Path file) {
        MessageDigestUtil.update(md, file.toAbsolutePath().toString());
        try {
            BasicFileAttributes attr = Files.readAttributes(file, BasicFileAttributes.class);
            Instant time = attr.lastModifiedTime().toInstant();
            MessageDigestUtil.update(md, time.getEpochSecond());
            MessageDigestUtil.update(md, time.getNano());
            MessageDigestUtil.update(md, attr.size());
        } catch (IOException e) {
            Logger.warn(e);
        }
    }

    public enum JijRemover implements Processor {
        INSTANCE;

        @Override
        public void process(ProcessingCollector inputs, ProcessingSink sink) throws IOException {
            for (ProcessingEntry e : inputs.map.values()) {
                if ("fabric.mod.json".equals(e.id.path)) {
                    Gson gson = new GsonBuilder().setPrettyPrinting().setLenient().create();
                    JsonObject fabricModJson;
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(e.in.get(), StandardCharsets.UTF_8))) {
                        fabricModJson = gson.fromJson(reader, JsonObject.class);
                    }
                    fabricModJson.remove("jars");
                    sink.sink(() -> GsonUtil.toIs(fabricModJson, gson), e.id);
                } else {
                    sink.sink(e.in, e.id);
                }
            }
        }
    }

    // https://github.com/FabricMC/fabric-loom/blob/dev/0.11/src/main/java/net/fabricmc/loom/build/nesting/IncludedJarFactory.java
    public static class FmjGenerator implements Processor {
        public final Map<ProcessingSource, MavenId> map;

        public FmjGenerator(Map<ProcessingSource, MavenId> map) {
            this.map = map;
        }

        @Override
        public void process(ProcessingCollector inputs, ProcessingSink sink) throws IOException {
            HashSet<ProcessingSource> fmj = new HashSet<>();
            for (ProcessingEntry e : inputs.map.values()) {
                if ("fabric.mod.json".equals(e.id.path)) {
                    fmj.add(e.id.source);
                }
                sink.sink(e.in, e.id);
            }
            for (Map.Entry<ProcessingSource, MavenId> e : map.entrySet()) {
                if (!fmj.contains(e.getKey())) {
                    Logger.info("Generating fmj for {}", e.getValue());
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    String modId = (e.getValue().groupId + "_" + e.getValue().artifactId).replace('.', '_').toLowerCase(Locale.ENGLISH);
                    if (modId.length() > 64) {
                        MessageDigest md = MessageDigestUtil.messageDigest(MessageDigestUtil.SHA256);
                        MessageDigestUtil.update(md, modId);
                        modId = modId.substring(0, 50) + MessageDigestUtil.toHexHash(md.digest()).substring(0, 14);
                    }
                    final JsonObject jsonObject = new JsonObject();
                    jsonObject.addProperty("schemaVersion", 1);
                    jsonObject.addProperty("id", modId);
                    jsonObject.addProperty("version", e.getValue().version);
                    jsonObject.addProperty("name", e.getValue().artifactId);
                    JsonObject custom = new JsonObject();
                    custom.addProperty("fabric-loom:generated", true);
                    jsonObject.add("custom", custom);
                    sink.sink(() -> GsonUtil.toIs(jsonObject, gson), new ProcessingId("fabric.mod.json", e.getKey()));
                }
            }
        }
    }

    public final Lazy<List<Dependency>> mcDependencies = Lazy.of(this::createMcDependencies);
    protected List<Dependency> createMcDependencies() {
        ArrayList<Dependency> result = new ArrayList<>(Minecraft.getDependencies(versionMeta.get()));
        result.add(Maven.getMavenJarDep(Maven.MAVEN_CENTRAL, new MavenId("org.jetbrains", "annotations", "19.0.0")));
        return result;
    }

    public List<Path> getCompileDependencies() {
        List<Path> result = new ArrayList<>();
        for (Dependency dependency : dependencies.get()) {
            if (dependency instanceof JavaJarDependency) {
                result.add(((JavaJarDependency) dependency).jar);
            }
        }
        result.add(namedJar.get().jar);
        result.add(BrachyuraMixinCompileExtensions.getJar());
        for (ModDependency dep : remappedModDependencies.get()) {
            if (dep.flags.contains(ModDependencyFlag.COMPILE)) result.add(dep.jarDependency.jar);
        }
        return result;
    }

    public final Lazy<List<JavaJarDependency>> ideDependencies = Lazy.of(this::createIdeDependencies);
    protected List<JavaJarDependency> createIdeDependencies() {
        List<JavaJarDependency> result = new ArrayList<>();
        for (Dependency dependency : dependencies.get()) {
            if (dependency instanceof JavaJarDependency) {
                result.add((JavaJarDependency) dependency);
            }
        }
        result.add(decompiledJar.get());
        for (ModDependency d : remappedModDependencies.get()) {
            if (d.flags.contains(ModDependencyFlag.COMPILE)) result.add(d.jarDependency);
        }
        return result;
    }

    public final Lazy<List<JavaJarDependency>> runtimeDependencies = Lazy.of(this::createRuntimeDependencies);
    protected List<JavaJarDependency> createRuntimeDependencies() {
        List<JavaJarDependency> result = new ArrayList<>();
        for (Dependency dependency : dependencies.get()) {
            if (dependency instanceof JavaJarDependency) {
                result.add((JavaJarDependency) dependency);
            }
        }
        result.add(Maven.getMavenJarDep(Maven.MAVEN_CENTRAL, new MavenId("net.minecrell", "terminalconsoleappender", "1.2.0")));
        result.add(decompiledJar.get());
        for (ModDependency d : remappedModDependencies.get()) {
            if (d.flags.contains(ModDependencyFlag.RUNTIME)) result.add(d.jarDependency);
        }
        return result;
    }

    public final Lazy<List<Dependency>> dependencies = Lazy.of(this::createDependencies);
    protected List<Dependency> createDependencies() {
        List<Dependency> result = new ArrayList<>(mcDependencies.get());
        FabricLoader floader = getLoader();
        result.add(floader.jar);
        Collections.addAll(result, floader.commonDeps);
        Collections.addAll(result, floader.serverDeps);
        Collections.addAll(result, floader.clientDeps);
        return result;
    }

    public final Lazy<MappingTree> intermediary = Lazy.of(this::createIntermediary);
    protected MappingTree createIntermediary() {
        return Intermediary.ofMaven(FabricMaven.URL, FabricMaven.intermediary(versionMeta.get().version)).tree;
    }

    public Path getMergedJar() {
        try {
            Path vanillaClientJar = Minecraft.getDownload(versionMeta.get(), "client");
            Path vanillaServerJar = Minecraft.getDownload(versionMeta.get(), "server");
            try (ZipFile file = new ZipFile(vanillaServerJar.toFile())) {
                ZipEntry entry = file.getEntry("META-INF/versions.list");
                if (entry != null) {
                    String jar;
                    try (InputStream is = file.getInputStream(entry)) {
                        jar = StreamUtil.readFullyAsString(is).split("\t")[2];
                    }
                    vanillaServerJar = fabricCache().resolve("serverextract").resolve(jar);
                    if (!Files.isRegularFile(vanillaServerJar)) {
                        try (
                            AtomicFile f = new AtomicFile(vanillaServerJar);
                            InputStream is = file.getInputStream(file.getEntry("META-INF/versions/" + jar))
                        ) {
                            Files.copy(is, f.tempPath, StandardCopyOption.REPLACE_EXISTING);
                            f.commit();
                        }
                    }
                }
            }
            Path result = fabricCache().resolve("merged").resolve(versionMeta.get().version + "-merged.jar");
            if (!Files.isRegularFile(result)) {
                try (AtomicFile atomicFile = new AtomicFile(result)) {
                    try (JarMerger jarMerger = new JarMerger(vanillaClientJar, vanillaServerJar, atomicFile.tempPath)) {
                        jarMerger.enableSyntheticParamsOffset();
                        jarMerger.merge();
                    }
                    atomicFile.commit();
                }
            }
            return result;
        } catch (Exception e) {
            throw Util.sneak(e);
        }
    }

    public final Lazy<RemappedJar> intermediaryjar = Lazy.of(this::createIntermediaryJar);
    protected RemappedJar createIntermediaryJar() {
            Path mergedJar = getMergedJar();
            String intermediaryHash = MappingHasher.hashSha256(intermediary.get());
            Path result = fabricCache().resolve("intermediary").resolve(versionMeta.get().version + TinyRemapperHelper.getFileVersionTag() + "intermediary-" + intermediaryHash + ".jar");
            if (!Files.isRegularFile(result)) {
                try (AtomicFile atomicFile = new AtomicFile(result)) {
                    remapJar(intermediary.get(), Namespaces.OBF, Namespaces.INTERMEDIARY, mergedJar, atomicFile.tempPath, mcClasspathPaths.get());
                    atomicFile.commit();
                }
            }
            return new RemappedJar(result, intermediaryHash);
    }

    public final Lazy<RemappedJar> remappedNamedJar = Lazy.of(this::createRemappedNamedJar);
    protected RemappedJar createRemappedNamedJar() {
        MessageDigest md = MessageDigestUtil.messageDigest(MessageDigestUtil.SHA256);
        MessageDigestUtil.update(md, intermediaryjar.get().mappingHash);
        MappingHasher.hash(md, mappings.get());
        String mappingHash = MessageDigestUtil.toHexHash(md.digest());
        Path result = fabricCache().resolve("named").resolve(versionMeta.get().version + TinyRemapperHelper.getFileVersionTag() + "named-" + mappingHash + ".jar");
        if (!Files.isRegularFile(result)) {
            try (AtomicFile atomicFile = new AtomicFile(result)) {
                remapJar(mappings.get(), Namespaces.INTERMEDIARY, Namespaces.NAMED, intermediaryjar.get().jar, atomicFile.tempPath, mcClasspathPaths.get());
                atomicFile.commit();
            }
        }
        return new RemappedJar(result, mappingHash);
    }

    public final Lazy<RemappedJar> namedJar = Lazy.of(this::createNamedJar);
    protected RemappedJar createNamedJar() {
        HashableProcessor[] hps = namedJarProcessors();
        if (hps.length == 0) return remappedNamedJar.get();
        MessageDigest md = MessageDigestUtil.messageDigest(MessageDigestUtil.SHA256);
        MessageDigestUtil.update(md, remappedNamedJar.get().mappingHash);
        for (HashableProcessor hp : hps) {
            hp.hash(md);
        }
        String hash = MessageDigestUtil.toHexHash(md.digest());
        Path result = getLocalBrachyuraPath().resolve("fabric").resolve("named").resolve(versionMeta.get().version + TinyRemapperHelper.getFileVersionTag() + "named-" + hash + ".jar");
        if (!Files.isRegularFile(result)) {
            try (
                ZipProcessingSource s = new ZipProcessingSource(remappedNamedJar.get().jar);
                AtomicZipProcessingSink a = new AtomicZipProcessingSink(result);
            ) {
                ProcessorChain.of(hps).apply(a, s);
                a.commit();
            }
        }
        return new RemappedJar(result, hash);
    }

    public HashableProcessor[] namedJarProcessors() {
        Optional<AccessWidener> oaw = aw.get();
        if (oaw.isPresent()) {
            return new HashableProcessor[] {new AccessWidenerProcessor(oaw.get())};
        } else {
            return new HashableProcessor[0];
        }
    }

    public final Lazy<JavaJarDependency> decompiledJar = Lazy.of(this::createDecompiledJar);
    protected JavaJarDependency createDecompiledJar() {
        RemappedJar named = namedJar.get();
        BrachyuraDecompiler decompiler = decompiler();
        if (decompiler == null) return new JavaJarDependency(named.jar, null, null);
        // Different Java Versions have different classes
        // This will lead to missing classes if ran on an older jdk and MC uses newer jdk
        // Adding the JVM version to the directory avoids this issue if you rerun with a newer jdk
        Path resultDir = fabricCache().resolve("decompiled").resolve(decompiler.getName() + "-" + decompiler.getVersion()).resolve(versionMeta.get().version + TinyRemapperHelper.getFileVersionTag() + "named-" + named.mappingHash + "-J" + JvmUtil.CURRENT_JAVA_VERSION);
        return decompiler.getDecompiled(named.jar, decompClasspath(), resultDir, mappings.get(), Namespaces.NAMED).toJavaJarDep(null);
    }

    public void remapJar(MappingTree mappings, String src, String dst, Path inputJar, Path outputJar, List<Path> classpath) {
        TrUtil.Builder remapperBuilder = TrUtil.newRemapper()
            .withMappings(new MappingTreeMappingProvider(mappings, src, dst))
            .withMappings(Jsr2JetbrainsMappingProvider.INSTANCE)
            .renameInvalidLocals(true)
            .invalidLvNamePattern(Pattern.compile("\\$\\$\\d+"))
            .rebuildSourceFilenames(true);
        try {
            Files.deleteIfExists(outputJar);
        } catch (IOException e) {
            throw Util.sneak(e);
        }
        try (
            ZipProcessingSource source = new ZipProcessingSource(inputJar);
            ZipProcessingSink sink = new ZipProcessingSink(outputJar);
            TrWrapper trw = remapperBuilder.build();
        ) {
            ProcessorChain.of(
                    new RemapperProcessor(trw, classpath)
            ).apply(sink, source);
        }
    }

    public List<Path> decompClasspath() {
        List<Path> result = new ArrayList<>(mcClasspath.get().size() + 1);
        for (JavaJarDependency dep : mcClasspath.get()) {
            result.add(dep.jar);
        }
        result.add(Maven.getMavenJarDep(FabricMaven.URL, FabricMaven.loader("0.9.3+build.207")).jar); // Just for the annotations added by fabric-merge
        return result;
    }
    
    public final Lazy<List<JavaJarDependency>> mcClasspath = Lazy.of(this::createMcClasspath);
    protected final Lazy<List<Path>> mcClasspathPaths = Lazy.of(() -> {
        ArrayList<Path> result = new ArrayList<>(mcClasspath.get().size());
        for (JavaJarDependency dep : mcClasspath.get()) {
            result.add(dep.jar);
        }
        return result;
    });

    public List<JavaJarDependency> createMcClasspath() {
        List<Dependency> deps = mcDependencies.get();
        List<JavaJarDependency> result = new ArrayList<>();
        for (Dependency dependency : deps) {
            if (dependency instanceof JavaJarDependency) {
                result.add((JavaJarDependency)dependency);
            }
        }
        return result;
    }

    public Path fabricCache() {
        return PathUtil.cachePath().resolve("fabric");
    }

    public Path getLocalBrachyuraPath() {
        return PathUtil.resolveAndCreateDir(getContextRoot(), ".brachyura");
    }

    public static class RemappedJar {
        public final Path jar;
        public final String mappingHash;

        public RemappedJar(Path jar, String mappingHash) {
            this.jar = jar;
            this.mappingHash = mappingHash;
        }
    }

    public final Lazy<Path> runtimeRemapClasspath = Lazy.of(this::createRuntimeRemapClasspath);
    protected Path createRuntimeRemapClasspath() {
        List<Path> result = new ArrayList<>();
        for (Dependency dependency : dependencies.get()) {
            if (dependency instanceof JavaJarDependency) {
                result.add(((JavaJarDependency) dependency).jar);
            }
        }
        for (ModDependency dep : modDependencies.get()) {
            result.add(dep.jarDependency.jar);
        }
        result.add(intermediaryjar.get().jar);
        Path target = PathUtil.resolveAndCreateDir(getLocalBrachyuraPath(), "remapclasspath").resolve("remapClasspath.txt");
        try {
            Files.deleteIfExists(target);
            Files.copy(new ByteArrayInputStream(result.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)).getBytes(StandardCharsets.UTF_8)), target);
        } catch (IOException e) {
            throw Util.sneak(e);
        }
        return target;
    }

    public final Lazy<List<Path>> extractedNatives = Lazy.of(this::createExtractedNatives);
    protected List<Path> createExtractedNatives() {
        List<Path> result = new ArrayList<>();
        for (Dependency dependency : mcDependencies.get()) {
            if (dependency instanceof NativesJarDependency) {
                NativesJarDependency nativesJarDependency = (NativesJarDependency) dependency;
                Path target = Minecraft.mcCache().resolve("natives-cache").resolve(Minecraft.mcLibCache().relativize(nativesJarDependency.jar));
                if (!Files.isDirectory(target)) {
                    try (AtomicDirectory atomicDirectory = new AtomicDirectory(target)) {
                        UnzipUtil.unzipToDir(nativesJarDependency.jar, atomicDirectory.tempPath);
                        atomicDirectory.commit();
                    }
                }
                result.add(target);
            }
        }
        return result;
    }

    public final Lazy<String> downloadedAssets = Lazy.of(this::createDownloadedAssets);
    protected String createDownloadedAssets() {
        return Minecraft.downloadAssets(versionMeta.get());
    }
}
