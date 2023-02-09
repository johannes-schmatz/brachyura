package io.github.coolcrabs.brachyura.project;

import io.github.coolcrabs.brachyura.compiler.java.CompilationFailedException;
import io.github.coolcrabs.brachyura.compiler.java.JavaCompilation;
import io.github.coolcrabs.brachyura.compiler.java.JavaCompilationResult;
import io.github.coolcrabs.brachyura.dependency.JavaJarDependency;
import io.github.coolcrabs.brachyura.ide.IdeModule;
import io.github.coolcrabs.brachyura.ide.IdeModule.RunConfigBuilder;
import io.github.coolcrabs.brachyura.processing.ProcessingId;
import io.github.coolcrabs.brachyura.processing.ProcessingSink;
import io.github.coolcrabs.brachyura.project.java.BaseJavaProject;
import io.github.coolcrabs.brachyura.util.*;
import org.jetbrains.annotations.Nullable;
import org.tinylog.Logger;

import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class BuildscriptProject extends BaseJavaProject {
    @Override
    public Path getProjectDir() {
        return super.getProjectDir().resolve("buildscript");
    }

    @Override
    public void getRunConfigTasks(Consumer<Task> p) {
        //noop
    }

    @Override
    public IdeModule[] getIdeModules() {
        Tasks t = new Tasks();
        Project p = project.get();
        if (p != null) p.getTasks(t);
        ArrayList<RunConfigBuilder> runConfigs = new ArrayList<>(t.t.size());
        Path cwd = getProjectDir().resolve("run");
        PathUtil.createDirectories(cwd);
        for (Map.Entry<String, Task> e : t.t.entrySet()) {
            runConfigs.add(
                new RunConfigBuilder()
                    .name(e.getKey())
                    .cwd(cwd)
                    .mainClass("io.github.coolcrabs.brachyura.project.BuildscriptDevEntry")
                    .classpath(getCompileDependencies())
                    .args(
                        () -> Arrays.asList(
                            super.getProjectDir().toString(),
                            getCompileDependencies().stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)),
                            e.getKey()
                        )
                    )
            );
        }
        IdeModule.IdeModuleBuilder b = new IdeModule.IdeModuleBuilder()
                .name("Buildscript")
                .root(getProjectDir())
                .sourcePath(getSrcDir());
        if (p instanceof BrachyuraBuildscript) {
            b.dependencyModules(((BrachyuraBuildscript) p).getBrachyuraIdeModule());
        } else {
            b.dependencies(this::getIdeDependencies);
        }
        b.runConfigs(runConfigs);

        return new IdeModule[] {b.build()};
    }

    public final Lazy<Project> project = new Lazy<>(this::createProject);
    @Nullable
    public Project createProject() {
        try {
            ClassLoader b = getBuildscriptClassLoader();
            if (b == null) return null;
            Class<?> projectclass = Class.forName("Buildscript", true, b);
            if (Project.class.isAssignableFrom(projectclass)) {
                return (Project) projectclass.getDeclaredConstructor().newInstance();
            } else {
                Logger.warn("Buildscript must be instance of Project");
                return null;
            }
        } catch (Exception e) {
            Logger.warn("Error getting project:");
            Logger.warn(e);
            return null;
        }
    }

    @Nullable
    public ClassLoader getBuildscriptClassLoader() {
        try {
            JavaCompilationResult compilation = new JavaCompilation()
                .addSourceDir(getSrcDir())
                .addClasspath(getCompileDependencies())
                .addOption(JvmUtil.compileArgs(JvmUtil.CURRENT_JAVA_VERSION, 8))
                .compile();
            BuildscriptClassloader r = new BuildscriptClassloader(BuildscriptProject.class.getClassLoader(), getSrcDir());
            compilation.getInputs(r);
            return r;
        } catch (CompilationFailedException e) {
            Logger.warn("Buildscript compilation failed!");
            return null;
        }
    }

    public List<JavaJarDependency> getIdeDependencies() {
        List<Path> compileDeps = getCompileDependencies();
        List<JavaJarDependency> result = new ArrayList<>(compileDeps.size());
        for (Path p : compileDeps) {
            Path source = p.getParent().resolve(p.getFileName().toString().replace(".jar", "-sources.jar"));
            if (!Files.exists(source)) source = null;
            result.add(new JavaJarDependency(p, source, null));
        }
        return result;
    }

    public List<Path> getCompileDependencies() {
        return EntryGlobals.getBuildscriptClasspath();
    }

    static class BuildscriptClassloader extends ClassLoader implements ProcessingSink {
        public final ProtectionDomain defaultProtectionDomain;
        public final HashMap<String, byte[]> classes = new HashMap<>();

        BuildscriptClassloader(ClassLoader parent, Path srcDir) {
            super(parent);
            try {
                CodeSource cs = new CodeSource(srcDir.toUri().toURL(), (Certificate[]) null);
                defaultProtectionDomain = new ProtectionDomain(cs, null, this, null);
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void sink(Supplier<InputStream> in, ProcessingId id) {
            try {
                if (id.path.endsWith(".class")) {
                    try (InputStream i = in.get()) {
                        String dottedName = id.path
                                .substring(0, id.path.length() - 6)
                                .replace("/", ".");
                        classes.put(dottedName, StreamUtil.readFullyAsBytes(i));
                    }
                } else {
                    Logger.warn("Cannot define resource {}", id.path);
                }
            } catch (Exception e) {
                throw Util.sneak(e);
            }
        }

        @Nullable
        protected Class<?> findClassFromUs(String name) {
            // check if it's a class that we know
            byte[] data = classes.get(name);
            if (data == null) return null;
            return defineClass(name, data, 0, data.length, defaultProtectionDomain);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);

                // without this different order, it would fail if you run
                // java Buildscript build
                // as the parent loader would already know the Buildscript class
                // (this case only happens for building brachyura itself)
                if (c == null) {
                    // try out our classloader first
                    c = findClassFromUs(name);

                    // then load from the parent
                    if (c == null && getParent() != null) {
                        c = getParent().loadClass(name);
                    }
                }
                if (resolve) {
                    resolveClass(c);
                }
                return c;
            }
        }

        @Override
        public String toString() {
            return "BuildscriptClassloader@".concat(super.toString());
        }
    }
}
