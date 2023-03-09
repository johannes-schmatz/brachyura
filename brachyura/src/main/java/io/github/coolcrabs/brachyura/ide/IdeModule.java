package io.github.coolcrabs.brachyura.ide;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import io.github.coolcrabs.brachyura.dependency.JavaJarDependency;
import io.github.coolcrabs.brachyura.util.Lazy;

public class IdeModule {
    public final String name;
    public final Path root;
    public final Lazy<List<JavaJarDependency>> dependencies;
    public final List<IdeModule> dependencyModules;
    public final List<RunConfig> runConfigs;
    public final List<Path> sourcePaths;
    public final List<Path> resourcePaths;
    public final List<Path> testSourcePaths;
    public final List<Path> testResourcePaths;
    public final int javaVersion;

    public IdeModule(String name, Path root, Supplier<List<JavaJarDependency>> dependencies, List<IdeModule> dependencyModules,
            List<RunConfig> runConfigs, List<Path> sourcePaths, List<Path> resourcePaths, List<Path> testSourcePaths, List<Path> testResourcePaths,
            int javaVersion) {
        this.name = Objects.requireNonNull(name, "IdeModule missing name");
        this.root = Objects.requireNonNull(root, "IdeModule missing root");
        this.dependencies = Lazy.of(dependencies);
        this.dependencyModules = Objects.requireNonNull(dependencyModules);
        this.runConfigs = Objects.requireNonNull(runConfigs);
        this.sourcePaths = Objects.requireNonNull(sourcePaths);
        this.resourcePaths = Objects.requireNonNull(resourcePaths);
        this.testSourcePaths = Objects.requireNonNull(testSourcePaths);
        this.testResourcePaths = Objects.requireNonNull(testResourcePaths);
        this.javaVersion = javaVersion;
    }

