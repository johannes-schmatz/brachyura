package io.github.coolcrabs.brachyura.util;

import java.util.HashMap;
import java.util.Locale;

public class OsUtil {
    private OsUtil() { }

    public static final Os OS;
    public static final String OS_VERSION = System.getProperty("os.version");

    private static final HashMap<String, Os> osMap = new HashMap<>();

    public enum Os {
        LINUX("linux"),
        OSX("osx"),
        WINDOWS("windows");

        public final String mojang;
        
        private Os(String mojang) {
            this.mojang = mojang;
            osMap.put(mojang, this);
        }

        public static Os fromMojang(String mojang) {
            return osMap.get(mojang);
        }
    }

    // https://stackoverflow.com/a/18417382
    static {
        String osString = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);
        if ((osString.contains("mac")) || (osString.contains("darwin"))) {
            OS = Os.OSX;
        } else if (osString.contains("win")) {
            OS = Os.WINDOWS;
        } else if (osString.contains("nux")) {
            OS = Os.LINUX;
        } else {
            throw new UnknownOsException(); // Minecraft requires natives so knowing the os is required
        }
    }

    public static class UnknownOsException extends RuntimeException {

    }
}
