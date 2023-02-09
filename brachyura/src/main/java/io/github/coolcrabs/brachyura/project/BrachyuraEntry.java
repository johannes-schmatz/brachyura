package io.github.coolcrabs.brachyura.project;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.tinylog.Logger;

import io.github.coolcrabs.brachyura.plugins.Plugin;
import io.github.coolcrabs.brachyura.plugins.Plugins;

public class BrachyuraEntry {
    private BrachyuraEntry() { }

    // Called via reflection by bootstrap
    public static void main(String[] args) {
        int exitcode = 0;
        List<Plugin> plugins = Plugins.getPlugins();
        for (Plugin plugin : plugins) {
            plugin.onEntry();
        }
        try {
            // bootstrap sets EntryGlobals for us
            BuildscriptProject buildscriptProject = new BuildscriptProject();
            if (args.length >= 1 && "buildscript".equals(args[0])) {
                Tasks t = new Tasks();
                buildscriptProject.getTasks(t);
                if (args.length >= 2) {
                    Task task = t.get(args[1]);
                    task.doTask(args.length >= 3 ? Arrays.copyOfRange(args, 2, args.length) : new String[0]);
                } else {
                    Logger.info("Available buildscript tasks: {}", t);
                }
            } else {
                Project project = buildscriptProject.project.get();
                if (project != null) {
                    project.setIdeProject(buildscriptProject);
                    Tasks t = new Tasks();
                    project.getTasks(t);
                    if (args.length >= 1) {
                        Task task = t.get(args[0]);
                        task.doTask(args.length >= 2 ? Arrays.copyOfRange(args, 1, args.length) : new String[0]);
                    } else {
                        Logger.info("Available tasks: {}", t);
                    }
                } else {
                    Logger.warn("Invalid build script :(");
                    exitcode = 1;
                }
            }
        } catch (Exception e) {
            Logger.error("Task Failed");
            Logger.error(e);
            exitcode = 1;
        }
        for (Plugin plugin : plugins) {
            plugin.onExit();
        }
        System.exit(exitcode);
    }
}
