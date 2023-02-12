package io.github.coolcrabs.brachyura.added.basicbuildscript;

import io.github.coolcrabs.accesswidener.AccessWidener;
import io.github.coolcrabs.brachyura.decompiler.BrachyuraDecompiler;
import io.github.coolcrabs.brachyura.decompiler.fernflower.FernflowerDecompiler;
import io.github.coolcrabs.brachyura.fabric.*;
import io.github.coolcrabs.brachyura.maven.Maven;
import io.github.coolcrabs.brachyura.minecraft.Minecraft;
import io.github.coolcrabs.brachyura.minecraft.VersionMeta;
import net.fabricmc.mappingio.tree.MappingTree;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public class LegacyFabricContext extends FabricContext {
	private final LegacyFabricProject project;
	private final SettingsCollector settings;
	public LegacyFabricContext(LegacyFabricProject project, SettingsCollector settings) {
		this.project = project;
		this.settings = settings;
	}

	@Override
	public VersionMeta createMcVersion() {
		return Minecraft.getVersion(settings.minecraft);
	}

	@Override
	public MappingTree createMappings() {
		return settings.yarn.as(Yarn::ofMaven).tree;
	}

	@Override
	public FabricLoader getLoader() {
		return settings.loader.as(FabricLoader::new);
	}

	@Override
	public void getModDependencies(ModDependencyCollector d) {
		for (MavenRef i: settings.dependencies) {
			d.add(i.getJarDependency(), i.usage);
		}
	}

	@Override
	protected @Nullable AccessWidener createAw() {
		return project.createAw();
	}

	@Override
	public @Nullable BrachyuraDecompiler decompiler() {
		// Uses QuiltFlower instead of CFR
		return new FernflowerDecompiler(
				settings.decompiler.as(Maven::getMavenJarDep)
		);
	}

	@Override
	public Path getContextRoot() {
		return project.getProjectDir();
	}

	@Override
	protected MappingTree createIntermediary() {
		return settings.intermediary.as(Intermediary::ofMaven).tree;
	}
}
