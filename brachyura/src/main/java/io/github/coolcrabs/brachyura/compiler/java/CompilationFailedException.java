package io.github.coolcrabs.brachyura.compiler.java;

import java.util.ArrayList;
import java.util.List;

public class CompilationFailedException extends Error {
	public final List<String> compilationOutput;
    public CompilationFailedException() {
		compilationOutput = new ArrayList<>();
	}

	public CompilationFailedException(List<String> compilationOutput) {
		this.compilationOutput = compilationOutput;
	}

	public void printCompilationsErrors() {
		for (String line : compilationOutput) {
			System.out.println(line);
		}
	}
}
