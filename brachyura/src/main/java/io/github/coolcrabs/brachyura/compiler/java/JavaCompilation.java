package io.github.coolcrabs.brachyura.compiler.java;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import javax.tools.JavaCompiler;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import javax.tools.JavaCompiler.CompilationTask;

import io.github.coolcrabs.brachyura.util.IteratorUtil;
import io.github.coolcrabs.brachyura.util.PathUtil;
import org.tinylog.Logger;

import io.github.coolcrabs.brachyura.processing.ProcessingSource;
import io.github.coolcrabs.brachyura.util.Util;

public class JavaCompilation {
    public final List<String> options = new ArrayList<>();
    public final List<Path> sourceFiles = new ArrayList<>();
    public final List<Path> sourcePath = new ArrayList<>();
    public final List<Path> classpath = new ArrayList<>();
    public final List<ProcessingSource> classpathSources = new ArrayList<>();
    public final List<ProcessingSource> sources = new ArrayList<>();
    private JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

    public JavaCompilation addOption(String... options) {
        Collections.addAll(this.options, options);
        return this;
    }

    public JavaCompilation addSourceFile(Path path) {
        this.sourceFiles.add(path);
        return this;
    }

    public JavaCompilation addSourceDirs(Path... paths) {
        for (Path p : paths) addSourceDir(p);
        return this;
    }

    public JavaCompilation addSourceDir(Path path) {
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.getFileName().toString().endsWith(".java")) {
                        sourceFiles.add(file);
                    } else {
                        Logger.warn("Unrecognized file type for file {} in java src dir", file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw Util.sneak(e);
        }
        return this;
    }

    public JavaCompilation addSourcePathFile(Path path) {
        this.sourcePath.add(path);
        return this;
    }

    public JavaCompilation addSourcePathDir(Path path) {
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.getFileName().toString().endsWith(".java")) {
                        sourcePath.add(file);
                    } else {
                        Logger.warn("Unrecognized file type for file {} in java src path dir", file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw Util.sneak(e);
        }
        return this;
    }

    public JavaCompilation addClasspath(List<Path> paths) {
        classpath.addAll(paths);
        return this;
    }

    public JavaCompilation addClasspath(Path... paths) {
        Collections.addAll(classpath, paths);
        return this;
    }

    /**
     * Should mostly be used for other compilation outputs; jars and directories should use the other methods
     */
    public JavaCompilation addClasspath(ProcessingSource source) {
        classpathSources.add(source);
        return this;
    }

    public JavaCompilation addProcessingSourceSources(ProcessingSource source) {
        sources.add(source);
        return this;
    }

    public JavaCompilation setCompiler(JavaCompiler compiler) {
        this.compiler = compiler;
        return this;
    }

    public JavaCompilationResult compile() throws CompilationFailedException {
        try {
            try (BrachyuraJavaFileManager fileManager = new BrachyuraJavaFileManager()) {

                fileManager.setLocation(StandardLocation.CLASS_PATH, PathUtil.pathsToFiles(classpath));
                fileManager.setLocation(StandardLocation.SOURCE_PATH, PathUtil.pathsToFiles(sourcePath));

                for (ProcessingSource s : classpathSources) {
                    fileManager.extraCp.add(s);
                }
                for (ProcessingSource source : sources) {
                    fileManager.sources.add(source);
                }

                List<String> allWrittenLines = Collections.synchronizedList(new ArrayList<>());
                try (LoggerWriter w = new LoggerWriter(allWrittenLines::add)) {
                    options.add("-Xlint:unchecked");

                    CompilationTask compilationTask = compiler.getTask(
                            w,
                            fileManager,
                            new BrachyuraDiagnosticListener(diagnostic -> allWrittenLines.add(diagnostic.toString())),
                            options,
                            null,
                            IteratorUtil.concat(
                                    fileManager.sources.files.values(),
                                    fileManager.getJavaFileObjectsFromFiles(PathUtil.pathsToFiles(sourceFiles))
                            )
                    );

                    if (compilationTask.call()) {
                        return new JavaCompilationResult(fileManager);
                    } else {
                        throw new CompilationFailedException(allWrittenLines);
                    }
                }
            }
        } catch (IOException e) {
            throw Util.sneak(e);
        }
    }
}
