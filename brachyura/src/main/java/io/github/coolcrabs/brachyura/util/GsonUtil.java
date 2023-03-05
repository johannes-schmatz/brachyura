package io.github.coolcrabs.brachyura.util;

import java.io.*;
import java.nio.charset.StandardCharsets;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.coolcrabs.brachyura.processing.ProcessingEntry;

public class GsonUtil {
    private GsonUtil() { }

    public static InputStream toIs(JsonElement e, Gson g) {
        ByteArrayOutputStreamEx os = new ByteArrayOutputStreamEx();
        try {
            try (OutputStreamWriter w = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                g.toJson(e, w);
            }
        } catch (IOException e0) {
            // Shouldn't be possible
            throw Util.sneak(e0);
        }
        return os.toIs();
    }

    public static JsonObject fromJson(ProcessingEntry entry, Gson gson) throws IOException {
        return fromJson(entry.in.get(), gson);
    }

    public static JsonObject fromJson(InputStream inputStream, Gson gson) throws IOException {
        JsonObject jsonObject;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            jsonObject = gson.fromJson(reader, JsonObject.class);
        }
        return jsonObject;
    }
}
