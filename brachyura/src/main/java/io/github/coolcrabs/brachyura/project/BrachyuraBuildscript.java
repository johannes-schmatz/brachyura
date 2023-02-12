package io.github.coolcrabs.brachyura.project;

import io.github.coolcrabs.brachyura.dependency.JavaJarDependency;
import io.github.coolcrabs.brachyura.ide.IdeModule;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * If you implement this interface, you signal brachyura that you're the buildscript that builds brachyura itself.
 * The IdeModule returned will be added as a ide module dependency for the buildscript.
 * This solves the problem of that the .jar files that the Buildscript.java (the one at the repo root) generates end up as libraries in
 * .idea/libraries
 */
public interface BrachyuraBuildscript {
	/**
	 * Give the IdeModule that is brachyura itself.<br>
	 * This allows brachyura to set up the dependencies for the ide so that the ide builds brachyura correctly first, and doesn't have any library from the
	 * original launch classpath of brachyura.
	 * @return the IdeModule that represents brachyura itself
	 */
	IdeModule getBrachyuraIdeModule();

	/**
	 * Give a list of dependencies that should be added to the buildscript module.
	 * @return a list of additional libraries, may be null
	 */
	@Nullable
	List<JavaJarDependency> getOtherDependencies();
}
