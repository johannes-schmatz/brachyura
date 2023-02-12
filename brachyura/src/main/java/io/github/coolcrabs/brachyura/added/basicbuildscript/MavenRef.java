package io.github.coolcrabs.brachyura.added.basicbuildscript;

import io.github.coolcrabs.brachyura.dependency.JavaJarDependency;
import io.github.coolcrabs.brachyura.fabric.FabricContext.ModDependencyFlag;
import io.github.coolcrabs.brachyura.maven.Maven;
import io.github.coolcrabs.brachyura.maven.MavenId;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.BiFunction;

public class MavenRef {
	private static final String DEFAULT_FILE_EXTENSION = ".jar";
	private static final ModDependencyFlag[] BOTH = {ModDependencyFlag.RUNTIME, ModDependencyFlag.COMPILE};
	public final String repo;
	public final MavenId mavenId;
	public final String fileExtension;
	public final ModDependencyFlag[] usage;

	public MavenRef(String repo, String group, String artifact, String version) {
		this(repo, group, artifact, version, DEFAULT_FILE_EXTENSION, BOTH);
	}

	public MavenRef(String repo, String group, String artifact, String version, String fileExtension) {
		this(repo, group, artifact, version, fileExtension, BOTH);
	}

	public MavenRef(String repo, String group, String artifact, String version, ModDependencyFlag[] usage) {
		this(repo, group, artifact, version, DEFAULT_FILE_EXTENSION, usage);
	}

	public MavenRef(String repo, String group, String artifact, String version, String fileExtension, ModDependencyFlag[] usage) {
		this.repo = repo;
		this.mavenId = new MavenId(group, artifact, version);
		this.fileExtension = fileExtension;
		this.usage = usage;
	}

	public <T> T as(BiFunction<String, MavenId, T> function) {
		return function.apply(repo, mavenId);
	}

	public JavaJarDependency getJarDependency() {
		switch (fileExtension) {
			case ".jar": {
				return Maven.getMavenJarDep(repo, mavenId);
			}
			case ".litemod": {
				throw new RuntimeException("not yet implemented");
			}
		}
		throw new RuntimeException("file extension " + fileExtension + " not known!");
	}

	public String asUrlString() {
		String repo = addTrailSlash(this.repo);
		String groupId = mavenId.groupId.replace('.', '/');
		String fileName = mavenId.artifactId + "-" + mavenId.version + fileExtension;
		if (mavenId.classifier != null)
			throw new UnsupportedOperationException("mavenId.classifier currently not implemented");
		String url = repo + groupId + "/" + mavenId.artifactId + "/" + mavenId.version + "/" + fileName;

		try {
			URI mavenRepoUri = new URI(url);
			return mavenRepoUri.toString();
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	private static String addTrailSlash(String o) {
		return o.endsWith("/") ? o : o + "/";
	}
}
