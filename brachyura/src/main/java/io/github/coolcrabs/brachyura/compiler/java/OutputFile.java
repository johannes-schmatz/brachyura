package io.github.coolcrabs.brachyura.compiler.java;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import javax.tools.FileObject;
import javax.tools.SimpleJavaFileObject;

import io.github.coolcrabs.brachyura.util.ByteArrayOutputStreamEx;
import io.github.coolcrabs.brachyura.util.PathUtil;

class OutputFile extends SimpleJavaFileObject {
    public final ByteArrayOutputStreamEx bytes = new ByteArrayOutputStreamEx();
    public final FileObject sibling;
    private boolean exists = false;

    protected OutputFile(URI uri, Kind kind, FileObject sibling) {
        super(uri, kind);
        this.sibling = sibling;
    }

    URI rawUri() {
        return super.toUri();
    }

    public boolean exists() {
        return exists;
    }

    @Override
    public URI toUri() {
        // https://github.com/SpongePowered/Mixin/blob/1e1aa7fb52dec78630f3f2f53fd70a4c496a7d66/src/ap/java/org/spongepowered/tools/obfuscation/ReferenceManager.java#L158
        // see https://discord.com/channels/844335788384452619/853484478306582538/1083139202541158412
        boolean workaround = false;
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
            if ("org.spongepowered.tools.obfuscation.ReferenceManager".equals(e.getClassName())) {
                workaround = true;
            }
            if ("createResource".equals(e.getMethodName())) {
                return super.toUri();
            }
        }
        // mixin decided it needs to convert the URI into a file to print it, so we need a valid URI that File() accepts, so this is needed
        if (workaround) {
            return PathUtil.CWD.resolve("MIXINBUGWORKAROUND").toFile().toURI();
        }
        return super.toUri();
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
        if (exists) {
            return new String(bytes.buf(), 0, bytes.size(), StandardCharsets.UTF_8);
        }
        throw new IOException(); // Immutables expects a certain error
    }

    @Override
    public InputStream openInputStream() {
        return bytes.toIs();
    }

    @Override
    public OutputStream openOutputStream() {
        exists = true;
        bytes.reset();
        return bytes;
    }
}
