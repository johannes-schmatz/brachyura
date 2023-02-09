package io.github.coolcrabs.brachyura.project;

import io.github.coolcrabs.brachyura.ide.IdeModule;

/**
 * If you implement this interface, you signal brachyura that you're the buildscript that builds brachyura itself.
 * The IdeModule returned will be added as a ide module dependency for the buildscript.
 * This solves the problem of that the .jar files that the Buildscript.java (the one at the repo root) generates end up as libraries in
 * .idea/libraries
 */
public interface BrachyuraBuildscript {
	IdeModule getBrachyuraIdeModule();
}
