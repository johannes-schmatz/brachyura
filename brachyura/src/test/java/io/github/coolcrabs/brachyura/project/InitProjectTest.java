package io.github.coolcrabs.brachyura.project;

import io.github.coolcrabs.brachyura.TestUtil;
import io.github.coolcrabs.brachyura.util.PathUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class InitProjectTest {
	Path projectDir = PathUtil.CWD.resolve("test").resolve("project_init_test");
	BuildscriptProject buildscriptProject = new BuildscriptProject() {

	};
	Project project;

	@Test
	void initProjectTest() {
		assertDoesNotThrow(this::initProject);
	}
	void initProject() throws IOException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {

		// setup EntryGlobals
		Method m = EntryGlobals.class.getDeclaredMethod("set", Path.class, List.class); // @Nullable Path, @Nullable List<Path>
		m.setAccessible(true);
		m.invoke(null, projectDir, null);

		PathUtil.deleteDirectoryIfExists(projectDir);

		Files.createDirectories(projectDir); // this is done by the user

		buildscriptProject.runTask("init");

		Path buildscript = PathUtil.resolve(projectDir, "buildscript", "src", "main", "java", "Buildscript.java");

		TestUtil.assertFileExists(buildscript);

		project = assertDoesNotThrow(() -> buildscriptProject.createProjectThrowing());

		assertNotNull(project, "Couldn't compile project");

		project.setIdeProject(buildscriptProject);

		project.runTask("netbeans");
		project.runTask("idea");
		project.runTask("jdt");

		project.runTask("build");

		TestUtil.assertFileExists(PathUtil.resolve(projectDir, "build", "libs", "modid-0.0.1.jar"));

		PathUtil.deleteDirectory(projectDir);
	}
}
