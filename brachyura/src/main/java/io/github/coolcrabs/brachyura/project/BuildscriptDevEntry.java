package io.github.coolcrabs.brachyura.project;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import io.github.coolcrabs.brachyura.plugins.Plugin;
import io.github.coolcrabs.brachyura.plugins.Plugins;

class BuildscriptDevEntry {
    public static void main(String[] args) throws Throwable {
        List<Plugin> plugins = Plugins.getPlugins();
        for (Plugin plugin : plugins) {
            plugin.onEntry();
        }
        try {
            {
                Path projectDir = Paths.get(args[0]);
                List<Path> buildscriptClasspath = Arrays.stream(args[1].split(File.pathSeparator)).map(Paths::get).collect(Collectors.toList());

                // setup EntryGlobals
                Method m = EntryGlobals.class.getDeclaredMethod("set", Path.class, List.class); // @Nullable Path, @Nullable List<Path>
                m.setAccessible(true);
                m.invoke(null, projectDir, buildscriptClasspath);

                // maybe make EntryGlobals use System.getProperty instead by default (to remove this code)
                System.out.println("classpath as of system.get: " + System.getProperty("java.class.path"));
                System.out.println("classpath as of arguments: " + buildscriptClasspath);


            }

            Project buildscript = (Project) Class.forName("Buildscript").getDeclaredConstructor().newInstance();
            BuildscriptProject buildscriptProject = new BuildscriptProject() {
                @Override
                public Project createProject() {
                    return buildscript;
                }
            };
            buildscript.setIdeProject(buildscriptProject);
            Tasks t = new Tasks();
            buildscript.getTasks(t);
            Task task = t.get(args[2]);
            task.doTask(new String[0]);
        } finally {
            for (Plugin plugin : plugins) {
                plugin.onExit();
            }
        }
    }
}
