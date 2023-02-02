package io.github.coolcrabs.brachyura.minecraft;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jetbrains.annotations.Nullable;

import io.github.coolcrabs.brachyura.util.OsUtil;

public class VersionMeta {
    public final String version;
    JsonElement json;

    VersionMeta(Reader reader) {
        json = JsonParser.parseReader(reader);
        this.version = json.getAsJsonObject().get("id").getAsString();
    }

    public VMDownload getDownload(String download) {
        return (new Gson()).fromJson(json.getAsJsonObject().get("downloads").getAsJsonObject().get(download), VMDownload.class);
    }

    public List<VMDependency> getDependencies() {
        Gson gson = new Gson();
        LinkedHashMap<String, VMDependency> result = new LinkedHashMap<>();
        JsonArray libraries = json.getAsJsonObject().get("libraries").getAsJsonArray();
        for (int i = 0; i < libraries.size(); i++) {
            JsonObject library = libraries.get(i).getAsJsonObject();
            if (!Rules.allowed(library.get("rules"))) continue;
            VMDependency dependency = result.computeIfAbsent(library.get("name").getAsString(), VMDependency::new);
            boolean hasNatives = false;
            String natives = null;
            if (library.get("natives") != null) {
                JsonElement native0 = library.get("natives").getAsJsonObject().get(OsUtil.OS.mojang);
                if (native0 != null) {
                    hasNatives = true;
                    natives = native0.getAsString();
                }
            }
            JsonObject downloads = library.get("downloads").getAsJsonObject();
            if (downloads.get("artifact") != null) {
                dependency.artifact = gson.fromJson(downloads.get("artifact"), VMDependencyDownload.class);
            }
            if (downloads.get("classifiers") != null) {
                JsonObject classifiers = downloads.get("classifiers").getAsJsonObject();
                if (hasNatives) {
                    dependency.natives = gson.fromJson(classifiers.get(natives), VMDependencyDownload.class);
                }
                if (classifiers.get("sources") != null) {
                    dependency.sources = gson.fromJson(classifiers.get("sources"), VMDependencyDownload.class);
                }
            }
        }
        return new ArrayList<>(result.values());
    }

    VMAssets getVmAssets() {
        return new Gson().fromJson(json.getAsJsonObject().get("assetIndex"), VMAssets.class);
    }

    public static class VMAssets {
        public String id;
        public String sha1;
        public int size;
        public int totalSize;
        public String url;
    }

    public static class VMDownload {
        public String sha1;
        public int size;
        public String url;
    }

    public static class VMDependencyDownload {
        public String path;
        public String sha1;
        public String size;
        public String url;
    }

    public static class VMDependency {
        public String name;
        public @Nullable VMDependencyDownload artifact;
        public @Nullable VMDependencyDownload natives;
        public @Nullable VMDependencyDownload sources;

        VMDependency(String name) {
            this.name = name;
        }
    }
}
