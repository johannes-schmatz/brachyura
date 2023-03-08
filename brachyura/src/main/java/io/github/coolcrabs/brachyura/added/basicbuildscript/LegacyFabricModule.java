package io.github.coolcrabs.brachyura.added.basicbuildscript;

import io.github.coolcrabs.brachyura.dependency.JavaJarDependency;
import io.github.coolcrabs.brachyura.fabric.FabricContext;
import io.github.coolcrabs.brachyura.fabric.FabricLoader;
import io.github.coolcrabs.brachyura.fabric.FabricModule;
import io.github.coolcrabs.brachyura.maven.MavenId;
import io.github.coolcrabs.brachyura.util.Lazy;
import io.github.coolcrabs.brachyura.util.OsUtil;
import io.github.coolcrabs.brachyura.util.Util;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LegacyFabricModule extends FabricModule {
	public final LegacyFabricProject project;
	public LegacyFabricModule(LegacyFabricProject project, FabricContext context, SettingsCollector settings) {
		super(context);
		this.project = project;
	}

	@Override
	public int getJavaVersion() {
		return 8; // TODO: move to settings
	}

	@Override
	public Path[] getResourceDirs() {
		StackTraceElement[] trace = Thread.currentThread().getStackTrace();
		String caller = trace[2].getClassName() + "." + trace[2].getMethodName();
		return getResourceDirs(caller).toArray(new Path[0]);
		//return project.settings.resources.toArray(new Path[0]);
	}

	@Override
	public Path[] getTemplateSrcDirs() {
		return new Path[0]; // TODO
	}

	@Override
	public Path[] getTemplateResourceDirs() {
		return new Path[0]; // TODO
	}

	private int resourceCtr = 0;
	private List<Path> getResourceDirs(String caller) {
		List<Path> resourceDirs = project.settings.resources;

		resourceCtr = (resourceCtr + 1) % 3;

		switch (caller) {
			case "io.github.coolcrabs.brachyura.fabric.FabricModule.ideModule": {
				if (resourceCtr == 0) {
					return resourceDirs;
				} else {
					return resourceDirs;
				}
			}
			default: {
				System.out.println("[WARN]: getResourceDirs for caller isn't defined:");
				new Throwable().printStackTrace(System.out);

				return resourceDirs;
			}
		}
	}

	@Override
	public String getModuleName() {
		return project.getModId();
	}

	@Override
	public Path getModuleRoot() {
		return project.getProjectDir();
	}

	@Override
	public Path[] getSrcDirs() {
		StackTraceElement[] trace = Thread.currentThread().getStackTrace();
		String caller = trace[2].getClassName() + "." + trace[2].getMethodName();
		return getSrcDirs(caller).toArray(new Path[0]);
	}

	private List<Path> getSrcDirs(String caller) {
		List<Path> srcDirs = project.settings.src;
		List<Path> templateDirs = project.settings.templates;

		switch (caller) {
			case "io.github.coolcrabs.brachyura.fabric.FabricModule.ideModule": {
				// for generating the ide module use this
				List<Path> paths = new ArrayList<>(srcDirs.size() + templateDirs.size());
				paths.addAll(srcDirs);
				paths.addAll(templateDirs);
				return paths;
			}
			case "io.github.coolcrabs.brachyura.fabric.FabricModule.createFabricCompilationResult": {
				// fill in the template, copy over, add that path to the build
				// TODO: do that

				List<Path> templatedTemplateDirs = copyTemplates(templateDirs);

				List<Path> paths = new ArrayList<>(srcDirs.size() + templateDirs.size());
				paths.addAll(srcDirs);
				paths.addAll(templatedTemplateDirs);
				return paths;
			}
			default: {
				System.out.println("[WARN]: getSrcDirs for caller isn't defined:");
				new Throwable().printStackTrace(System.out);

				return srcDirs;
			}
		}
	}

	@Override
	public List<String> ideVmArgs(boolean client) {
		try {
			ArrayList<String> r = new ArrayList<>();
			r.add("-Dfabric.development=true");
			r.add("-Dfabric.remapClasspathFile=" + context.runtimeRemapClasspath.get());
			r.add("-Dlog4j.configurationFile=" + writeLog4jXml());
			r.add("-Dlog4j2.formatMsgNoLookups=true");
			r.add("-Dfabric.log.disableAnsi=false");

			r.add("-Djava.awt.headless=true");

			r.add("-javaagent:" + getJavaAgent());

			if (client) {
				String natives = context.extractedNatives.get().stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
				r.add("-Djava.library.path=" + natives);
				r.add("-Dtorg.lwjgl.librarypath=" + natives);
				if (OsUtil.OS == OsUtil.Os.OSX) {
					r.add("-XstartOnFirstThread");
				}
			}
			return r;
		} catch (Exception e) {
			throw Util.sneak(e);
		}
	}

	private String getJavaAgent() {
		FabricLoader loader = context.getLoader();
		JavaJarDependency[] deps = loader.commonDeps;
		for (JavaJarDependency i : deps) {
			MavenId mavenId = i.mavenId;
			if (mavenId != null) {
				if ("sponge-mixin".equals(mavenId.artifactId)) {
					Path jar = i.jar;
					return jar.toString();
				}
			}
		}

		throw new RuntimeException("sponge-mixin was not found.");
	}

	private List<Path> copyTemplates(List<Path> templates) {
		Path base = project.getLocalBrachyuraPath().resolve("template");

		List<Path> ret = new ArrayList<>(templates.size());

		int uniqueCounter = 0;

		for(Path templateDir: templates) {
			String dirName = templateDir.getFileName() + "_" + uniqueCounter;

			Path output = base.resolve(dirName);

			try {
				Files.createDirectories(output);

				TemplateFileVisitor visitor = new TemplateFileVisitor(templateDir, output, this::mapTemplate);
				Files.walkFileTree(templateDir, visitor);
			} catch (IOException e) {
				Util.sneak(e);
			}

			ret.add(output);
		}

		return ret;
	}

	private static class TemplateFileVisitor extends SimpleFileVisitor<Path> {
		public final Path fromBase, toBase;
		public final Function<String, String> mapper;
		public TemplateFileVisitor(Path fromBase, Path toBase, Function<String, String> mapper) {
			this.fromBase = fromBase;
			this.toBase = toBase;
			this.mapper = mapper;
		}
		@Override
		public FileVisitResult visitFile(Path from, BasicFileAttributes attrs) throws IOException {
			Path relative = fromBase.relativize(from);
			Path to = toBase.resolve(relative);

			Files.deleteIfExists(to);
			Files.createDirectories(to.getParent());
			Files.createFile(to);

			try (
				PrintWriter w = new PrintWriter(Files.newBufferedWriter(to, StandardOpenOption.CREATE));
				Stream<String> stream = Files.lines(from);
			) {
					stream
						.map(mapper)
						.forEach(w::println);
			}

			return FileVisitResult.CONTINUE;
		}
	}

	private String mapTemplate(String original) {
		try {
			for (Map.Entry<String, Lazy<String>> i: project.settings.templateMap.entrySet()) {
				String key = i.getKey();
				Pattern templatePattern = Pattern.compile(
						"(" +
							"(" + // variants of simple data types
								"Boolean\\.parseBoolean" + "|" +
								"Byte\\.parseByte" + "|" +
								"Short\\.parseShort" + "|" +
								"Integer\\.parseInt" + "|" +
								"Long\\.parseLong" +
							")" +
							"(" +
								"\\(\"\\$\\{" + key +"}\"\\)" + // ("${key}")
							")" + "|" +
								"(?<=\")" + // has " in front
								"\\$\\{" + key + "}" + // ${key}
								"(?=\")" + // has " in back
						")"
				);

				Matcher m = templatePattern.matcher(original);

				if (m.find()) {
					original = m.replaceAll(i.getValue().get());
				}
			}

			return original;
		} catch (IllegalArgumentException e) {
			System.err.println("It seems your replacement value contains $, { or }. Go fix that!");
			throw e;
		}
	}
}
