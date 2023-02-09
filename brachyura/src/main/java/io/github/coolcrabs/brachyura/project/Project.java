package io.github.coolcrabs.brachyura.project;

import java.nio.file.Path;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import io.github.coolcrabs.brachyura.project.java.BaseJavaProject;
import io.github.coolcrabs.brachyura.util.PathUtil;

public class Project {
    public BaseJavaProject buildscriptIdeProject;

    public void getTasks(Consumer<Task> p) {
        // no default tasks
    }

    public final void runTask(String name, String... args) {
        Tasks t = new Tasks();
        getTasks(t);
        t.get(name).doTask(args);
    }

    public Path getProjectDir() {
        return EntryGlobals.getProjectDir();
    }

    public Path getLocalBrachyuraPath() {
        return PathUtil.resolveAndCreateDir(getProjectDir(), ".brachyura");
    }

    public @Nullable BaseJavaProject getBuildscriptProject() {
        return buildscriptIdeProject;
    }

    public void setIdeProject(BaseJavaProject ideProject) {
        this.buildscriptIdeProject = ideProject;
    }
}
