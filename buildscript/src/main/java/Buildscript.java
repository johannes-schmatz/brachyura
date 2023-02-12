import java.io.BufferedReader;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.tools.StandardLocation;

import io.github.coolcrabs.brachyura.compiler.java.JavaCompilation;
import io.github.coolcrabs.brachyura.compiler.java.JavaCompilationResult;
import io.github.coolcrabs.brachyura.dependency.JavaJarDependency;
import io.github.coolcrabs.brachyura.ide.IdeModule;
import io.github.coolcrabs.brachyura.maven.Maven;
import io.github.coolcrabs.brachyura.maven.MavenId;
import io.github.coolcrabs.brachyura.processing.ProcessingSource;
import io.github.coolcrabs.brachyura.processing.ProcessorChain;
import io.github.coolcrabs.brachyura.processing.sinks.AtomicZipProcessingSink;
import io.github.coolcrabs.brachyura.processing.sources.DirectoryProcessingSource;
import io.github.coolcrabs.brachyura.project.Task;
import io.github.coolcrabs.brachyura.project.BrachyuraBuildscript;
import io.github.coolcrabs.brachyura.project.java.BaseJavaProject;
import io.github.coolcrabs.brachyura.project.java.BuildModule;
import io.github.coolcrabs.brachyura.project.java.SimpleJavaModule;
import io.github.coolcrabs.brachyura.test.basicbuildscript.MavenRef;
import io.github.coolcrabs.brachyura.util.*;

/*
 * mostly taken from
 * https://github.com/CoolCrabs/brachyura/blob/ea8cb5cf49353d86238a6adaedab05ddd60a66d9/buildscript/src/main/java/Buildscript.java
 */
public class Buildscript extends BaseJavaProject implements BrachyuraBuildscript {

	public static void main(String[] args) {

	}

	// TODO: make this read the deps.txt files from all over the project
	static final String GROUP = "io.github.coolcrabs";

	// https://junit.org/junit5/docs/current/user-guide/#running-tests-console-launcher
	static final Lazy<List<JavaJarDependency>> junit = new Lazy<>(Buildscript::createJunit);
	static List<JavaJarDependency> createJunit() {
		String jupiterVersion = "5.9.0";
		String platformVersion = "1.9.0";
		ArrayList<JavaJarDependency> r = new ArrayList<>();
		r.add(Maven.getMavenJarDep(Maven.MAVEN_CENTRAL, new MavenId("org.apiguardian:apiguardian-api:1.1.2")));
		r.add(Maven.getMavenJarDep(Maven.MAVEN_CENTRAL, new MavenId("org.opentest4j:opentest4j:1.2.0")));
		for (String jup : new String[]{"api", "engine", "params"}) {
			r.add(Maven.getMavenJarDep(Maven.MAVEN_CENTRAL, new MavenId("org.junit.jupiter", "junit-jupiter-" + jup, jupiterVersion)));
		}
		for (String plat : new String[]{"commons", "console", "engine", "launcher"}) {
			r.add(Maven.getMavenJarDep(Maven.MAVEN_CENTRAL, new MavenId("org.junit.platform", "junit-platform-" + plat, platformVersion)));
		}
		return r;
	}

	static class BJarResult {
		JavaJarDependency main;
		JavaJarDependency tests;
	}

	public final Lazy<List<JavaJarDependency>> bdeps = new Lazy<>(this::createBdeps);
	protected List<JavaJarDependency> createBdeps() {
		MavenRef[] deps = {
				new MavenRef("https://repo.maven.apache.org/maven2", "org.jetbrains", "annotations", "23.0.0"),
				new MavenRef("https://repo.maven.apache.org/maven2", "com.google.code.gson", "gson", "2.9.0"),
				new MavenRef("https://repo.maven.apache.org/maven2", "org.tinylog", "tinylog-api", "2.4.1"),
				new MavenRef("https://repo.maven.apache.org/maven2", "org.tinylog", "tinylog-impl", "2.4.1"),

				new MavenRef("https://maven.fabricmc.net", "net.fabricmc", "mapping-io", "0.3.0"),
				new MavenRef("https://maven.fabricmc.net", "net.fabricmc", "tiny-remapper", "0.8.2"),

				new MavenRef("https://maven.fabricmc.net", "org.ow2.asm", "asm", "9.3"),
				new MavenRef("https://maven.fabricmc.net", "org.ow2.asm", "asm-commons", "9.3")
		};

		List<JavaJarDependency> r = new ArrayList<>(deps.length);
		for (MavenRef i : deps) {
			r.add(Maven.getMavenJarDep(i));
		}
		return r;
	}

