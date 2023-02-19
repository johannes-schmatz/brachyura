package io.github.coolcrabs.brachyura.bootstrap;

import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

//@SuppressWarnings("all") // Sue me
public class Main {
    static final Path BOOTSTRAP_DIR = Paths.get(System.getProperty("user.home")).resolve(".brachyura").resolve("bootstrap");

    public static final String[] LIBS = {
            "https://repo.maven.apache.org/maven2/org/ow2/asm/asm/9.3/asm-9.3.jar",
            "https://repo.maven.apache.org/maven2/org/ow2/asm/asm/9.3/asm-9.3-sources.jar",
            "https://repo.maven.apache.org/maven2/org/ow2/asm/asm-analysis/9.3/asm-analysis-9.3.jar",
            "https://repo.maven.apache.org/maven2/org/ow2/asm/asm-analysis/9.3/asm-analysis-9.3-sources.jar",
            "https://repo.maven.apache.org/maven2/org/ow2/asm/asm-commons/9.3/asm-commons-9.3.jar",
            "https://repo.maven.apache.org/maven2/org/ow2/asm/asm-commons/9.3/asm-commons-9.3-sources.jar",
            "https://repo.maven.apache.org/maven2/org/ow2/asm/asm-tree/9.3/asm-tree-9.3.jar",
            "https://repo.maven.apache.org/maven2/org/ow2/asm/asm-tree/9.3/asm-tree-9.3-sources.jar",
            "https://repo.maven.apache.org/maven2/org/ow2/asm/asm-util/9.3/asm-util-9.3.jar",
            "https://repo.maven.apache.org/maven2/org/ow2/asm/asm-util/9.3/asm-util-9.3-sources.jar",
            "https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.9.0/gson-2.9.0.jar",
            "https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.9.0/gson-2.9.0-sources.jar",
            "https://repo.maven.apache.org/maven2/org/tinylog/tinylog-api/2.4.1/tinylog-api-2.4.1.jar",
            "https://repo.maven.apache.org/maven2/org/tinylog/tinylog-api/2.4.1/tinylog-api-2.4.1-sources.jar",
            "https://repo.maven.apache.org/maven2/org/tinylog/tinylog-impl/2.4.1/tinylog-impl-2.4.1.jar",
            "https://repo.maven.apache.org/maven2/org/tinylog/tinylog-impl/2.4.1/tinylog-impl-2.4.1-sources.jar",
            "https://maven.fabricmc.net/net/fabricmc/mapping-io/0.3.0/mapping-io-0.3.0.jar",
            "https://maven.fabricmc.net/net/fabricmc/mapping-io/0.3.0/mapping-io-0.3.0-sources.jar",
            "https://maven.fabricmc.net/net/fabricmc/tiny-remapper/0.8.2/tiny-remapper-0.8.2.jar",
            "https://maven.fabricmc.net/net/fabricmc/tiny-remapper/0.8.2/tiny-remapper-0.8.2-sources.jar",
            "https://repo.maven.apache.org/maven2/org/jetbrains/annotations/23.0.0/annotations-23.0.0.jar",
            "https://repo.maven.apache.org/maven2/org/jetbrains/annotations/23.0.0/annotations-23.0.0-sources.jar"
    };

    public static void main(String[] args) throws Throwable {
        // assume the path where the jar is the project source
        // https://stackoverflow.com/a/2837287
        URL thisJar = Main.class.getProtectionDomain().getCodeSource().getLocation();
        Path thisJarPath = Paths.get(thisJar.toURI());

        // use -DprojectDir to overwrite the project dir base, relative to cwd, otherwise it will use the location of the jar
        String projectDir = System.getProperty("projectDir", null);
        Path projectPath = projectDir == null ? thisJarPath.getParent() : Paths.get(projectDir);

        Files.createDirectories(BOOTSTRAP_DIR);
        List<Path> classpathPaths = new ArrayList<>();
        List<URL> classpath = new ArrayList<>();

        for (String lib : LIBS) {
            URL url = new URL(lib);
            String fileName = lib.substring(lib.lastIndexOf('/') + 1);
            boolean isJar = !lib.endsWith("-sources.jar");
            Path download = getDownload(url, fileName);

            if (isJar) {
                classpathPaths.add(download);
                classpath.add(download.toUri().toURL());
            }
        }

        // allows us to load BrachyuraEntry
        classpathPaths.add(thisJarPath);
        classpath.add(thisJar);

        // class loader with the dependencies
        // https://kostenko.org/blog/2019/06/runtime-class-loading.html
        // this getParent() here makes it so that that class loader doesn't know our jar already
        URLClassLoader classLoader = new URLClassLoader(classpath.toArray(new URL[0]), ClassLoader.getSystemClassLoader().getParent()) {
            @Override
            public String toString() {
                return "BrachyuraBootstrapClassLoader@" + Integer.toHexString(hashCode());
            }
        };
        Thread.currentThread().setContextClassLoader(classLoader);

        // setup EntryGlobals
        Class<?> entryGlobals = Class.forName("io.github.coolcrabs.brachyura.project.EntryGlobals", true, classLoader);
        Method m = entryGlobals.getDeclaredMethod("set", Path.class, List.class); // @Nullable Path, @Nullable List<Path>
        m.setAccessible(true);
        m.invoke(null, projectPath, classpathPaths);

        // call the entry
        Class<?> entry = Class.forName("io.github.coolcrabs.brachyura.project.BrachyuraEntry", true, classLoader);
        MethodHandles.publicLookup().findStatic(
                entry,
                "main",
                MethodType.methodType(void.class, String[].class)
        ).invokeExact(args);
    }

    static Path getDownload(URL url, String fileName) throws Throwable {
        switch (url.getProtocol()) {
            case "file": {
                return Paths.get(url.toURI());
            }
            case "https": {
                Path target = BOOTSTRAP_DIR.resolve(fileName);
                if (!Files.isRegularFile(target)) {
                    Path tempFile = Files.createTempFile(BOOTSTRAP_DIR, fileName, ".tmp");
                    try (InputStream is = url.openStream()) {
                        Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
                        Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE);
                    } finally {
                        Files.deleteIfExists(tempFile);
                    }
                }

                return target;
            }
            default:
                throw new RuntimeException("Url doesn't use https or file: " + url + ", can't provide security.");
        }
    }
}
