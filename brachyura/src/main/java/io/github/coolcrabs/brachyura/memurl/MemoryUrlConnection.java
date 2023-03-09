package io.github.coolcrabs.brachyura.memurl;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Objects;
import java.util.function.Supplier;

public class MemoryUrlConnection extends URLConnection {
    private Supplier<InputStream> in = null;

    public MemoryUrlConnection(URL url) {
        super(url);
    }

    @Override
    public void connect() throws IOException {
        if (in == null) {
            in = Objects.requireNonNull(MemoryUrlProvider.getStreamSupplier(url));
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return in.get();
    }

}
