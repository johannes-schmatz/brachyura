package io.github.coolcrabs.brachyura.added.basicbuildscript;

public class DefaultVersions {
	public static final String MINECRAFT = "1.12.2";
	public static final MavenRef FABRIC_LOADER = new MavenRef(
			"https://maven.fabricmc.net",
			"net.fabricmc",
			"fabric-loader",
			"0.14.9"
	);
	public static final MavenRef LEGACY_YARN = new MavenRef(
			"https://maven.legacyfabric.net",
			"net.legacyfabric",
			"yarn",
			"1.12.2+build.442"
	);
	public static final MavenRef LEGACY_INTERMEDIARY = new MavenRef(
			"https://maven.legacyfabric.net",
			"net.fabricmc",
			"intermediary",
			"1.12.2"
	);
	public static final MavenRef QUILT_FLOWER = new MavenRef(
			"https://maven.quiltmc.org/repository/release",
			"org.quiltmc",
			"quiltflower",
			"1.8.1"
	);
}
