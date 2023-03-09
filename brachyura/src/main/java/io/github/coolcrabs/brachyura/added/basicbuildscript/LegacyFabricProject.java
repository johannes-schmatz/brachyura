package io.github.coolcrabs.brachyura.added.basicbuildscript;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.coolcrabs.accesswidener.AccessWidener;
import io.github.coolcrabs.accesswidener.AccessWidenerReader;
import io.github.coolcrabs.brachyura.dependency.JavaJarDependency;
import io.github.coolcrabs.brachyura.exception.UnknownJsonException;
import io.github.coolcrabs.brachyura.fabric.FabricContext;
import io.github.coolcrabs.brachyura.fabric.FabricModule;
import io.github.coolcrabs.brachyura.ide.IdeModule;
import io.github.coolcrabs.brachyura.mappings.Namespaces;
import io.github.coolcrabs.brachyura.maven.MavenId;
import io.github.coolcrabs.brachyura.processing.ProcessingSource;
import io.github.coolcrabs.brachyura.processing.sinks.AtomicZipProcessingSink;
import io.github.coolcrabs.brachyura.processing.sources.DirectoryProcessingSource;
import io.github.coolcrabs.brachyura.project.Task;
import io.github.coolcrabs.brachyura.project.java.BaseJavaProject;
import io.github.coolcrabs.brachyura.project.java.SimpleJavaProject;
import io.github.coolcrabs.brachyura.util.Lazy;
import io.github.coolcrabs.brachyura.util.PathUtil;
import io.github.coolcrabs.brachyura.util.Util;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class LegacyFabricProject extends BaseJavaProject {
	public final SettingsCollector settings = new SettingsCollector(getProjectDir());
 	public final Lazy<FabricContext> context = Lazy.of(this::createContext);
	public final Lazy<FabricModule> module = Lazy.of(this::createModule);
	protected ArrayList<JavaJarDependency> jijList = new ArrayList<>();
	private BuildDate buildDate = null; // to save the build date for the version
	private final Lazy<String[]> fmjParseThingy = Lazy.of(() -> {
		try {
			Gson gson = new GsonBuilder().setPrettyPrinting().setLenient().create();
			JsonObject fabricModJson;
			for (Path resDir : getResourceDirs()) {
				Path fmj = resDir.resolve("fabric.mod.json");
				if (Files.exists(fmj)) {
					try (BufferedReader reader = PathUtil.newBufferedReader(fmj)) {
						fabricModJson = gson.fromJson(reader, JsonObject.class);
					}
					JsonElement aw = fabricModJson.get("accessWidener");
					return new String[]{fabricModJson.get("id").getAsString(), fabricModJson.get("version").getAsString(), aw == null ? null : aw.getAsString()};
				}
			}
		} catch (Exception e) {
			throw Util.sneak(e);
		}
		throw new UnknownJsonException("fabric.mod.json not found");
	});

	public JavaJarDependency build() {
		// we want to know from when the build is
		getBuildDate();

		// TODO: copy templates

		try {
			try (AtomicZipProcessingSink out = new AtomicZipProcessingSink(getBuildJarPath())) {
				FabricContext context = this.context.get();

				context.modDependencies.get(); // Ugly hack

				Iterable<ProcessingSource> sources = getResourceDirs()
						.stream()
						.map(DirectoryProcessingSource::new)
						.collect(Collectors.toList());

				context
						.resourcesProcessingChain(jijList)
						.apply(
								out,
								sources
						);
				context
						.getRemappedClasses(module.get())
						.values()
						.forEach(s -> s.getInputs(out));
				out.commit();
			}

			return new JavaJarDependency(getBuildJarPath(), null, getId());
		} catch (Exception e) {
			throw Util.sneak(e);
		}

	}

	public String getVersion() {
		String version = fmjParseThingy.get()[1];

		// append the build date when it's the dev version
		if (version.endsWith("-dev")) {
			return version + getBuildDate();
		}

		if (version.contains("$") || version.contains("/") || version.contains("(") || version.contains(")") || version.contains("\"")) {
			throw new RuntimeException("Either your version in the fabric.mod.json or the buildDate contains one of $/()\" as an illegal character. Fix that.");
		}

		return version;
	}

	public FabricContext createContext() {
		return new LegacyFabricContext(this, settings);
	}

	public FabricModule createModule() {
		return new LegacyFabricModule(this, context.get(), settings);
	}

	public List<Path> getResourceDirs() {
		return settings.resources;
	}

	public @Nullable AccessWidener createAw() {
		String aw = fmjParseThingy.get()[2];
		if (aw == null) return null;
		for (Path r : getResourceDirs()) {
			Path awp = r.resolve(aw);
			if (Files.exists(awp)) {
				AccessWidener result = new AccessWidener(Namespaces.NAMED);
				try {
					try (BufferedReader read = Files.newBufferedReader(awp)) {
						new AccessWidenerReader(result).read(read);
					}
				} catch (IOException e) {
					throw Util.sneak(e);
				}
				return result;
			}
		}
		throw new UnknownJsonException("Unable to find aw named:" + aw);
	}

	public String getMavenGroup() {
		return null;
	}

	@Nullable
	public MavenId getId() {
		return getMavenGroup() == null ? null : new MavenId(getMavenGroup(), getModId(), getVersion());
	}

	public String getModId() {
		return fmjParseThingy.get()[0];
	}

	public JavaJarDependency jij(JavaJarDependency mod) {
		jijList.add(mod);
		return mod;
	}

	@Override
	public void getTasks(Consumer<Task> p) {
		super.getTasks(p);
		p.accept(Task.of("build", this::build));
		SimpleJavaProject.createPublishTasks(p, this::build);
	}

	@Override
	public IdeModule[] getIdeModules() {
		return new IdeModule[]{module.get().createIdeModule()};
	}

	public Path getBuildJarPath() {
		return getBuildLibsDir().resolve(getModId() + "-" + getVersion() + ".jar");
	}

	public Lazy<String> getBuildDate(String format) {
		return Lazy.of(() -> {
			if (buildDate != null)
				return buildDate.format(format);
			throw new NullPointerException("build date not set");
		});
	}

	public BuildDate getBuildDate() {
		if (this.buildDate == null)
			this.buildDate = new BuildDate(settings.buildDateFormat);
		return this.buildDate;
	}
}
