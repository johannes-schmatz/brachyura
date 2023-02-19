package io.github.coolcrabs.brachyura.project;

import io.github.coolcrabs.brachyura.util.PathUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class EntryGlobals {
    private static Path projectDir;
    private static List<Path> buildscriptClasspath;

    private EntryGlobals() { }

    // called by bootstrap to set it, null doesn't overwrite the default it gets on loading this class
    private static void set(@Nullable Path projectDir, @Nullable List<Path> buildscriptClasspath) {
        if (projectDir != null) {
            // normalize here, as this makes the path not have /././ in it, which destroys .startsWith comparisons (brachyuras own Buildscript)
            EntryGlobals.projectDir = projectDir.toAbsolutePath().normalize();
        }
        if (buildscriptClasspath != null) {
            EntryGlobals.buildscriptClasspath = buildscriptClasspath.stream()
                    .map(Path::toAbsolutePath)
                    .map(Path::normalize)
                    .collect(Collectors.toList());
        }
        // TODO: maybe check if calling this is even required in some cases, print if the value is changed
    }

    static {
        projectDir = PathUtil.CWD;
        buildscriptClasspath = Arrays.stream(
                System.getProperty("java.class.path")
                .split(File.pathSeparator)
        )
                .map(Paths::get)
                .collect(Collectors.toList());
    }

    public static Path getProjectDir() {
        return projectDir;
    }

    public static List<Path> getBuildscriptClasspath() {
        return buildscriptClasspath;
    }
}