	abstract class BJavaModule extends SimpleJavaModule {
		public boolean packageLibsIntoIt() {
			return false;
		}

		public boolean hasTests() {
			return true;
		}

		abstract MavenId getId();

		@Override
		public Path[] getSrcDirs() {
			return new Path[]{getModuleRoot().resolve("src").resolve("main").resolve("java")};
		}

		@Override
		public Path[] getResourceDirs() {
			Path res = getModuleRoot().resolve("src").resolve("main").resolve("resources");
			if (Files.exists(res)) {
				return new Path[]{res};
			} else {
				return new Path[0];
			}
		}

		@Override
		public String getModuleName() {
			return getId().artifactId;
		}

		@Override
		public Path getModuleRoot() {
			return Buildscript.this.getProjectDir().resolve(getModuleName());
		}

		@Override
		public IdeModule createIdeModule() {
			IdeModule.IdeModuleBuilder r =  new IdeModule.IdeModuleBuilder()
					.name(getModuleName())
					.root(getModuleRoot())
					.javaVersion(getJavaVersion())
					.sourcePaths(getSrcDirs())
					.resourcePaths(getResourceDirs())
					.dependencies(dependencies.get())
					.dependencyModules(getModuleDependencies().stream().map(m -> m.ideModule.get()).collect(Collectors.toList()));
			if (hasTests()) {
				r.testSourcePath(getModuleRoot().resolve("src").resolve("test").resolve("java"));
				Path testRes = getModuleRoot().resolve("src").resolve("test").resolve("resources");
				if (Files.exists(testRes)) r.testResourcePath(testRes);
			}
			return r.build();
		}

		@Override
		protected JavaCompilation createCompilation() {
			JavaCompilation r = new JavaCompilation()
					.addSourceDir(getSrcDirs())
					.addClasspath(getCompileDependencies())
					.addOption(JvmUtil.compileArgs(JvmUtil.CURRENT_JAVA_VERSION, getJavaVersion()));
			if (hasTests()) {
				r.addSourceDir(getModuleRoot().resolve("src").resolve("test").resolve("java"));
			}
			for (ProcessingSource s : Lazy.getParallel(getModuleDependencies().stream().map(m -> m.compilationOutput).collect(Collectors.toList()))) {
				r.addClasspath(s);
			}
			return r;
		}

		public String getJarBaseName() {
			return getId().artifactId + "-" + getId().version;
		}

