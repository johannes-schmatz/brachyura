package io.github.coolcrabs.brachyura.project.java;

import io.github.coolcrabs.brachyura.dependency.JavaJarDependency;
import io.github.coolcrabs.brachyura.ide.IdeModule;
import io.github.coolcrabs.brachyura.ide.IdeModule.RunConfigBuilder;
import io.github.coolcrabs.brachyura.maven.Maven;
import io.github.coolcrabs.brachyura.maven.MavenId;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.github.coolcrabs.brachyura.util.PathUtil;
import org.junit.jupiter.api.Assertions;

class SimpleJavaProjectTest {
    @Test
    void compile() {
        SimpleJavaProject project = new SimpleJavaProject() {
            @Override
            public MavenId getId() {
                return new MavenId("io.github.coolcrabs", "testprogram", "0.0");
            }

            @Override
            public int getJavaVersion() {
                return 8;
            }

            @Override
            public Path getProjectDir() {
                return PathUtil.CWD.resolve("testprogram");
            }

            @Override
            public List<JavaJarDependency> createDependencies() {
                return Arrays.asList(
                    Maven.getMavenJarDep(Maven.MAVEN_CENTRAL, new MavenId("org.junit.platform:junit-platform-console-standalone:1.8.2"))
                );
            }

            @Override
            public SimpleJavaModule createProjectModule() {
                return new SimpleJavaProjectModule() {
                    @Override
                    public IdeModule createIdeModule() {
                        return new IdeModule.IdeModuleBuilder()
                            .name(getModuleName())
                            .root(getModuleRoot())
                            .javaVersion(getJavaVersion())
                            .sourcePaths(getSrcDirs())
                            .resourcePaths(getResourceDirs())
                            .testSourcePath(getModuleRoot().resolve("src").resolve("test").resolve("java"))
                            .testResourcePath(getModuleRoot().resolve("src").resolve("test").resolve("resources"))
                            .dependencies(dependencies.get())
                            .dependencyModules(getModuleDependencies().stream().map(BuildModule::createIdeModule).collect(Collectors.toList()))
                            .runConfigs(
                                new RunConfigBuilder()
                                    .name("bruh")
                                    .cwd(getModuleRoot())
                                    .mainClass("io.github.coolcrabs.testprogram.TestProgram")
                            )
                            .build();
                    }
                };
            }
        };

        project.runTask("netbeans");
        project.runTask("idea");
        project.runTask("jdt");
        Assertions.assertNotNull(project.build());
        // for now don't publish to local maven
        //project.getTasks(p -> {
        //    if (p.getName().equals("publishToMavenLocal")) p.doTask(new String[]{});
        //});
    }
}
