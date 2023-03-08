package io.github.coolcrabs.brachyura.compiler.java;

import java.io.InputStream;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.function.Supplier;

import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;

import io.github.coolcrabs.brachyura.processing.ProcessingId;
import io.github.coolcrabs.brachyura.processing.ProcessingSink;
import io.github.coolcrabs.brachyura.processing.ProcessingSource;

public class InputFiles implements ProcessingSink {
    public final TreeMap<String, InputFile> files = new TreeMap<>();

    public void add(ProcessingSource s) {
        s.getInputs(this);
    }

    @Override
    public void sink(Supplier<InputStream> in, ProcessingId id) {
        files.put(id.path.replace('/', '.'), new InputFile(in, id));
    }

    Iterator<JavaFileObject> it(String packageName, Set<Kind> kinds, boolean recurse) {
        return new Iterator<JavaFileObject>() {
            public final Iterator<Entry<String, InputFile>> c = files.tailMap(packageName).entrySet().iterator();
            public InputFile next = advance();

            private InputFile advance() {
                while (c.hasNext()) {
                    Entry<String, InputFile> e = c.next();
                    if (!e.getKey().startsWith(packageName)) {
                        return null; // We've gone past this package
                    }
                    if (!recurse && e.getKey().lastIndexOf('/') > packageName.length()) {
                        continue; // Subpackage
                    }
                    if (kinds.contains(e.getValue().getKind())) {
                        return e.getValue();
                    }
                }
                return null;
            }

            public boolean hasNext() {
                return next != null;
            }

            public JavaFileObject next() {
                if (!hasNext()) throw new NoSuchElementException();
                InputFile r = next;
                next = advance();
                return r;
            }
        };
    }
}
