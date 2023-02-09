/*
 * Copyright (c) 2020 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.coolcrabs.accesswidener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class AccessWidenerReader {
    public static final Charset ENCODING = StandardCharsets.UTF_8;

    // Also includes some weirdness such as vertical tabs
    private static final Pattern V1_DELIMITER = Pattern.compile("\\s+");
    // Only spaces or tabs
    private static final Pattern V2_DELIMITER = Pattern.compile("[ \\t]+");
    // Prefix used on access types to denote the entry should be inherited by mods depending on this mod
    private static final String TRANSITIVE_PREFIX = "transitive-";

    // Access widener format versions
    private static final int V1 = 1;
    private static final int V2 = 2;

    private final AccessWidenerVisitor visitor;

    private int lineNumber;

    public AccessWidenerReader(AccessWidenerVisitor visitor) {
        this.visitor = visitor;
    }

    public static int readVersion(byte[] content) {
        return readHeader(content).version;
    }

    public static int readVersion(BufferedReader reader) throws IOException {
        return readHeader(reader).version;
    }

    public void read(byte[] content) {
        read(content, null);
    }

    public void read(byte[] content, String currentNamespace) {
        String strContent = new String(content, ENCODING);

        try {
            read(new BufferedReader(new StringReader(strContent)), currentNamespace);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void read(BufferedReader reader) throws IOException {
        read(reader, null);
    }

    public void read(BufferedReader reader, String currentNamespace) throws IOException {
        Header header = readHeader(reader);
        lineNumber = 1;

        int version = header.version;

        visitor.visitVersion(version);

        if (currentNamespace != null && !header.namespace.equals(currentNamespace)) {
            throw new AccessWidenerFormatException(lineNumber,
                    "Namespace (" + header.namespace + ") does not match current runtime namespace (" + currentNamespace + ")"
            );
        }

        visitor.visitHeader(header.namespace);

        String line;

        Pattern delimiter = version < V2 ? V1_DELIMITER : V2_DELIMITER;

        while ((line = reader.readLine()) != null) {
            // we can safely use lineNumber below for errors, since this is first incremented and then used
            // (we are 0 based, but we increment first, AccessWidenerFormatException is 1 based)
            lineNumber++;

            line = handleComment(version, line);

            if (line.isEmpty()) {
                continue;
            }

            if (Character.isWhitespace(line.codePointAt(0))) {
                throw new AccessWidenerFormatException(lineNumber,
                        "Leading whitespace is not allowed"
                );
            }

            // Note that this trims trailing spaces. See the docs of split for details.
            List<String> tokens = Arrays.asList(delimiter.split(line));

            String accessType = tokens.get(0);

            boolean transitive = false;

            if (version >= V2) {
                // transitive access widener flag
                if (accessType.startsWith(TRANSITIVE_PREFIX)) {
                    accessType = accessType.substring(TRANSITIVE_PREFIX.length());
                    transitive = true;
                }
            }

            AccessType access = readAccessType(accessType);

            if (tokens.size() < 2) {
                throw new AccessWidenerFormatException(lineNumber,
                        "Expected <class|field|method> following " + tokens.get(0)
                );
            }

            switch (tokens.get(1)) {
            case "class":
                handleClass(line, tokens, transitive, access);
                break;
            case "field":
                handleField(line, tokens, transitive, access);
                break;
            case "method":
                handleMethod(line, tokens, transitive, access);
                break;
            default:
                throw new AccessWidenerFormatException(lineNumber,
                        "Unsupported type: '" + tokens.get(1) + "'"
                );
            }
        }
    }

    public static Header readHeader(byte[] content) {
        String strContent = new String(content, ENCODING);

        try {
            return readHeader(new BufferedReader(new StringReader(strContent)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Header readHeader(BufferedReader reader) throws IOException {
        String headerLine = reader.readLine();
        String[] header = headerLine.split("\\s+");

        if (header.length != 3 || !header[0].equals("accessWidener")) {
            throw new AccessWidenerFormatException(
                    1,
                    "Invalid access widener file header. Expected: 'accessWidener <version> <namespace>'"
            );
        }

        int version;
        switch (header[1]) {
        case "v1":
            version = V1;
            break;
        case "v2":
            version = V2;
            break;
        default:
            throw new AccessWidenerFormatException(
                    1,
                    "Unsupported access widener format: " + header[1]
            );
        }

        return new Header(version, header[2]);
    }

    private void handleClass(String line, List<String> tokens, boolean transitive, AccessType access) {
        if (tokens.size() != 3) {
            throw new AccessWidenerFormatException(lineNumber,
                    "Expected (<access> class <className>) got (" + line + ")"
            );
        }

        String name = tokens.get(2);
        validateClassName(name);

        try {
            visitor.visitClass(name, access, transitive);
        } catch (Exception e) {
            throw new AccessWidenerFormatException(lineNumber, e);
        }
    }

    private void handleField(String line, List<String> tokens, boolean transitive, AccessType access) {
        if (tokens.size() != 5) {
            throw new AccessWidenerFormatException(lineNumber,
                    "Expected (<access> field <className> <fieldName> <fieldDesc>) got (" + line + ")"
            );
        }

        String owner = tokens.get(2);
        String fieldName = tokens.get(3);
        String descriptor = tokens.get(4);

        validateClassName(owner);

        try {
            visitor.visitField(owner, fieldName, descriptor, access, transitive);
        } catch (Exception e) {
            throw new AccessWidenerFormatException(lineNumber, e);
        }
    }

    private void handleMethod(String line, List<String> tokens, boolean transitive, AccessType access) {
        if (tokens.size() != 5) {
            throw new AccessWidenerFormatException(lineNumber,
                    "Expected (<access> method <className> <methodName> <methodDesc>) got (" + line + ")"
            );
        }

        String owner = tokens.get(2);
        String methodName = tokens.get(3);
        String descriptor = tokens.get(4);

        validateClassName(owner);

        try {
            visitor.visitMethod(owner, methodName, descriptor, access, transitive);
        } catch (Exception e) {
            throw new AccessWidenerFormatException(lineNumber, e);
        }
    }

    private String handleComment(int version, String line) {
        //Comment handling
        int commentPos = line.indexOf('#');

        if (commentPos >= 0) {
            line = line.substring(0, commentPos);

            // In V1, trimming led to leading whitespace being tolerated
            // The tailing whitespace is already stripped by the split below
            if (version <= V1) {
                line = line.trim();
            }
        }

        return line;
    }

    private AccessType readAccessType(String access) {
        switch (access.toLowerCase(Locale.ROOT)) {
        case "accessible":
            return AccessType.ACCESSIBLE;
        case "extendable":
            return AccessType.EXTENDABLE;
        case "mutable":
            return AccessType.MUTABLE;
        default:
            throw new AccessWidenerFormatException(lineNumber,
                    "Unknown access type: " + access
            );
        }
    }

    public enum AccessType {
        ACCESSIBLE("accessible"),
        EXTENDABLE("extendable"),
        MUTABLE("mutable");

        private final String id;

        AccessType(String id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return id;
        }
    }

    private void validateClassName(String className) {
        // Common mistake is using periods to separate packages/class names
        if (className.contains(".")) {
            throw new AccessWidenerFormatException(lineNumber,
                    "Class-names must be specified as a/b/C, not a.b.C, but found: " + className
            );
        }
    }

    public static class Header {
        private final int version;
        private final String namespace;

        Header(int version, String namespace) {
            this.version = version;
            this.namespace = namespace;
        }

        public int getVersion() {
            return version;
        }

        public String getNamespace() {
            return namespace;
        }
    }
}
