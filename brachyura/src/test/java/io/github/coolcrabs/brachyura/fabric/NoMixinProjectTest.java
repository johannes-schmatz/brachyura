package io.github.coolcrabs.brachyura.fabric;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import io.github.coolcrabs.brachyura.util.Lazy;
import org.junit.jupiter.api.Test;

import io.github.coolcrabs.brachyura.TestUtil;
import io.github.coolcrabs.brachyura.decompiler.BrachyuraDecompiler;
import io.github.coolcrabs.brachyura.dependency.JavaJarDependency;
import io.github.coolcrabs.brachyura.fabric.FabricContext.ModDependencyCollector;
import io.github.coolcrabs.brachyura.minecraft.Minecraft;
import io.github.coolcrabs.brachyura.minecraft.VersionMeta;
import io.github.coolcrabs.brachyura.util.PathUtil;
import net.fabricmc.mappingio.tree.MappingTree;

public class NoMixinProjectTest {
    SimpleFabricProject fabricProject = new SimpleFabricProject() {
        @Override
        public VersionMeta createMcVersion() {
            return Minecraft.getVersion("1.16.5");
        }

        @Override
        public MappingTree createMappings() {
            return createMojmap();
        }

        @Override
        public FabricLoader getLoader() {
            return new FabricLoader(FabricMaven.URL, FabricMaven.loader("0.12.12"));
        }

        @Override
        public Path getProjectDir() {
            Path result = PathUtil.CWD.resolve("test").resolve("fabric").resolve("mojmap_nomixin");
            assertTrue(Files.isDirectory(result)); 
            return result;
        }

        @Override
        public void getModDependencies(ModDependencyCollector d) {
        }

        @Override
        public BrachyuraDecompiler decompiler() {
            return null;
        }

        @Override
        public Map<String, Lazy<String>> getTemplateMappingsForSources() {
            Map<String, Lazy<String>> r = new HashMap<>();

            r.put("boolean", new Lazy<>(() -> "true"));
            r.put("byte", new Lazy<>(() -> "0b00101010"));
            r.put("short", new Lazy<>(() -> "420"));
            r.put("int", new Lazy<>(() -> "69"));
            r.put("long", new Lazy<>(() -> "69696969"));
            r.put("string", new Lazy<>(() -> "Templates work."));

            return r;
        }

        @Override
        public Map<String, Lazy<String>> getTemplateMappingsForResources() {
            Map<String, Lazy<String>> r = new HashMap<>();

            r.put("newline", new Lazy<>(() -> "\n"));
            r.put("hello_world", new Lazy<>(() -> "Hello World!"));

            return r;
        }
    };

    @Test
    void compile() {
        try {
            long s = System.currentTimeMillis();
            JavaJarDependency b = fabricProject.build();
            long s2 = System.currentTimeMillis() - s;
            System.out.println(s2);
            // Seems to work accross java versions for now
            TestUtil.assertSha256(b.jar, "7e2790149be4af7e38e6ea28f8c72de89cf2d02e08977de9c305951e6bc59f76");
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
}
