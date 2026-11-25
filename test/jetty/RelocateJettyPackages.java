/*
 * Build-time bytecode relocator for the Jetty 12 migration.
 *
 * This is deliberately a small build tool, not YaCy runtime code. It keeps
 * Solr's public packages unchanged while moving its private Jetty 10 linkage
 * below net.yacy.solr9.jetty.
 */
package net.yacy.test.jetty;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

public final class RelocateJettyPackages {

    private static final String SOURCE_INTERNAL = "org/eclipse/jetty";
    private static final String TARGET_INTERNAL = "net/yacy/solr9/jetty";
    private static final String SOURCE_BINARY = "org.eclipse.jetty";
    private static final String TARGET_BINARY = "net.yacy.solr9.jetty";

    private RelocateJettyPackages() {
    }

    public static void main(final String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: RelocateJettyPackages INPUT.jar OUTPUT.jar");
        }
        relocate(Path.of(args[0]), Path.of(args[1]));
    }

    private static void relocate(final Path input, final Path output) throws IOException {
        Files.createDirectories(output.toAbsolutePath().getParent());
        final Remapper remapper = new BridgeRemapper();

        try (JarFile source = new JarFile(input.toFile());
                JarOutputStream target = new JarOutputStream(Files.newOutputStream(output))) {
            final Enumeration<JarEntry> entries = source.entries();
            while (entries.hasMoreElements()) {
                final JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                final String upperName = entry.getName().toUpperCase(Locale.ROOT);
                if (upperName.startsWith("META-INF/")
                        && (upperName.endsWith(".SF") || upperName.endsWith(".RSA") || upperName.endsWith(".DSA"))) {
                    continue;
                }
                final String outputName = relocateEntryName(entry.getName());
                final JarEntry outputEntry = new JarEntry(outputName);
                outputEntry.setTime(entry.getTime());
                target.putNextEntry(outputEntry);
                try (InputStream stream = source.getInputStream(entry)) {
                    final byte[] content = stream.readAllBytes();
                    if (entry.getName().endsWith(".class")) {
                        final ClassReader reader = new ClassReader(content);
                        final ClassWriter writer = new ClassWriter(0);
                        reader.accept(new ClassRemapper(writer, remapper), 0);
                        target.write(writer.toByteArray());
                    } else if (upperName.equals(JarFile.MANIFEST_NAME)) {
                        // Parse and reserialize: manifest folding can split a package name across lines.
                        final Manifest manifest = new Manifest(new ByteArrayInputStream(content));
                        relocateAttributes(manifest.getMainAttributes());
                        final Map<String, Attributes> sections = new LinkedHashMap<>(manifest.getEntries());
                        manifest.getEntries().clear();
                        for (final Map.Entry<String, Attributes> section : sections.entrySet()) {
                            relocateAttributes(section.getValue());
                            manifest.getEntries().put(relocateEntryName(section.getKey()), section.getValue());
                        }
                        manifest.write(target);
                    } else if (entry.getName().startsWith("META-INF/services/")) {
                        target.write(relocateText(new String(content, StandardCharsets.UTF_8))
                                .getBytes(StandardCharsets.UTF_8));
                    } else {
                        target.write(content);
                    }
                }
                target.closeEntry();
            }
        }
    }

    private static final class BridgeRemapper extends Remapper {

        private BridgeRemapper() {
            super(Opcodes.ASM9);
        }

        @Override
        public String map(final String internalName) {
            return relocateInternalName(internalName);
        }

        @Override
        public String mapPackageName(final String name) {
            return relocateInternalName(name);
        }

        @Override
        public String mapModuleName(final String name) {
            if (name.equals(SOURCE_BINARY) || name.startsWith(SOURCE_BINARY + ".")) {
                return TARGET_BINARY + name.substring(SOURCE_BINARY.length());
            }
            return name;
        }

        @Override
        public Object mapValue(final Object value) {
            if (value instanceof String) {
                final String text = (String) value;
                return relocateText(text);
            }
            return super.mapValue(value);
        }
    }

    private static void relocateAttributes(final Attributes attributes) {
        for (final Map.Entry<Object, Object> attribute : attributes.entrySet()) {
            attribute.setValue(relocateText((String) attribute.getValue()));
        }
    }

    private static String relocateEntryName(final String name) {
        final String internalName = relocateInternalName(name);
        if (internalName.startsWith("META-INF/services/")) {
            return relocateText(internalName);
        }
        return internalName;
    }

    private static String relocateInternalName(final String name) {
        if (name.equals(SOURCE_INTERNAL) || name.startsWith(SOURCE_INTERNAL + "/")) {
            return TARGET_INTERNAL + name.substring(SOURCE_INTERNAL.length());
        }
        return name;
    }

    private static String relocateText(final String text) {
        return text.replace(SOURCE_BINARY, TARGET_BINARY).replace(SOURCE_INTERNAL, TARGET_INTERNAL);
    }
}
