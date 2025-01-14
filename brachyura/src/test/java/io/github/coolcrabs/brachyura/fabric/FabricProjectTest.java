package io.github.coolcrabs.brachyura.fabric;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.github.coolcrabs.brachyura.decompiler.BrachyuraDecompiler;
import io.github.coolcrabs.brachyura.fabric.FabricContext.ModDependencyCollector;
import io.github.coolcrabs.brachyura.fabric.FabricContext.ModDependencyFlag;
import io.github.coolcrabs.brachyura.maven.Maven;
import io.github.coolcrabs.brachyura.maven.MavenId;
import io.github.coolcrabs.brachyura.minecraft.Minecraft;
import io.github.coolcrabs.brachyura.minecraft.VersionMeta;
import io.github.coolcrabs.brachyura.util.JvmUtil;
import io.github.coolcrabs.brachyura.util.PathUtil;
import net.fabricmc.mappingio.tree.MappingTree;

class FabricProjectTest {
    SimpleFabricProject fabricProject = new SimpleFabricProject() {
        @Override
        public VersionMeta createMcVersion() {
            return Minecraft.getVersion("1.18.2");
        }

        public int getJavaVersion() {
            return 17;
        }

        @Override
        public MappingTree createMappings() {
            return Yarn.ofMaven(FabricMaven.URL, FabricMaven.yarn("1.18.2+build.2")).tree;
        }

        @Override
        public FabricLoader getLoader() {
            return new FabricLoader(FabricMaven.URL, FabricMaven.loader("0.13.3"));
        }

        @Override
        public Path getProjectDir() {
            Path result = PathUtil.CWD.resolve("testmod");
            assertTrue(Files.isDirectory(result)); 
            return result;
        }

        @Override
        public void getModDependencies(ModDependencyCollector d) {
            jij(d.addMaven(FabricMaven.URL, new MavenId(FabricMaven.GROUP_ID + ".fabric-api", "fabric-resource-loader-v0", "0.4.18+2de5574560"), ModDependencyFlag.RUNTIME, ModDependencyFlag.COMPILE));
            jij(d.addMaven(FabricMaven.URL, new MavenId(FabricMaven.GROUP_ID + ".fabric-api", "fabric-game-rule-api-v1", "1.0.13+d7c144a860"), ModDependencyFlag.RUNTIME, ModDependencyFlag.COMPILE));
            jij(d.addMaven(FabricMaven.URL, new MavenId(FabricMaven.GROUP_ID + ".fabric-api", "fabric-registry-sync-v0", "0.9.8+0d9ab37260"), ModDependencyFlag.RUNTIME, ModDependencyFlag.COMPILE));
            jij(d.addMaven(Maven.MAVEN_CENTRAL, new MavenId("org.ini4j:ini4j:0.5.4"), ModDependencyFlag.RUNTIME, ModDependencyFlag.COMPILE));
        }

        @Override
        public BrachyuraDecompiler decompiler() {
            return null;
            // return new CfrDecompiler();
            // return new FernflowerDecompiler(Maven.getMavenJarDep("https://maven.quiltmc.org/repository/release", new MavenId("org.quiltmc:quiltflower:1.7.0"))); 
        }
    };

    @Test
    void testProject() {
        assertTrue(Files.isRegularFile(fabricProject.context.get().intermediaryjar.get().jar));
        assertTrue(Files.isRegularFile(fabricProject.context.get().namedJar.get().jar));
        // assertTrue(Files.isRegularFile(fabricProject.getDecompiledJar().jar));
    }
    
    @Test
    void ide() {
        long a = System.currentTimeMillis();
        fabricProject.runTask("netbeans");
        fabricProject.runTask("idea");
        fabricProject.runTask("jdt");
        long b = System.currentTimeMillis();
        System.out.println(b - a);
    }

    @Test
    void compile() {
        if (JvmUtil.CURRENT_JAVA_VERSION >= 17) {
            try {
                fabricProject.build();
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        }
    }
}