    @Override
    public String toString() {
        return "IdeModule{" +
                "name='" + name + '\'' +
                ", root=" + root +
                ", dependencies=" + dependencies +
                ", dependencyModules=" + dependencyModules +
                ", runConfigs=" + runConfigs +
                ", sourcePaths=" + sourcePaths +
                ", resourcePaths=" + resourcePaths +
                ", testSourcePaths=" + testSourcePaths +
                ", testResourcePaths=" + testResourcePaths +
                ", javaVersion=" + javaVersion +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdeModule ideModule = (IdeModule) o;
        return javaVersion == ideModule.javaVersion && name.equals(ideModule.name) && root.equals(ideModule.root) &&
                dependencies.equals(ideModule.dependencies) &&
                dependencyModules.equals(ideModule.dependencyModules) && runConfigs.equals(ideModule.runConfigs) && sourcePaths.equals(ideModule.sourcePaths) &&
                resourcePaths.equals(ideModule.resourcePaths) && testSourcePaths.equals(ideModule.testSourcePaths) &&
                testResourcePaths.equals(ideModule.testResourcePaths);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                name,
                root,
                dependencies,
                dependencyModules,
                runConfigs,
                sourcePaths,
                resourcePaths,
                testSourcePaths,
                testResourcePaths,
                javaVersion
        );
    }

    public static class IdeModuleBuilder {
        private String name;
        private Path root;
        private Supplier<List<JavaJarDependency>> dependencies = Collections::emptyList;
        // TODO: more new ArrayList<>() here
        private List<IdeModule> dependencyModules = Collections.emptyList();
        private List<RunConfigBuilder> runConfigs = Collections.emptyList();
        private final List<Path> sourcePaths = new ArrayList<>();
        private final List<Path> resourcePaths = new ArrayList<>();
        private List<Path> testSourcePaths = Collections.emptyList();
        private List<Path> testResourcePaths = Collections.emptyList();
        private int javaVersion = 8;
        
        public IdeModuleBuilder name(String name) {
            this.name = name;
            return this;
        }

        public IdeModuleBuilder root(Path root) {
            this.root = root;
            return this;
        }

        public IdeModuleBuilder dependencies(Supplier<List<JavaJarDependency>> dependencies) {
            this.dependencies = dependencies;
            return this;
        }
        
        public IdeModuleBuilder dependencies(List<JavaJarDependency> dependencies) {
            this.dependencies = () -> dependencies;
            return this;
        }

        public IdeModuleBuilder dependencies(JavaJarDependency... dependencies) {
            this.dependencies = () -> Arrays.asList(dependencies);
            return this;
        }

        public IdeModuleBuilder dependencyModules(List<IdeModule> dependencyModules) {
            this.dependencyModules = dependencyModules;
            return this;
        }

        public IdeModuleBuilder dependencyModules(IdeModule... dependencyModules) {
            this.dependencyModules = Arrays.asList(dependencyModules);
            return this;
        }

        public IdeModuleBuilder runConfigs(List<RunConfigBuilder> runConfigs) {
            this.runConfigs = runConfigs;
            return this;
        }

        public IdeModuleBuilder runConfigs(RunConfigBuilder... runConfigs) {
            this.runConfigs = Arrays.asList(runConfigs);
            return this;
        }

        public IdeModuleBuilder sourcePaths(List<Path> sourcePaths) {
            this.sourcePaths.addAll(sourcePaths);
            return this;
        }

        public IdeModuleBuilder sourcePaths(Path... sourcePaths) {
            this.sourcePaths.addAll(Arrays.asList(sourcePaths));
            return this; 
        }

        public IdeModuleBuilder sourcePath(Path sourcePath) {
            this.sourcePaths.add(sourcePath);
            return this;
        }

        public IdeModuleBuilder resourcePaths(List<Path> resourcePaths) {
            this.resourcePaths.addAll(resourcePaths);
            return this;
        }

        public IdeModuleBuilder resourcePaths(Path... resourcePaths) {
            this.resourcePaths.addAll(Arrays.asList(resourcePaths));
            return this;
        }

        public IdeModuleBuilder testSourcePaths(Path... testSourcePaths) {
            this.testSourcePaths = Arrays.asList(testSourcePaths);
            return this; 
        }

        public IdeModuleBuilder testSourcePath(Path testSourcePath) {
            this.testSourcePaths = new ArrayList<>();
            testSourcePaths.add(testSourcePath);
            return this;
        }

        public IdeModuleBuilder testResourcePaths(List<Path> testResourcePaths) {
            this.testResourcePaths = testResourcePaths;
            return this;
        }

        public IdeModuleBuilder testResourcePaths(Path... testResourcePaths) {
            this.testResourcePaths = Arrays.asList(testResourcePaths);
            return this;
        }

        public IdeModuleBuilder testResourcePath(Path testResourcePath) {
            this.testResourcePaths = new ArrayList<>();
            testResourcePaths.add(testResourcePath);
            return this;
        }
        
        public IdeModuleBuilder javaVersion(int javaVersion) {
            this.javaVersion = javaVersion;
            return this;
        }

        public IdeModule build() {
            List<RunConfig> runConfigs = new ArrayList<>(this.runConfigs.size());
            for (RunConfigBuilder b : this.runConfigs) {
                runConfigs.add(b.build());
            }
            return new IdeModule(name, root, dependencies, dependencyModules, runConfigs, sourcePaths, resourcePaths, testSourcePaths, testResourcePaths, javaVersion);
        }
    }

    public static class RunConfig {
        public final String name;
        public final String mainClass;
        public final Path cwd; // Make sure this exists
        public final Lazy<List<String>> vmArgs;
        public final Lazy<List<String>> args;
        public final Lazy<List<Path>> classpath;
        public final List<IdeModule> additionalModulesClasspath;
        public final List<Path> resourcePaths;

        public RunConfig(String name, String mainClass, Path cwd, Supplier<List<String>> vmArgs, Supplier<List<String>> args, Supplier<List<Path>> classpath,
                List<IdeModule> additionalModulesClasspath, List<Path> resourcePaths) {
            this.name = Objects.requireNonNull(name, "Null name");
            this.mainClass = Objects.requireNonNull(mainClass, "Null mainClass");
            this.cwd = Objects.requireNonNull(cwd, "Null cwd");
            this.vmArgs = Lazy.of(vmArgs);
            this.args = Lazy.of(args);
            this.classpath = Lazy.of(classpath);
            this.additionalModulesClasspath = additionalModulesClasspath;
            this.resourcePaths = resourcePaths;
        }
    }

    public static class RunConfigBuilder {
        private String name;
        private String mainClass;
        private Path cwd;
        private Supplier<List<String>> vmArgs = Collections::emptyList;
        private Supplier<List<String>> args = Collections::emptyList;
        private Supplier<List<Path>> classpath = Collections::emptyList;
        private List<IdeModule> additionalModulesClasspath = Collections.emptyList();
        private List<Path> resourcePaths = Collections.emptyList();

        public RunConfigBuilder name(String name) {
            this.name = name;
            return this;
        }

        public RunConfigBuilder mainClass(String mainClass) {
            this.mainClass = mainClass;
            return this;
        }

        public RunConfigBuilder cwd(Path cwd) {
            this.cwd = cwd;
            return this;
        }

        public RunConfigBuilder vmArgs(Supplier<List<String>> vmArgs) {
            this.vmArgs = vmArgs;
            return this;
        }

        public RunConfigBuilder vmArgs(List<String> vmArgs) {
            this.vmArgs = () -> vmArgs;
            return this;
        }

        public RunConfigBuilder vmArgs(String... vmArgs) {
            this.vmArgs = () -> Arrays.asList(vmArgs);
            return this;
        }

        public RunConfigBuilder args(Supplier<List<String>> args) {
            this.args = args;
            return this;
        }

        public RunConfigBuilder args(List<String> args) {
            this.args = () -> args;
            return this;
        }

        public RunConfigBuilder args(String... args) {
            this.args = () -> Arrays.asList(args);
            return this;
        }

        public RunConfigBuilder classpath(Supplier<List<Path>> classpath) {
            this.classpath = classpath;
            return this;
        }

        public RunConfigBuilder classpath(List<Path> classpath) {
            this.classpath = () -> classpath;
            return this;
        }

        public RunConfigBuilder classpath(Path... classpath) {
            this.classpath = () -> Arrays.asList(classpath);
            return this;
        }

        public RunConfigBuilder additionalModulesClasspath(List<IdeModule> additionalModulesClasspath) {
            this.additionalModulesClasspath = additionalModulesClasspath;
            return this;
        }
        
        public RunConfigBuilder additionalModulesClasspath(IdeModule... additionalModulesClasspath) {
            this.additionalModulesClasspath = Arrays.asList(additionalModulesClasspath);
            return this;
        }
        
        public RunConfigBuilder resourcePaths(List<Path> resourcePaths) {
            this.resourcePaths = resourcePaths;
            return this;
        }

        public RunConfigBuilder resourcePaths(Path... resourcePaths) {
            this.resourcePaths = Arrays.asList(resourcePaths);
            return this;
        }

        public RunConfig build() {
            return new RunConfig(name, mainClass, cwd, vmArgs, args, classpath, additionalModulesClasspath, resourcePaths);
        }
    }
}
