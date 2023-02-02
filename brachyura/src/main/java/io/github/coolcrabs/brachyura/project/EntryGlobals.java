package io.github.coolcrabs.brachyura.project;

import java.nio.file.Path;
import java.util.List;

class EntryGlobals {
    private EntryGlobals() { }

    public static Path projectDir;
    public static List<Path> buildscriptClasspath;
}
