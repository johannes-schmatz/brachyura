package io.github.coolcrabs.brachyura.mappings.tinyremapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import io.github.coolcrabs.brachyura.processing.ProcessingCollector;
import io.github.coolcrabs.brachyura.processing.ProcessingEntry;
import io.github.coolcrabs.brachyura.processing.ProcessingSink;
import io.github.coolcrabs.brachyura.processing.Processor;
import io.github.coolcrabs.brachyura.util.ByteArrayOutputStreamEx;

// https://github.com/FabricMC/tiny-remapper/blob/master/src/main/java/net/fabricmc/tinyremapper/MetaInfFixer.java
// Rewritten since tinyremapper's is heavily nio tied atm
public class MetaInfFixer implements Processor {
    final TrWrapper remapper;

    public MetaInfFixer(TrWrapper remapper) {
        this.remapper = remapper;
    }

    @Override
    public void process(ProcessingCollector inputs, ProcessingSink sink) throws IOException {
        for (ProcessingEntry e : inputs.map.values()) {
            if (e.id.path.startsWith("META-INF/")) {
                int si = e.id.path.lastIndexOf('/');
                String fn = si == -1 ? e.id.path : e.id.path.substring(si + 1);
                if (e.id.path.equals("META-INF/MANIFEST.MF")) {
                    Manifest m;
                    try (InputStream i = e.in.get()) {
                        m = new Manifest(i);
                    }
                    fixManifest(m, remapper);
                    ByteArrayOutputStreamEx ex = new ByteArrayOutputStreamEx();
                    m.write(ex);
                    sink.sink(ex::toIs, e.id);
                } else if (e.id.path.startsWith("META-INF/services/")) {
                    ByteArrayOutputStreamEx ex = new ByteArrayOutputStreamEx();
                    try (
                        BufferedReader r = new BufferedReader(new InputStreamReader(e.in.get()));
                        Writer w = new OutputStreamWriter(ex);
                    ) {
                        fixServiceDecl(r, w, remapper);
                    }
                    sink.sink(ex::toIs, e.id);
                } else if (fn.endsWith(".SF") || fn.endsWith(".DSA") || fn.endsWith(".RSA") || fn.startsWith("SIG-")) {
                    // Strip (noop)
                } else {
                    sink.sink(e.in, e.id);
                }
            } else {
                sink.sink(e.in, e.id);
            }
        }
    }

    private static void fixManifest(Manifest manifest, TrWrapper remapper) {
        Attributes mainAttrs = manifest.getMainAttributes();
        if (remapper != null) {
            String val = mainAttrs.getValue(Attributes.Name.MAIN_CLASS);
            if (val != null)
                mainAttrs.put(Attributes.Name.MAIN_CLASS, remapper.mapFullyQualifiedClassName(val));
            String val1 = mainAttrs.getValue("Launcher-Agent-Class");
            if (val1 != null)
                mainAttrs.putValue("Launcher-Agent-Class", remapper.mapFullyQualifiedClassName(val1));
        }
        mainAttrs.remove(Attributes.Name.SIGNATURE_VERSION);
        for (Iterator<Attributes> it = manifest.getEntries().values().iterator(); it.hasNext();) {
            Attributes attrs = it.next();
            for (Iterator<Object> it2 = attrs.keySet().iterator(); it2.hasNext();) {
                Attributes.Name attrName = (Attributes.Name) it2.next();
                String name = attrName.toString();
                if (name.endsWith("-Digest") || name.contains("-Digest-") || name.equals("Magic")) {
                    it2.remove();
                }
            }
            if (attrs.isEmpty()) it.remove();
        }
    }

    private static void fixServiceDecl(BufferedReader reader, Writer writer, TrWrapper remapper) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            int end = line.indexOf('#');
            if (end < 0) end = line.length();
            // trim start+end to skip ' ' and '\t'
            int start = 0;
            char c;
            while (start < end && ((c = line.charAt(start)) == ' ' || c == '\t')) {
                start++;
            }
            while (end > start && ((c = line.charAt(end - 1)) == ' ' || c == '\t')) {
                end--;
            }
            if (start == end) {
                writer.write(line);
            } else {
                writer.write(line, 0, start);
                writer.write(remapper.mapFullyQualifiedClassName(line.substring(start, end)));
                writer.write(line, end, line.length() - end);
            }
            writer.write('\n');
        }
    }
}
