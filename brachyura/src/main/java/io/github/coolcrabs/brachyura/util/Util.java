package io.github.coolcrabs.brachyura.util;

public class Util {
    private Util() { }

    @SuppressWarnings("unchecked")
    public static <T extends Throwable> RuntimeException sneak(Throwable t) throws T {
        throw (T)t;
    }

    public static <T extends Throwable> void unsneak() throws T {
        //noop
    }

    public static String getCaller() {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        StackTraceElement callerOfCaller = stack[2];
        return callerOfCaller.toString();
    }
}