		public final Lazy<BJarResult> built = new Lazy<>(this::build);
		protected BJarResult build() {
			Path buildLibsDir = PathUtil.resolveAndCreateDir(PathUtil.resolveAndCreateDir(getModuleRoot(), "build"), "libs");
			Path testSrc = getModuleRoot().resolve("src").resolve("test").resolve("java");
			Path outjar = buildLibsDir.resolve(getJarBaseName() + ".jar");
			Path outjarsources = buildLibsDir.resolve(getJarBaseName() + "-sources.jar");
			BJarResult r = new BJarResult();
			if (hasTests()) {
				Path testoutjar = buildLibsDir.resolve(getJarBaseName() + "-test.jar");
				Path testoutjarsources = buildLibsDir.resolve(getJarBaseName() + "-test-sources.jar");
				try (
						AtomicZipProcessingSink jarSink = new AtomicZipProcessingSink(outjar);
						AtomicZipProcessingSink jarSourcesSink = new AtomicZipProcessingSink(outjarsources);
						AtomicZipProcessingSink testJarSink = new AtomicZipProcessingSink(testoutjar);
						AtomicZipProcessingSink testJarSourcesSink = new AtomicZipProcessingSink(testoutjarsources);
				) {
					new ProcessorChain().apply(jarSink, Arrays.stream(getResourceDirs()).map(DirectoryProcessingSource::new).collect(Collectors.toList()));
					Path testRes = getModuleRoot().resolve("src").resolve("test").resolve("resources");
					if (Files.exists(testRes)) new ProcessorChain().apply(testJarSink, new DirectoryProcessingSource(testRes));
					JavaCompilationResult comp = compilationResult.get();
					comp.getInputs((in, id) -> {
						if (comp.getSourceFile(id).startsWith(testSrc)) {
							testJarSink.sink(in, id);
						} else {
							jarSink.sink(in, id);
						}
					});
					for (Path p : getSrcDirs()) {
						new DirectoryProcessingSource(p).getInputs(jarSourcesSink);
					}
					new DirectoryProcessingSource(testSrc).getInputs(testJarSourcesSink);
					comp.getOutputLocation(StandardLocation.SOURCE_OUTPUT, jarSourcesSink);
					jarSink.commit();
					jarSourcesSink.commit();
					testJarSink.commit();
					testJarSourcesSink.commit();
					r.tests = new JavaJarDependency(testoutjar, testoutjarsources, null);
				}
			} else {
				try (
						AtomicZipProcessingSink jarSink = new AtomicZipProcessingSink(outjar);
						AtomicZipProcessingSink jarSourcesSink = new AtomicZipProcessingSink(outjarsources);
				) {
					new ProcessorChain().apply(jarSink, Arrays.stream(getResourceDirs()).map(DirectoryProcessingSource::new).collect(Collectors.toList()));
					JavaCompilationResult comp = compilationResult.get();
					comp.getInputs(jarSink::sink);
					for (Path p : getSrcDirs()) {
						new DirectoryProcessingSource(p).getInputs(jarSourcesSink);
					}
					comp.getOutputLocation(StandardLocation.SOURCE_OUTPUT, jarSourcesSink);
					jarSink.commit();
					jarSourcesSink.commit();
				}
			}
			r.main = new JavaJarDependency(outjar, outjarsources, getId());
			return r;
		}

