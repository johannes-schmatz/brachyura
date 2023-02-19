package io.github.coolcrabs.brachyura;

import io.github.coolcrabs.brachyura.util.PathUtil;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UtilTest {
	@Test
	void pathUtilResolve() {
		Path p = PathUtil.CWD;

		Path p1 = p.resolve("foo").resolve("bar").resolve("barbarbar");
		Path p2 = PathUtil.resolve(p, "foo", "bar", "barbarbar");

		assertEquals(p1, p2);
	}
}
