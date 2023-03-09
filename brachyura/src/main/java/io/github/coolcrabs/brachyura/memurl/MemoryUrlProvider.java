package io.github.coolcrabs.brachyura.memurl;

import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import io.github.coolcrabs.brachyura.util.UrlUtil;
import io.github.coolcrabs.brachyura.util.Util;

public class MemoryUrlProvider implements AutoCloseable {
    public static final ConcurrentHashMap<String, Function<String, Supplier<InputStream>>> FUNCTIONS = new ConcurrentHashMap<>();
    public static final String PROTOCOL = "memurl";
    static {
        UrlUtil.addHandler(PROTOCOL, new Handler());
    }

    private static final AtomicInteger idSup = new AtomicInteger(0);


    public final String host;

    public MemoryUrlProvider(Function<String, Supplier<InputStream>> func) {
        this.host = "MemUrlProvider" + idSup.getAndIncrement();
        FUNCTIONS.put(host, func);
    }

    public URL getRootUrl() {
        try {
            return new URL(PROTOCOL, host, "/");
        } catch (Exception e) {
            throw Util.sneak(e);
        }
    }

    public static Supplier<InputStream> getStreamSupplier(URL url) {
        return MemoryUrlProvider.FUNCTIONS.get(url.getHost()).apply(stripStartingSlash(url.getPath()));
    }

    @Override
    public void close() {
        FUNCTIONS.remove(this.host);
    }


    public static String stripStartingSlash(String input) {
        return input.charAt(0) == '/' ? input.substring(1) : input;
    }
}
