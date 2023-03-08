package io.github.coolcrabs.brachyura.util;

import org.tinylog.Logger;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

public class PathUtil {
    private PathUtil() { }

    public static final Path HOME = Paths.get(System.getProperty("user.home"));
    public static final Path CWD = Paths.get("").toAbsolutePath();

    public static Path brachyuraPath() {
        return HOME.resolve(".brachyura");
    }

    public static Path cachePath() {
        return brachyuraPath().resolve("cache");
    }

    public static Path resolve(Path parent, String... children) {
        Path p = parent;
        for (String child : children) {
            p = p.resolve(child);
        }
        return p;
    }

    public static Path resolveAndCreateDir(Path parent, String... children) {
        Path result = parent;
        for (String child : children) result = result.resolve(child);
        try {
            Files.createDirectories(result);
            return result;
        } catch (IOException e) {
            throw Util.sneak(e);
        }
    }

    public static void deleteDirectoryChildren(Path directory) {
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw Util.sneak(e);
        }
    }

    public static void copyDir(Path source, Path target) {
        Path a = pathTransform(target.getFileSystem(), source);
        try {
            Files.walkFileTree(source, new SimpleFileVisitor<Path>(){
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path targetFile = target.resolve(a.relativize(pathTransform(target.getFileSystem(), file)));
                    Files.createDirectories(targetFile.getParent());
                    Files.copy(file, targetFile);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw Util.sneak(e);
        }
    }

    // https://stackoverflow.com/a/22611925
    public static Path pathTransform(FileSystem fs, final Path path) {
        Path ret = fs.getPath(path.isAbsolute() ? fs.getSeparator() : "");
        for (Path component : path) {
            ret = ret.resolve(component.getFileName().toString());
        }
        return ret;
    }

    public static InputStream inputStream(Path path) {
        try {
            return new BufferedInputStream(Files.newInputStream(path));
        } catch (IOException e) {
            throw Util.sneak(e);
        }
    }

    public static OutputStream outputStream(Path path) {
        try {
            Files.createDirectories(path.getParent());
            return new BufferedOutputStream(Files.newOutputStream(path));
        } catch (IOException e) {
            throw Util.sneak(e);
        }
    }

    /**
     * Returns a temp file in the same directory as the target file
     */
    public static Path tempFile(Path target) {
        try {
            Files.createDirectories(target.getParent());
            return Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        } catch (IOException e) {
            throw Util.sneak(e);
        }
    }

    public static Path tempDir(Path target) {
        try {
            Files.createDirectories(target.getParent());
            return Files.createTempDirectory(target.getParent(), target.getFileName().toString());
        } catch (IOException e) {
            throw Util.sneak(e);
        }
    }

    public static void moveAtoB(Path a, Path b) {
        try {
            Files.move(a, b, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try {
                Files.delete(b);
            } catch (Exception e2) {
                // File prob wasn't created
            }
            throw Util.sneak(e);
        }
    }

    public static void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw Util.sneak(e);
        }
    }

    public static void deleteDirectory(Path dir) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>(){
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw Util.sneak(e);
        }
    }

    public static void deleteDirectoryIfExists(Path projectDir) {
        if (Files.exists(projectDir)) deleteDirectory(projectDir);
    }

    public static BufferedWriter newBufferedWriter(Path path) {
        try {
            Files.createDirectories(path.getParent());
            return Files.newBufferedWriter(path);
        } catch (IOException e) {
            throw Util.sneak(e);
        }
    }

    public static BufferedWriter newBufferedWriter(Path path, OpenOption... options) {
        try {
            Files.createDirectories(path.getParent());
            return Files.newBufferedWriter(path, options);
        } catch (IOException e) {
            throw Util.sneak(e);
        }
    }

    public static BufferedReader newBufferedReader(Path path) {
        try {
            return Files.newBufferedReader(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw Util.sneak(e);
        }
    }

    public static BufferedWriter newGzipBufferedWriter(Path path) {
        try {
            Files.createDirectories(path.getParent());
            return new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(path)), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw Util.sneak(e);
        }
    }

    public static void createDirectories(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw Util.sneak(e);
        }
    }

    /**
     * Merges multiple jars into one jar.
     * @param outputJar the jar to be deleted and then filled with the content form the other jars
     * @param jars the jars where the content should be taken from
     */
    public static void mergeJars(Path outputJar, Iterable<Path> jars) {
        try {
            Files.deleteIfExists(outputJar);

            try (FileSystem fs = FileSystemUtil.newJarFileSystem(outputJar)) {
                for (Path fromJar : jars) {
                    try (
                            FileSystem fromFs = FileSystemUtil.newJarFileSystem(fromJar);
                            Stream<Path> stream = Files.walk(fromFs.getPath("/"));
                    ) {
                        stream.forEach(from -> {
                            Path to = fs.getPath(from.toString());

                            try {
                                boolean exists = Files.exists(to);
                                boolean isDirectory = Files.isDirectory(from);
                                if (exists) {
                                    if (!isDirectory) {
                                        Logger.warn("Ignoring file: {}", to);
                                    }
                                } else {
                                    Files.copy(from, to);
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void copyFromResources(String resource, Path output) throws Throwable {
        URL resourceUrl = PathUtil.class.getResource(resource);
        Objects.requireNonNull(resourceUrl, "Resource " + resource + " was not found!");
        URI resrouceUri = resourceUrl.toURI();

        try (FileSystem fs = FileSystems.newFileSystem(resrouceUri, Collections.emptyMap())) {
            Path resourcePath = Paths.get(resrouceUri);

            Path directory = output.getParent();
            Files.createDirectories(directory);

            Files.copy(resourcePath, output);
        }
    }

    public static void copyTemplateFromResources(String template, Path projectDir) {
        String resource = "/template" + (template.startsWith("/") ? template : ("/" + template));
        Path output = projectDir.resolve(template);
        try {
            copyFromResources(resource, output);
        } catch (Throwable t){
            Util.sneak(t);
        }
    }

    public static List<Path> filesToPaths(List<File> files) {
        return filesToPaths(files, new ArrayList<>());
    }

    public static <T extends List<Path>> T filesToPaths(List<File> files, T list) {
        for (File file : files) {
            list.add(file.toPath());
        }
        return list;
    }

    public static List<File> pathsToFiles(List<Path> files) {
        return pathsToFiles(files, new ArrayList<>());
    }

    public static <T extends List<File>> T pathsToFiles(List<Path> paths, T list) {
        for (Path path : paths) {
            list.add(path.toFile());
        }
        return list;
    }
}
