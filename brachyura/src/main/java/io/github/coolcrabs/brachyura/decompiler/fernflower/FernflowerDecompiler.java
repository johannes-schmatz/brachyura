package io.github.coolcrabs.brachyura.decompiler.fernflower;

import java.nio.file.Path;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import io.github.coolcrabs.brachyura.decompiler.BrachyuraDecompiler;
import io.github.coolcrabs.brachyura.decompiler.DecompileLineNumberTable;
import io.github.coolcrabs.brachyura.decompiler.LineNumberTableReplacer;
import io.github.coolcrabs.brachyura.decompiler.DecompileLineNumberTable.ClassLineMap;
import io.github.coolcrabs.brachyura.dependency.JavaJarDependency;
import io.github.coolcrabs.fernutil.FernUtil;
import net.fabricmc.mappingio.tree.MappingTree;

public class FernflowerDecompiler extends BrachyuraDecompiler {
    JavaJarDependency fernflower;

    public FernflowerDecompiler(JavaJarDependency fernflower) {
        this.fernflower = fernflower;
    }

    @Override
    public String getName() {
        return fernflower.mavenId.groupId + "." + fernflower.mavenId.artifactId;
    }

    @Override
    public String getVersion() {
        return fernflower.mavenId.version;
    }

    @Override
    public int getThreadCount() {
        return -1;
    }

    @Override
    protected void decompileAndLinemap(Path jar, List<Path> classpath, Path resultDir, @Nullable MappingTree tree, int namespace) {
        DecompileResult r = getDecompileResult(jar, resultDir);
        DecompileLineNumberTable table = new DecompileLineNumberTable();
        FernUtil.decompile(fernflower.jar, jar, r.sourcesJar, classpath, l -> {
            if (l.mapping != null) table.classes.put(l.clazz, new ClassLineMap(l.mapping));
        }, tree == null ? null : new FFJavadocProvider(tree, namespace));
        LineNumberTableReplacer.replaceLineNumbers(jar, r.jar, table);
    }
    
}
