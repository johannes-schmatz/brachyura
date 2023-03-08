package io.github.coolcrabs.brachyura.compiler.java;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import javax.tools.SimpleJavaFileObject;

import io.github.coolcrabs.brachyura.processing.ProcessingId;
import io.github.coolcrabs.brachyura.util.StreamUtil;
import io.github.coolcrabs.brachyura.util.Util;

public class InputFile extends SimpleJavaFileObject {
    public static final Kind[] KINDS = Kind.values();

    public final Supplier<InputStream> in;
    public final String path;

    public InputFile(Supplier<InputStream> in, ProcessingId id) {
        super(uri(id), getKind(id.path));
        this.in = in;
        this.path = id.path;
    }

    public static URI uri(ProcessingId id) {
        try {
            return new URI("crabin", "authority", "/" + id.path, null);
        } catch (URISyntaxException e) {
            throw Util.sneak(e);
        }
    }

    public static Kind getKind(String path) {
        for (Kind k : KINDS) {
            if (path.endsWith(k.extension)) return k;
        }
        return Kind.OTHER;
    }

    @Override
    public InputStream openInputStream() throws IOException {
        return in.get();
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
        return StreamUtil.readFullyAsString(in.get());
    }
}
