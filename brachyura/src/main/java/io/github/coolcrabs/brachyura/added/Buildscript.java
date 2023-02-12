package io.github.coolcrabs.brachyura.added;

import io.github.coolcrabs.brachyura.added.basicbuildscript.DefaultVersions;
import io.github.coolcrabs.brachyura.added.basicbuildscript.GitHelper;
import io.github.coolcrabs.brachyura.added.basicbuildscript.LegacyFabricProject;
import io.github.coolcrabs.brachyura.added.basicbuildscript.SettingsCollector;

@SuppressWarnings("unused")
public class Buildscript extends LegacyFabricProject {
	@SuppressWarnings("unused")
	public Buildscript() {
		super();

		SettingsCollector o = settings;

		o.dependency(
				"https://maven.enginehub.org/repo/",
				"com.sk89q.worldedit",
				"worldedit-core",
				"6.1"//"7.2.9"
		);

		o.src("src", "main", "java");
		o.resource("src", "main", "resources");
		o.template("src", "template", "java");

		o.templateMap("version", getVersion());
		o.templateMap("timestamp", getBuildDate("yyyy-MM-dd'T'HH:mm:ss'Z'"));
		o.templateMap("branch", GitHelper.BRANCH);
		o.templateMap("commit", GitHelper.COMMIT);
		o.templateMap("working_dir_clean", GitHelper.STATUS);
		o.templateMap("minecraft_version", DefaultVersions.MINECRAFT);
		o.templateMap("yarn_mappings", DefaultVersions.LEGACY_YARN.mavenId.version);
		o.templateMap("yarn_jar_url", DefaultVersions.LEGACY_YARN.asUrlString());
	}
/*
	@Override
	public void getTasks(Consumer<Task> p){
		super.getTasks(p);
		p.accept(Task.of("buildRun", this::buildRun));
	}

	public void buildRun(){
		build();
		runTask("runMinecraftClient");
	}*/


}