		public final void test() {
			if (!hasTests()) return;
			//Logger.info("Testing {}", getModuleName());
			System.out.println("Testing " +  getModuleName());
			try {
				ArrayList<Path> cp = new ArrayList<>();
				for (JavaJarDependency jdep : dependencies.get()) cp.add(jdep.jar);
				cp.add(built.get().main.jar);
				cp.add(built.get().tests.jar);
				for (BuildModule bm : getModuleDependencies()) {
					cp.add(((BJavaModule)bm).built.get().main.jar);
					JavaJarDependency tests = ((BJavaModule)bm).built.get().tests;
					if (tests != null) cp.add(tests.jar);
				}
				ProcessBuilder pb = new ProcessBuilder(
						JvmUtil.CURRENT_JAVA_EXECUTABLE,
						"-cp",
						cp.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)),
						"org.junit.platform.console.ConsoleLauncher",
						"--scan-classpath",
						built.get().tests.jar.toString()
				);
				pb.inheritIO();
				Process p = pb.start();
				int exitCode = p.waitFor();
				if (exitCode != 0) {
					throw new RuntimeException("Tests failed with exit code " + exitCode);
				}
			} catch (Exception e) {
				throw Util.sneak(e);
			}
		}
	}

	public final BJavaModule brachyura = new BJavaModule() {
		@Override
		public boolean packageLibsIntoIt() {
			return true;
		}
		@Override
		MavenId getId() {
			return new MavenId(GROUP, "brachyura", "0.95");
		}

		@Override
		protected List<JavaJarDependency> createDependencies() {
			ArrayList<JavaJarDependency> deps = new ArrayList<>();
			deps.addAll(bdeps.get());
			deps.addAll(junit.get());
			return deps;
		}

		@Override
		protected List<BuildModule> getModuleDependencies() {
			return Arrays.asList(trieharder, fernutil, fabricmerge, mixinCompileExtensions, accessWidener);
		}
	};
	@Override
	public IdeModule getBrachyuraIdeModule() {
		return brachyura.ideModule.get();
	}

	Lazy<JavaJarDependency> mappingIo = new Lazy<>(() -> Maven.getMavenJarDep("https://maven.fabricmc.net/", new MavenId("net.fabricmc", "mapping-io", "0.3.0")));

	public final BJavaModule trieharder = new BJavaModule() {
		@Override
		MavenId getId() {
			return new MavenId(GROUP, "trieharder", "0.2.0");
		}

		@Override
		protected List<JavaJarDependency> createDependencies() {
			ArrayList<JavaJarDependency> deps = new ArrayList<>();
			deps.add(mappingIo.get());
			deps.addAll(junit.get());
			return deps;
		}
	};

	public final Lazy<JavaJarDependency> fastutil = new Lazy<>(() -> Maven.getMavenJarDep(Maven.MAVEN_CENTRAL, new MavenId("it.unimi.dsi", "fastutil", "8.5.8")));

	public final BJavaModule fernutil = new BJavaModule() {
		@Override
		public boolean hasTests() {
			return false;
		}

		@Override
		MavenId getId() {
			return new MavenId(GROUP, "fernutil", "0.2");
		}

		@Override
		protected List<JavaJarDependency> createDependencies() {
			ArrayList<JavaJarDependency> deps = new ArrayList<>();
			deps.addAll(junit.get());
			for (JavaJarDependency jjdep : bdeps.get()) {
				if ("tinylog-api".equals(jjdep.mavenId.artifactId)) {
					deps.add(jjdep);
				}
			}
			return deps;
		}
	};

	public final Lazy<List<JavaJarDependency>> asm = new Lazy<>(this::createAsm);
	protected List<JavaJarDependency> createAsm() {
		String asmGroup = "org.ow2.asm";
		String asmVersion = "9.3";
		return Arrays.asList(
				Maven.getMavenJarDep(Maven.MAVEN_CENTRAL, new MavenId(asmGroup, "asm", asmVersion)),
				Maven.getMavenJarDep(Maven.MAVEN_CENTRAL, new MavenId(asmGroup, "asm-analysis", asmVersion)),
				Maven.getMavenJarDep(Maven.MAVEN_CENTRAL, new MavenId(asmGroup, "asm-commons", asmVersion)),
				Maven.getMavenJarDep(Maven.MAVEN_CENTRAL, new MavenId(asmGroup, "asm-tree", asmVersion)),
				Maven.getMavenJarDep(Maven.MAVEN_CENTRAL, new MavenId(asmGroup, "asm-util", asmVersion))
		);
	}

	public final BJavaModule fabricmerge = new BJavaModule() {
		@Override
		public boolean hasTests() {
			return false;
		}

		@Override
		MavenId getId() {
			return new MavenId(GROUP, "fabricmerge", "0.2");
		}

		@Override
		protected List<JavaJarDependency> createDependencies() {
			ArrayList<JavaJarDependency> deps = new ArrayList<>();
			deps.addAll(junit.get());
			deps.addAll(asm.get());
			return deps;
		}
	};

	public final BJavaModule mixinCompileExtensions = new BJavaModule() {
		@Override
		public boolean hasTests() {
			return false;
		}

		@Override
		MavenId getId() {
			return new MavenId(GROUP, "brachyura-mixin-compile-extensions", "0.10");
		}

		@Override
		protected List<JavaJarDependency> createDependencies() {
			return Collections.singletonList(Maven.getMavenJarDep("https://repo.spongepowered.org/repository/maven-public/", new MavenId("org.spongepowered", "mixin", "0.8.3")));
		}

		@Override
		protected JavaCompilation createCompilation() {
			return super.createCompilation().addOption("-proc:none");
		}
	};

	public final BJavaModule accessWidener = new BJavaModule() {
		@Override
		public boolean hasTests() {
			return false;
		}

		@Override
		MavenId getId() {
			return new MavenId(GROUP, "access-widener", "0.2");
		}

		@Override
		protected List<JavaJarDependency> createDependencies() {
			return asm.get();
		}
	};

	public final BJavaModule bootstrap = new BJavaModule() {
		@Override
		public boolean hasTests() {
			return false;
		}

		@Override
		MavenId getId() {
			return new MavenId(GROUP, "brachyura-bootstrap", "0");
		}

		@Override
		public String getModuleName() {
			return "bootstrap";
		}

		@Override
		protected List<JavaJarDependency> createDependencies() {
			return Collections.emptyList();
		}
	};

	public final BJavaModule[] modules = {brachyura, trieharder, fernutil, fabricmerge, mixinCompileExtensions, accessWidener, bootstrap};
	public final BJavaModule[] publishModules = {brachyura, trieharder, fernutil, fabricmerge, mixinCompileExtensions, accessWidener, bootstrap};

	@Override
	public IdeModule[] getIdeModules() {
		IdeModule[] ideModules = new IdeModule[modules.length];
		for (int i = 0; i < ideModules.length; i++) {
			ideModules[i] = modules[i].ideModule.get();
		}
		return ideModules;
	}

	void build() {
		List<BJarResult> builts = Lazy.getParallel(Arrays.stream(modules).map(m -> m.built).collect(Collectors.toList()));

		Path allOutJar = getBuildDir().resolve("out.jar");

		List<Path> jars = builts.stream()
				.map(x -> x.main.jar)
				.collect(Collectors.toList());

		PathUtil.mergeJars(allOutJar, jars);

		/*if (!Boolean.getBoolean("skiptests")) {
			for (BJavaModule javaModule : modules) {
				javaModule.test();
			}
		}*/
	}

	void publish() {
		build();
		try {
			/*List<URL> cp = new ArrayList<>();
			cp.add(build.built.get().main.jar.toUri().toURL());
			for (JavaJarDependency jjd : build.dependencies.get()) {
				cp.add(jjd.jar.toUri().toURL());
			}
			List<String> mstrings = Arrays.stream(publishModules).map(BJavaModule::getModuleName).collect(Collectors.toList());
			ArrayList<String> depJars = new ArrayList<>();
			for (JavaJarDependency jjd : bdeps.get()) appendUrls(depJars, Maven.MAVEN_CENTRAL, jjd);
			for (JavaJarDependency jjd : asm.get()) appendUrls(depJars, Maven.MAVEN_CENTRAL, jjd);
			appendUrls(depJars, Maven.MAVEN_CENTRAL, fastutil.get());
			appendUrls(depJars, "https://maven.fabricmc.net/", mappingIo.get());
			try (URLClassLoader ucl = new URLClassLoader(cp.toArray(new URL[0]), ClassLoader.getSystemClassLoader().getParent())) {
				Class<?> entry = Class.forName("io.github.coolcrabs.brachyura.build.Main", true, ucl);
				MethodHandles.publicLookup().findStatic(
								entry,
								"main",
								MethodType.methodType(void.class, Path.class, String[].class, String[].class)
						)
						.invokeExact(build.getModuleRoot(), mstrings.toArray(new String[0]), depJars.toArray(new String[0]));
			}*/
		} catch (Throwable e) {
			throw Util.sneak(e);
		}
	}

	static final String[] exts = {".jar", "-sources.jar"};
	void appendUrls(List<String> depJars, String murl, JavaJarDependency jdep) {
		MavenId mid = jdep.mavenId;
		for (String ext : exts) {
			String fileName = mid.artifactId + "-" + mid.version + ext;
			String url = murl + mid.groupId.replace('.', '/') + "/" + mid.artifactId + "/" + mid.version + "/" + fileName;
			depJars.add(url);
		}
	}

	@Override
	public void getTasks(Consumer<Task> p) {
		super.getTasks(p);
		p.accept(Task.of("build", this::build));
		p.accept(Task.of("publish", this::publish));
	}

}