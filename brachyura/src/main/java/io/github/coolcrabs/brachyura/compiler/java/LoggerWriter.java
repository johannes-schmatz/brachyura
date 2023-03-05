package io.github.coolcrabs.brachyura.compiler.java;

import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.tinylog.Logger;

class LoggerWriter extends Writer {
    public final StringBuilder data = new StringBuilder();
    public final List<String> allWrittenLines = new ArrayList<>();

    @Override
    public void write(int c0) {
        synchronized (lock) {
            char c = (char) c0;
            if (c == '\n') {
                String line = data.toString();
                allWrittenLines.add(line);
                Logger.info(line);
                data.setLength(0);
            } else {
                data.append(c);
            }
        }
    }

    @Override
    public void write(char @NotNull [] cbuf, int off, int len) {
        synchronized (lock) {
            for (int i = off; i - off < len; i++) {
                char c = cbuf[i];
                if (c == '\n') {
                    String line = data.toString();
                    allWrittenLines.add(line);
                    Logger.info(line);
                    data.setLength(0);
                } else {
                    data.append(c);
                }
            }
        }
    }

    @Override
    public void flush() {
        // stub
    }

    @Override
    public void close() {
        if (data.length() > 0) {
            Logger.info(data.toString());
        }
    }
}
