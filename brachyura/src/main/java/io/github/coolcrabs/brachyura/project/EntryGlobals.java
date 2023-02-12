package io.github.coolcrabs.brachyura.project;

import java.nio.file.Path;
import java.util.List;

public class EntryGlobals {
    private static Path projectDir;
    private static List<Path> buildscriptClasspath;

    private EntryGlobals() { }

    // called by bootstrap to set it
    private static void set(Path projectDir, List<Path> buildscriptClasspath) {
        // normalize here, as this makes the path not have /././ in it, which destroys .startsWith comparisons (brachyuras own Buildscript)
        EntryGlobals.projectDir = projectDir.normalize();
        EntryGlobals.buildscriptClasspath = buildscriptClasspath;
    }

    public static Path getProjectDir() {
        return projectDir;
    }

    public static List<Path> getBuildscriptClasspath() {
        return buildscriptClasspath;
    }
}
