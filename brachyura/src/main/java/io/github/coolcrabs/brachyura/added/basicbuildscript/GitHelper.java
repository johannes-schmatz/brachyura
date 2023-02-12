package io.github.coolcrabs.brachyura.added.basicbuildscript;

import io.github.coolcrabs.brachyura.util.Lazy;

import java.io.IOException;
import java.util.Scanner;

public class GitHelper {
	/**
	 * is the current directory clean or not?
	 */
	public static final Lazy<String> STATUS = new Lazy<>(GitHelper::getStatus);
	private static boolean getStatusBoolean() {
		try {
			int exitCode = new ProcessBuilder( // not added
					"git", "diff", "--quiet"
			).start().waitFor();

			if (exitCode == 0) {
				return new ProcessBuilder( // not commited
						"git", "diff", "--cached", "--quiet"
				).start().waitFor() == 0;
			}
			return false;
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	private static String getStatus() {
		return Boolean.toString(getStatusBoolean());
	}

	/**
	 * the current commit hash
	 */
	public static final Lazy<String> COMMIT = new Lazy<>(GitHelper::getCommit);
	private static String getCommit() {
		Process p;
		try {
			p = new ProcessBuilder(
					"git", "rev-parse", "HEAD"
			).start();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		String l = null;
		try (Scanner s = new Scanner(p.getInputStream())) {
			while (s.hasNextLine())
				l = s.nextLine();
			if (l != null)
				return l;
		}
		return "";
	}

	/**
	 * the current branch
	 */
	public static final Lazy<String> BRANCH = new Lazy<>(GitHelper::getBranch);
	private static String getBranch() {
		Process p;
		try {
			p = new ProcessBuilder(
					"git", "rev-parse", "--abbrev-ref", "HEAD"
			).start();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		String l = null;
		try (Scanner s = new Scanner(p.getInputStream())) {
			while (s.hasNextLine())
				l = s.nextLine();
			if (l != null)
				return l.substring(l.lastIndexOf('/') + 1);
		}
		return "";
	}
}
