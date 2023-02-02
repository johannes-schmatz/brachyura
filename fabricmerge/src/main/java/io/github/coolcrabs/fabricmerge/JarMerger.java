/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
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

package io.github.coolcrabs.fabricmerge;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


public class JarMerger implements AutoCloseable {
    public static final int ASM_VERSION = Opcodes.ASM9;

    public class Entry {
        public final Path path;
        public final BasicFileAttributes metadata;
        public final byte[] data;

        public Entry(Path path, BasicFileAttributes metadata, byte[] data) {
            this.path = path;
            this.metadata = metadata;
            this.data = data;
        }
    }

    private final FileSystem inputClientFs;
    private final FileSystem inputServerFs;
    private final FileSystem outputFs;
    private final Path inputClient;
    private final Path inputServer;
    private final Map<String, Entry> entriesClient;
    private final Map<String, Entry> entriesServer;
    private final Set<String> entriesAll;
    private boolean removeSnowmen = false;
    private boolean offsetSyntheticsParams = false;

    public JarMerger(Path inputClient, Path inputServer, Path output) throws IOException {
        Files.deleteIfExists(output);
        if (!Files.isRegularFile(inputClient)) throw new RuntimeException("Missing client jar");
        if (!Files.isRegularFile(inputServer)) throw new RuntimeException("Missing server jar");

        this.inputClientFs = FileSystemUtil.newJarFileSystem(inputClient);
        this.inputClient = inputClientFs.getPath("/");
        this.inputServerFs = FileSystemUtil.newJarFileSystem(inputServer);
        this.inputServer = inputServerFs.getPath("/");
        this.outputFs = FileSystemUtil.newJarFileSystem(output);

        this.entriesClient = new HashMap<>();
        this.entriesServer = new HashMap<>();
        this.entriesAll = new TreeSet<>();
    }

    public void enableSnowmanRemoval() {
        removeSnowmen = true;
    }

    public void enableSyntheticParamsOffset() {
        offsetSyntheticsParams = true;
    }

    @Override
    public void close() throws IOException {
        inputClientFs.close();
        inputServerFs.close();
        outputFs.close();
    }

    private void readToMap(Map<String, Entry> map, Path input) {
        try {
            Files.walkFileTree(input, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attr) throws IOException {
                    if (attr.isDirectory()) {
                        return FileVisitResult.CONTINUE;
                    }

                    if (!path.getFileName().toString().endsWith(".class")) {
                        if ("/META-INF/MANIFEST.MF".equals(path.toString())) {
                            map.put("META-INF/MANIFEST.MF", new Entry(path, attr,
                                    "Manifest-Version: 1.0\nMain-Class: net.minecraft.client.Main\n".getBytes(StandardCharsets.UTF_8)));
                        } else {
                            if (path.toString().startsWith("/META-INF/") && (path.toString().endsWith(".SF") || path.toString().endsWith(".RSA"))) {
                                return FileVisitResult.CONTINUE;
                            }
                            map.put(path.toString().substring(1), new Entry(path, attr, null));
                        }
                        
                        return FileVisitResult.CONTINUE;
                    }

                    byte[] output = Files.readAllBytes(path);
                    map.put(path.toString().substring(1), new Entry(path, attr, output));
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void add(Entry entry) throws IOException {
        Path outPath = outputFs.getPath(entry.path.toString());
        if (outPath.getParent() != null) {
            Files.createDirectories(outPath.getParent());
        }

        if (entry.data != null) {
            Files.write(outPath, entry.data, StandardOpenOption.CREATE_NEW);
        } else {
            Files.copy(entry.path, outPath);
        }

        Files.getFileAttributeView(entry.path, BasicFileAttributeView.class)
                .setTimes(
                        entry.metadata.creationTime(),
                        entry.metadata.lastAccessTime(),
                        entry.metadata.lastModifiedTime()
                );
    }

    public void merge() throws IOException {
        ExecutorService service = Executors.newFixedThreadPool(2);
        service.submit(() -> readToMap(entriesClient, inputClient));
        service.submit(() -> readToMap(entriesServer, inputServer));
        service.shutdown();
        try {
            service.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        entriesAll.addAll(entriesClient.keySet());
        entriesAll.addAll(entriesServer.keySet());

        List<Entry> entries = entriesAll.parallelStream().map(entry -> {
            boolean isClass = entry.endsWith(".class");
            boolean isMinecraft = entriesClient.containsKey(entry) || entry.startsWith("net/minecraft") || !entry.contains("/");
            Entry result;
            String side = null;

            Entry entry1 = entriesClient.get(entry);
            Entry entry2 = entriesServer.get(entry);

            if (entry1 != null && entry2 != null) {
                if (Arrays.equals(entry1.data, entry2.data)) {
                    result = entry1;
                } else {
                    if (isClass) {
                        result = new Entry(entry1.path, entry1.metadata, ClassMerger.merge(entry1.data, entry2.data));
                    } else {
                        // FIXME: More heuristics?
                        result = entry1;
                    }
                }
            } else if ((result = entry1) != null) {
                side = "CLIENT";
            } else if ((result = entry2) != null) {
                side = "SERVER";
            }

            if (isClass && !isMinecraft && "SERVER".equals(side)) {
                // Server bundles libraries, client doesn't - skip them
                return null;
            }

            if (result != null) {
                if (isMinecraft && isClass) {
                    byte[] data = result.data;
                    ClassReader reader = new ClassReader(data);
                    ClassWriter writer = new ClassWriter(0);
                    ClassVisitor visitor = writer;

                    if (side != null) {
                        visitor = new ClassMerger.SidedClassVisitor(ASM_VERSION, visitor, side);
                    }

                    if (removeSnowmen) {
                        visitor = new SnowmanClassVisitor(ASM_VERSION, visitor);
                    }

                    if (offsetSyntheticsParams) {
                        visitor = new SyntheticParameterClassVisitor(ASM_VERSION, visitor);
                    }

                    if (visitor != writer) {
                        reader.accept(visitor, 0);
                        byte[] data1 = writer.toByteArray();
                        result = new Entry(result.path, result.metadata, data1);
                    }
                }

                return result;
            } else {
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());

        for (Entry e : entries) {
            add(e);
        }
    }
}