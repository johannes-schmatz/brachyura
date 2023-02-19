import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributeView;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/*
 * Written to avoid inner/annon classes so it can be run with java Buildscript.java without creating class files
 * https://github.com/CoolCrabs/brachyura/blob/d5c86237c25c5cf32ece023686e85258b6ca8a89/Buildscript.java
 */
public class Buildscript {
    public static final int CURRENT_JAVA_VERSION = getVersion();

    public static final Path ROOT;
    public static final Path BRACHYURA;
    public static final Path DOT_BUILDSCRIPT;
    public static final Path DEPS;
    public static final Path BUILD;
    public static final JavaCompiler COMPILER = ToolProvider.getSystemJavaCompiler();

    public static int getVersion() {
        // https://stackoverflow.com/a/2591122
        // Changed to java.specification.version to avoid -ea and other various odditites
        // See https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/System.html#getProperties()
        // and https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/Runtime.Version.html
        String version = System.getProperty("java.specification.version");
        if (version.startsWith("1.")) {
            version = version.substring(2, 3);
        } else {
            int dot = version.indexOf(".");
            if (dot != -1) {
                version = version.substring(0, dot);
            }
        }
        return Integer.parseInt(version);
    }

    static {
        System.setProperty("http.agent", "brachyura");
        try {
            ROOT = Paths.get("").toAbsolutePath();
            BRACHYURA = ROOT.resolve("brachyura");

            DOT_BUILDSCRIPT = ROOT.resolve(".buildscript");
            Files.createDirectories(DOT_BUILDSCRIPT);

            DEPS = DOT_BUILDSCRIPT.resolve("deps");
            Files.createDirectories(DEPS);

            BUILD = DOT_BUILDSCRIPT.resolve("build");
            Files.createDirectories(BUILD);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void compileArgs(List<String> args) {
        int compilerversion = CURRENT_JAVA_VERSION;
        int targetversion = 8;
        if (compilerversion == targetversion) return;
        if (compilerversion >= 9 && targetversion >= 7) {
            args.add("--release");
            args.add(String.valueOf(targetversion)); // Doesn't accept 1.8 etc for some reason
        } else {
            throw new UnsupportedOperationException("Target Version: " + targetversion + " " + "Compiler Version: " + compilerversion);
        }
    }

    public static void main(String[] args) throws Throwable {
        // compile
        List<Path> classpath = get();

        URL[] urls = new URL[classpath.size()];
        for (int i = 0; i < classpath.size(); i++) {
            urls[i] = classpath.get(i).toUri().toURL();
        }

        ClassLoader classLoader = new URLClassLoader(urls, ClassLoader.getSystemClassLoader()/*.getParent()*/);

        Thread.currentThread().setContextClassLoader(classLoader);


        // setup EntryGlobals
        Class<?> entryGlobals = Class.forName("io.github.coolcrabs.brachyura.project.EntryGlobals", true, classLoader);
        Method m = entryGlobals.getDeclaredMethod("set", Path.class, List.class); // @Nullable Path, @Nullable List<Path>
        m.setAccessible(true);
        m.invoke(null, ROOT, classpath);

        // call the entry
        Class<?> entry = Class.forName("io.github.coolcrabs.brachyura.project.BrachyuraEntry", true, classLoader);
        MethodHandles.publicLookup().findStatic(
                entry,
                "main",
                MethodType.methodType(void.class, String[].class)
        ).invokeExact(args);
    }

    // https://www.baeldung.com/java-delete-directory
    public static boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }
    
    public static void update(MessageDigest md, String string) {
        md.update(string.getBytes(StandardCharsets.UTF_8));
    }

    public static void update(MessageDigest md, long i) {
        md.update(
            new byte[] {
                (byte)(i >>> 56),
                (byte)(i >>> 48),
                (byte)(i >>> 40),
                (byte)(i >>> 32),
                (byte)(i >>> 24),
                (byte)(i >>> 16),
                (byte)(i >>> 8),
                (byte)i
            }
        );
    }

    private static final String HEXES = "0123456789ABCDEF";
    // https://www.rgagnon.com/javadetails/java-0596.html
    public static String toHexHash(byte[] hash) {
        if (hash == null) {
            return null;
        }
        final StringBuilder hex = new StringBuilder(2 * hash.length);
        for (final byte b : hash) {
            hex.append(HEXES.charAt((b & 0xF0) >> 4)).append(HEXES.charAt((b & 0x0F)));
        }
        return hex.toString();
    }

    private static final String[] exts = {".jar", "-sources.jar"};
    private static final Map<String, Path> alreadyGotten = new HashMap<>();

    public static Supplier<Path> lib(String repo, String group, String id, String version) {
        return () -> libGet(repo, group, id, version);
    }

    private static Path libGet(String repo, String group, String id, String version) {
        String baseUrl = repo.endsWith("/") ? repo : (repo + "/");

        Path r = null;
        for (String ext : exts) {
            String fileName = id + "-" + version + ext;
            String urlString = baseUrl + group.replace('.', '/') + "/" + id + "/" + version + "/" + fileName;

            if (!alreadyGotten.containsKey(urlString)) {
                Path p = DEPS.resolve(fileName);
                if (!Files.exists(p)) {
                    try {
                        Path tmp = Files.createTempFile(p.getParent(), null, null);
                        Files.delete(tmp); // delete as the above line creates files..., TODO: make this use the algo from in there instead and not create a file

                        try {
                            URL url = new URL(urlString);

                            if (!"https".equals(url.getProtocol())) {
                                System.out.println("WARN: You should use https instead of http!");
                            }

                            System.out.println("Downloading: " + id + " from " + url + " to " + p);

                            try (InputStream in = url.openStream()) {
                                Files.copy(in, tmp);
                            }
                            Files.move(tmp, p);

                        } finally {
                            Files.deleteIfExists(tmp);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                alreadyGotten.put(urlString, p);
            }

            if (".jar".equals(ext)) {
                r = alreadyGotten.get(urlString);
            }
        }

        return Objects.requireNonNull(r, "lib somehow is null, shoudln't happen!");
    }

    public static Supplier<List<Path>> mod(String name, Supplier<?>... deps) {
        return () -> {
            try {
                return modGet(name, deps);
            } catch (Exception e) {
                if (e instanceof RuntimeException) throw (RuntimeException) e;
                throw new RuntimeException(e);
            }
        };
    }

    public static List<Path> modGet(String name, Supplier<?>[] deps) throws Exception {
        // TODO: make this store itself in a global Map<String, List<Path> so that it behaves like a Lazy<>

        Path baseDir = ROOT.resolve(name);
        Path buildDir = BUILD.resolve(name);

        Set<Path> depsToRun = new LinkedHashSet<>();
        List<Path> depsToCompile = new ArrayList<>();
        for (Supplier<?> dep : deps) {
            Object o = dep.get();
            if (o instanceof List) {
                @SuppressWarnings("unchecked")
                List<Path> modDep = (List<Path>) o;

                // for running we need everything
                depsToRun.addAll(modDep);

                // for compilation we only need the module
                Path mod = modDep.get(0);
                depsToCompile.add(mod);
            } else if (o instanceof Path) {
                Path libDep = (Path) o;

                // for running we need it
                depsToRun.add(libDep);

                // for compilation we need it
                depsToCompile.add(libDep);
            } else {
                throw new RuntimeException("Could not get depedency: " + o.getClass() + " cannot be castet to either a List<Path> or Path");
            }
        }

        // create a unique number that tells us if the files changed
        MessageDigest md = MessageDigest.getInstance("SHA-512");
        for (Path p : depsToCompile) {
            update(md, p.toString());
            update(md, Files.getFileAttributeView(p, BasicFileAttributeView.class).readAttributes().lastModifiedTime().toMillis());
        }
        try (Stream<Path> s = Files.find(baseDir.resolve("src"), Integer.MAX_VALUE, (p, bfa) -> bfa.isRegularFile())) {
            s.sorted().forEach(p -> {
                update(md, p.toString());
                try {
                    update(md, Files.getFileAttributeView(p, BasicFileAttributeView.class).readAttributes().lastModifiedTime().toMillis());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }

        // we'll use 7 chars for now, like git
        String hash = toHexHash(md.digest()).substring(0, 7);

        Path buildPath = buildDir.resolve(name + "-" + hash + ".jar");

        if (!Files.exists(buildPath) || Boolean.getBoolean("recompile")) {
            Files.createDirectories(buildDir);
            for (File f : buildDir.toFile().listFiles()) deleteDirectory(f);

            System.out.println("Compiling " + name + "...");

            StandardJavaFileManager fm = COMPILER.getStandardFileManager(null, null, null);

            Path sourcesPath = baseDir.resolve("src").resolve("main").resolve("java");
            Path resourcesDir = baseDir.resolve("src").resolve("main").resolve("resources");

            ArrayList<File> files = new ArrayList<>();
            try (Stream<Path> s = Files.find(sourcesPath, Integer.MAX_VALUE, (p, bfa) -> bfa.isRegularFile())) {
                s.forEach(p -> files.add(p.toFile()));
            }

            Path tmpBuildDir = Files.createTempDirectory("build-" + name); // in /tmp
            // use a try to ensure that it gets deleted again
            try {
                ArrayList<String> args = new ArrayList<>(Arrays.asList("-g", "-d", tmpBuildDir.toString(), "-cp",
                        depsToCompile.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator))));
                compileArgs(args);

                boolean success = COMPILER.getTask(
                        null,
                        null,
                        null,
                        args,
                        null,
                        fm.getJavaFileObjectsFromFiles(files)
                ).call();
                if (!success) throw new RuntimeException("didn't compile: " + name);

                System.out.println("Finished writing classes.");

                // get proper temp file
                Path jarTmpFile = Files.createTempFile("build-" + name + "-", ".jar");
                Files.delete(jarTmpFile);

                // use try to ensure the deletion
                try {
                    URI jarUri = new URI("jar:" + jarTmpFile.toUri());
                    try (
                            FileSystem jarFileSystem = FileSystems.newFileSystem(jarUri, Collections.singletonMap("create", "true"));
                            Stream<Path> classes = Files.walk(tmpBuildDir);
                    ) {
                        // classes
                        classes.forEach(in -> {
                            Path p = tmpBuildDir.relativize(in);

                            if (p.toString().isEmpty()) return;

                            Path out = jarFileSystem.getPath(p.toString()).toAbsolutePath();

                            // copy into the jar
                            try {
                                Files.copy(in, out);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });

                        // resources
                        if (Files.exists(resourcesDir)) {
                            try (
                                    Stream<Path> resources = Files.walk(resourcesDir);
                            ) {
                                resources.forEach(in -> {
                                    Path p = resourcesDir.relativize(in);

                                    if (p.toString().isEmpty()) return;

                                    Path out = jarFileSystem.getPath(p.toString()).toAbsolutePath();

                                    // copy into the jar
                                    try {
                                        Files.copy(in, out);
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                });
                            }
                        }
                    }

                    Files.move(jarTmpFile, buildPath);
                } finally {
                    Files.deleteIfExists(jarTmpFile);
                }
            } finally {
                deleteDirectory(tmpBuildDir.toFile());
            }

            System.out.println("Finished compiling: " + buildPath);
        } else {
            //System.out.println(name + " is up to date");
        }

        List<Path> ret = new ArrayList<>(1 + depsToRun.size());
        ret.add(buildPath);
        ret.addAll(depsToRun);

        return ret;
    }

    public static List<Path> get() {
        // @formatter:off
        String sponge_maven = "https://repo.spongepowered.org/repository/maven-public";
        Supplier<Path> asm                   = lib("https://maven.fabricmc.net",           "org.ow2.asm",          "asm",           "9.3"        );
        Supplier<Path> asm_commons           = lib("https://maven.fabricmc.net",           "org.ow2.asm",          "asm-commons",   "9.3"        );
        Supplier<Path> asm_tree              = lib("https://maven.fabricmc.net",           "org.ow2.asm",          "asm-tree",      "9.3"        );
        Supplier<Path> gson                  = lib("https://repo.maven.apache.org/maven2", "com.google.code.gson", "gson",          "2.9.0"      );
        Supplier<Path> guava                 = lib("https://repo.maven.apache.org/maven2", "com.google.guava",     "guava",         "31.0.1-jre" );
        Supplier<Path> jetbrains_annotations = lib("https://repo.maven.apache.org/maven2", "org.jetbrains",        "annotations",   "23.0.0"     );
        Supplier<Path> mapping_io            = lib("https://maven.fabricmc.net",           "net.fabricmc",         "mapping-io",    "0.3.0"      );
        Supplier<Path> sponge_mixin          = lib(sponge_maven,                           "org.spongepowered",    "mixin",         "0.8.3"      );
        Supplier<Path> tiny_remapper         = lib("https://maven.fabricmc.net",           "net.fabricmc",         "tiny-remapper", "0.8.2"      );
        Supplier<Path> tinylog_api           = lib("https://repo.maven.apache.org/maven2", "org.tinylog",          "tinylog-api",   "2.4.1"      );
        Supplier<Path> tinylog_impl          = lib("https://repo.maven.apache.org/maven2", "org.tinylog",          "tinylog-impl",  "2.4.1"      );
        // @formatter:on


        Supplier<List<Path>> access_widener = mod(
                "access-widener",
                asm
        );
        Supplier<List<Path>> brachyura_mixin_compile_extensions = mod(
                "brachyura-mixin-compile-extensions",
                sponge_mixin, gson, guava, asm, asm_tree
        );
        Supplier<List<Path>> fabricmerge = mod(
                "fabricmerge",
                asm, asm_tree
        );
        Supplier<List<Path>> fernutil = mod(
                "fernutil",
                tinylog_api, tinylog_impl
        );
        Supplier<List<Path>> trieharder = mod(
                "trieharder",
                mapping_io
        );
        Supplier<List<Path>> brachyura = mod(
                "brachyura",
                jetbrains_annotations, gson, mapping_io, tiny_remapper, asm, asm_commons, access_widener,
                brachyura_mixin_compile_extensions, fabricmerge, fernutil, trieharder, tinylog_api, tinylog_impl
        );

        return brachyura.get();
    }
}
