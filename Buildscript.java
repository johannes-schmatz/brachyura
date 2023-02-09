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
        //TODO:
        // basically have this create a jar that contains all the source files, output this as brachyura.jar
        // then we can let brachyura.jar do the stuff about ide modules and stuff
        // this needs brachyura.jar as a dep for the buildscript module, brachyura should link itself as a dep for buildscript
        // what about integrating the buildscript/src/main/java/Buildscript into brachyura, as it's _needed_ there anyways?
        // - this would reduce the needing of a special linking

        Set<Path> dependencies = new LinkedHashSet<>();
        getModule(ROOT.resolve("brachyura"), dependencies);

        List<Path> classpath = new ArrayList<>(dependencies.size());
        classpath.addAll(dependencies);

        URL[] urls = new URL[classpath.size()];
        for (int i = 0; i < classpath.size(); i++) {
            urls[i] = classpath.get(i).toUri().toURL();
        }

        ClassLoader classLoader = new URLClassLoader(urls, ClassLoader.getSystemClassLoader()/*.getParent()*/);


        // setup EntryGlobals
        Class<?> entryGlobals = Class.forName("io.github.coolcrabs.brachyura.project.EntryGlobals", true, classLoader);
        Method m = entryGlobals.getDeclaredMethod("set", Path.class, List.class); // Path, List<Path>
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

    public static final HashSet<Path> CURRENT_COMPILATIONS = new HashSet<>();
    public static Path getModule(Path baseDir, Set<Path> toRun) throws Throwable {
        String name = baseDir.getFileName().toString();
        Path buildDir = BUILD.resolve(name);

        List<Path> dependencies;
        try {
            if (CURRENT_COMPILATIONS.contains(baseDir)) throw new StackOverflowError("Circular dependency detected: " + baseDir);
            CURRENT_COMPILATIONS.add(baseDir);
            dependencies = getDependencies(baseDir, "deps.txt", toRun);
        } finally {
            CURRENT_COMPILATIONS.remove(baseDir);
        }

        // create a unique number that tells us if the files changed
        MessageDigest md = MessageDigest.getInstance("SHA-512");
        for (Path p : dependencies) {
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
                        dependencies.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator))));
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

        if (toRun != null) {
            toRun.addAll(dependencies);
            toRun.add(buildPath);
        }

        return buildPath;
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

    public static List<Path> getDependencies(Path baseDir, String depsFileName, Set<Path> toRun) throws Throwable {
        Path depsFile = baseDir.resolve(depsFileName);

        if (!Files.exists(depsFile)) return Collections.emptyList();

        List<Path> r = new ArrayList<>();
        try (BufferedReader w = Files.newBufferedReader(depsFile)) {
            String line;
            while ((line = w.readLine()) != null) {
                // parse the dependency line
                // https://maven.example.org org.example:foo:0.1.2

                if (line.startsWith("#")) continue;

                if (line.startsWith("./")) {
                    String localModule = line.substring(2);
                    try {
                        Path module = getModule(baseDir.resolve(localModule), toRun);
                        if (toRun != null) toRun.add(module);
                        r.add(module);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                    continue;
                }

                String[] segments = line.split(" ");
                if (segments.length < 2) continue;

                String baseUrl = segments[0].endsWith("/") ? segments[0] : segments[0] + "/";
                String[] mavenId = segments[1].split(":");

                for (String ext : exts) {
                    String fileName = mavenId[1] + "-" + mavenId[2] + ext;
                    Path p = DEPS.resolve(fileName);
                    if (!Files.exists(p)) {
                        Path tmpPath = Files.createTempFile(fileName,null);
                        Files.delete(tmpPath);

                        try {
                            URL url = new URL(baseUrl + mavenId[0].replace('.', '/') + "/" + mavenId[1] + "/" + mavenId[2] + "/" + fileName);

                            System.out.println("Downloading lib: " + url);

                            if ("http".equals(url.getProtocol())) {
                                System.out.println("WARN: You should use https instead of http!");
                            }

                            try (InputStream in = url.openStream()) {
                                Files.copy(in, tmpPath);
                            }
                            Files.move(tmpPath, p);
                        } finally {
                            Files.deleteIfExists(tmpPath);
                        }
                    }
                    if (".jar".equals(ext)) r.add(p);
                }
            }
        }

        return r;
    }

    private static final String[] exts = {".jar", "-sources.jar"};
}
