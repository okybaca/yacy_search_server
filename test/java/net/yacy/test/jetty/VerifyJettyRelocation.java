/* Verify runtime linkage in the private Solr 9 Jetty bridge using only the JDK. */
package net.yacy.test.jetty;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class VerifyJettyRelocation {

    private VerifyJettyRelocation() {
    }

    public static void main(final String[] args) throws IOException {
        if (args.length == 0) throw new IllegalArgumentException("usage: VerifyJettyRelocation ARCHIVE.jar...");
        for (final String archive : args) {
            try (JarFile jar = new JarFile(archive)) {
                final Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    final JarEntry entry = entries.nextElement();
                    final String name = entry.getName();
                    final boolean manifest = JarFile.MANIFEST_NAME.equalsIgnoreCase(name);
                    // Original Maven coordinates and license/NOTICE attribution are not runtime linkage.
                    final String basename = name.substring(name.lastIndexOf('/') + 1).toUpperCase(Locale.ROOT);
                    if (entry.isDirectory() || name.startsWith("META-INF/maven/")
                            || basename.equals("LICENSE") || basename.startsWith("LICENSE.")
                            || basename.equals("NOTICE") || basename.startsWith("NOTICE.")) continue;
                    if (name.contains("org/eclipse/jetty/") || name.startsWith("META-INF/services/org.eclipse.jetty")) {
                        throw new IOException("unrelocated Jetty entry in " + archive + "!" + name);
                    }
                    try (InputStream stream = jar.getInputStream(entry)) {
                        String content = new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
                        if (manifest) content = content.replace("\r\n ", "").replace("\n ", "");
                        if (content.contains("org/eclipse/jetty") || content.contains("org.eclipse.jetty")) {
                            throw new IOException("unrelocated Jetty reference in " + archive + "!" + name);
                        }
                    }
                }
            }
        }
    }
}
