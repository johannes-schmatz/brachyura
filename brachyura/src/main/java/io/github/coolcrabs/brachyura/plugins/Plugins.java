package io.github.coolcrabs.brachyura.plugins;

import java.util.ArrayList;
import java.util.List;

import io.github.coolcrabs.brachyura.profiler.ProfilePlugin;

public class Plugins {
    private Plugins() { }

    public static final ArrayList<Plugin> PLUGINS = new ArrayList<>();
    
    static {
        PLUGINS.add(ProfilePlugin.INSTANCE); // TODO: real plugin loading
    }

    public static List<Plugin> getPlugins() {
        return new ArrayList<>(PLUGINS);
    }
}
