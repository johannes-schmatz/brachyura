package io.github.coolcrabs.brachyura.added.basicbuildscript;

import io.github.coolcrabs.brachyura.fabric.FabricContext.*;
import io.github.coolcrabs.brachyura.util.Lazy;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SettingsCollector {
	private final Path projectDir;
	public SettingsCollector(Path projectDir) {
		this.projectDir = projectDir;
	}
	//TODO: write java doc
	//TODO: split this up in static classes like Dependencies and Minecraft, maybe even have instances from them? (for the module??)
	// so code would look like `Dependencies d = new Dependencies(this_ide_module); d.add("https://maven.example.com/", "org.example.foo:foo:2.3");`
	// maybe make it register itself in the module?

	// build date format
	public DateTimeFormatter buildDateFormat = DateTimeFormatter.ofPattern(".yyyyMMdd.HHmmss");
	public void buildDateFormat(String format) {
		buildDateFormat = DateTimeFormatter.ofPattern(format);
	}

	// minecraft
	public String minecraft = DefaultVersions.MINECRAFT;
	public void minecraft(String version) {
		minecraft = version;
	}

	// loader
	public MavenRef loader = DefaultVersions.FABRIC_LOADER;
	public void loader(String repo, String group, String artifact, String version) {
		loader = new MavenRef(repo, group, artifact, version);
	}

	// yarn
	public MavenRef yarn = DefaultVersions.LEGACY_YARN;
	public void yarn(String repo, String group, String artifact, String version) {
		yarn = new MavenRef(repo, group, artifact, version);
	}

	// intermediary
	public MavenRef intermediary = DefaultVersions.LEGACY_INTERMEDIARY;
	public void intermediary(String repo, String group, String artifact, String version) {
		intermediary = new MavenRef(repo, group, artifact, version);
	}

	// decompiler
	public MavenRef decompiler = DefaultVersions.QUILT_FLOWER;
	public void decompiler(String repo, String group, String artifact, String version) {
		decompiler = new MavenRef(repo, group, artifact, version);
	}

	// dependencies

	public final List<MavenRef> dependencies = new ArrayList<>();

	/**
	 * use this like {@code dependency("https://maven.example.com", "com.example.foo:foo:0.2.3")}
	 */
	public void dependency(String repo, String mavenId) {
		String[] mavenIdSplit = mavenId.split(":");
		if (mavenIdSplit.length != 3) throw new IllegalArgumentException("Cannot parse maven id '" + mavenId + "', doesn't split in 3 parts");
		dependency(repo, mavenIdSplit[0], mavenIdSplit[1], mavenIdSplit[2]);
	}
	public void dependency(String repo, String group, String artifact, String version) {
		dependency(new MavenRef(repo, group, artifact, version));
	}
	public void dependency(String repo, String group, String artifact, String version, String fileExtension) {
		dependency(new MavenRef(repo, group, artifact, version, fileExtension));
	}
	public void dependency(MavenRef mavenRef) {
		dependencies.add(mavenRef);
	}

	public void dependencyCompile(String repo, String group, String artifact, String version) {
		dependency(new MavenRef(repo, group, artifact, version, new ModDependencyFlag[]{ModDependencyFlag.COMPILE}));
	}
	public void dependencyCompile(String repo, String group, String artifact, String version, String fileExtension) {
		dependency(new MavenRef(repo, group, artifact, version, fileExtension, new ModDependencyFlag[]{ModDependencyFlag.COMPILE}));
	}

	public void dependencyRun(String repo, String group, String artifact, String version) {
		dependency(new MavenRef(repo, group, artifact, version, new ModDependencyFlag[]{ModDependencyFlag.RUNTIME}));
	}
	public void dependencyRun(String repo, String group, String artifact, String version, String fileExtension) {
		dependency(new MavenRef(repo, group, artifact, version, fileExtension, new ModDependencyFlag[]{ModDependencyFlag.RUNTIME}));
	}

	// src
	public final List<Path> src = new ArrayList<>();
	public void src(String... path) {
		src.add(resolve(path));
	}

	// resources
	public final List<Path> resources = new ArrayList<>();
	public void resource(String... path) {
		resources.add(resolve(path));
	}

	// template
	public final List<Path> templates = new ArrayList<>();
	public void template(String... path) {
		templates.add(resolve(path));
	}

	// templateMap
	public final Map<String, Lazy<String>> templateMap = new HashMap<>();
	public void templateMap(String key, Lazy<String> value) {
		templateMap.put(key, value);
	}
	public void templateMap(String key, String value) {
		templateMap.put(key, new Lazy<>(() -> value));
	}

	// helper methods
	private Path resolve(String[] paths) {
		Path p = projectDir;
		for (String i : paths) p = p.resolve(i);
		return p;
	}

	// verify
	private boolean verified = false;
	public void verify() {
		if (verified) return;

		SettingsCollector other = new SettingsCollector(null);
		warnAbout(other, "loader");
		warnAbout(other, "yarn");
		warnAbout(other, "intermediary");
		warnAbout(other, "decompiler");

		verified = true;
	}

	private void warnAbout(SettingsCollector defaults, String fieldName) {
		try {
			Field field = this.getClass().getDeclaredField(fieldName);
			Object our = field.get(this);
			Object other = field.get(defaults);
			if (our == other) {
				System.err.println("[WARN]: You're not supposed to keep using defaults (reference) for \"" + fieldName + "\" as they might change in the " +
						"future.");
			}
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}
}
