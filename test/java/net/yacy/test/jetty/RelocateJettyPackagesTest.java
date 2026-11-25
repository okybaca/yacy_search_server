package net.yacy.test.jetty;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;

public class RelocateJettyPackagesTest {

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void relocatesModuleNamesPackagesAndServiceDeclarations() throws IOException {
        final ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V11, Opcodes.ACC_MODULE, "module-info", null, null, null);
        final ModuleVisitor module = writer.visitModule("org.eclipse.jetty.client", 0, "10.0.26");
        module.visitRequire("java.base", Opcodes.ACC_MANDATED, null);
        module.visitRequire("org.eclipse.jetty.io", 0, null);
        module.visitPackage("org/eclipse/jetty/client");
        module.visitExport("org/eclipse/jetty/client", 0, "org.eclipse.jetty.io");
        module.visitOpen("org/eclipse/jetty/client", 0, "org.eclipse.jetty.io");
        module.visitUse("org/eclipse/jetty/io/ssl/ALPNProcessor$Client");
        module.visitProvide("org/eclipse/jetty/io/ssl/ALPNProcessor$Client", "org/eclipse/jetty/client/Client");
        module.visitMainClass("org/eclipse/jetty/client/Client");
        module.visitEnd();
        writer.visitEnd();

        final Path input = archive(Map.of("module-info.class", writer.toByteArray()));
        final Path output = input.resolveSibling("relocated.jar");
        RelocateJettyPackages.main(new String[] {input.toString(), output.toString()});
        try (JarFile jar = new JarFile(output.toFile());
                InputStream stream = jar.getInputStream(jar.getJarEntry("module-info.class"))) {
            final ModuleDescriptor descriptor = ModuleDescriptor.read(ByteBuffer.wrap(stream.readAllBytes()));
            assertEquals("net.yacy.solr9.jetty.client", descriptor.name());
            assertEquals(Set.of("net.yacy.solr9.jetty.client"), descriptor.packages());
            assertTrue(descriptor.requires().stream().anyMatch(required -> required.name().equals("net.yacy.solr9.jetty.io")));
            assertTrue(descriptor.requires().stream().anyMatch(required -> required.name().equals("java.base")));
            final ModuleDescriptor.Exports exported = descriptor.exports().iterator().next();
            assertEquals("net.yacy.solr9.jetty.client", exported.source());
            assertEquals(Set.of("net.yacy.solr9.jetty.io"), exported.targets());
            final ModuleDescriptor.Opens opened = descriptor.opens().iterator().next();
            assertEquals("net.yacy.solr9.jetty.client", opened.source());
            assertEquals(Set.of("net.yacy.solr9.jetty.io"), opened.targets());
            assertEquals(Set.of("net.yacy.solr9.jetty.io.ssl.ALPNProcessor$Client"), descriptor.uses());
            final ModuleDescriptor.Provides provided = descriptor.provides().iterator().next();
            assertEquals("net.yacy.solr9.jetty.io.ssl.ALPNProcessor$Client", provided.service());
            assertEquals("net.yacy.solr9.jetty.client.Client", provided.providers().get(0));
            assertEquals("net.yacy.solr9.jetty.client.Client", descriptor.mainClass().orElseThrow());
        }
        VerifyJettyRelocation.main(new String[] {output.toString()});
    }

    @Test
    public void relocatesFoldedManifestAttributesAndSectionNames() throws IOException {
        final String content = "Manifest-Version: 1.0\r\n"
                + "Bundle-SymbolicName: org.eclipse.jet\r\n ty.client\r\n"
                + "Import-Package: java.lang,org.eclipse.jet\r\n ty.io;version=\"10.0.26\"\r\n\r\n"
                + "Name: org/eclipse/jetty/client/Client.class\r\n"
                + "Provider-Class: org.eclipse.jetty.client.Client\r\n\r\n";
        final Path input = archive(Map.of(JarFile.MANIFEST_NAME, content.getBytes(StandardCharsets.UTF_8)));
        final Path output = input.resolveSibling("relocated.jar");
        RelocateJettyPackages.main(new String[] {input.toString(), output.toString()});
        try (JarFile jar = new JarFile(output.toFile())) {
            final Manifest manifest = jar.getManifest();
            assertEquals("net.yacy.solr9.jetty.client", manifest.getMainAttributes().getValue("Bundle-SymbolicName"));
            assertEquals("java.lang,net.yacy.solr9.jetty.io;version=\"10.0.26\"",
                    manifest.getMainAttributes().getValue("Import-Package"));
            assertEquals("1.0", manifest.getMainAttributes().getValue(Attributes.Name.MANIFEST_VERSION));
            assertNull(manifest.getAttributes("org/eclipse/jetty/client/Client.class"));
            assertEquals("net.yacy.solr9.jetty.client.Client", manifest
                    .getAttributes("net/yacy/solr9/jetty/client/Client.class").getValue("Provider-Class"));
        }
        VerifyJettyRelocation.main(new String[] {output.toString()});
    }

    @Test
    public void relocatesClassAndServiceLinkageWhilePreservingOriginalAttribution() throws IOException {
        final byte[] notice = "Copyright Eclipse Jetty Project: org.eclipse.jetty\n".getBytes(StandardCharsets.UTF_8);
        final byte[] coordinates = "groupId=org.eclipse.jetty\nversion=10.0.26\n".getBytes(StandardCharsets.UTF_8);
        final String pom = "META-INF/maven/org.eclipse.jetty/jetty-client/pom.properties";
        final Path input = archive(Map.of(
                "org/eclipse/jetty/client/Client.class", clientClass(),
                "META-INF/services/org.eclipse.jetty.io.ssl.ALPNProcessor$Client",
                "org.eclipse.jetty.client.Client\n".getBytes(StandardCharsets.UTF_8),
                "META-INF/NOTICE.txt", notice, pom, coordinates,
                "META-INF/ORIGINAL.SF", new byte[] {1}, "META-INF/ORIGINAL.RSA", new byte[] {2}));
        final Path output = input.resolveSibling("relocated.jar");
        RelocateJettyPackages.main(new String[] {input.toString(), output.toString()});
        try (JarFile jar = new JarFile(output.toFile())) {
            assertNull(jar.getJarEntry("org/eclipse/jetty/client/Client.class"));
            final JarEntry client = jar.getJarEntry("net/yacy/solr9/jetty/client/Client.class");
            assertNotNull(client);
            try (InputStream stream = jar.getInputStream(client)) {
                final byte[] bytes = stream.readAllBytes();
                assertEquals("net/yacy/solr9/jetty/client/Client", new ClassReader(bytes).getClassName());
                assertTrue(new String(bytes, StandardCharsets.ISO_8859_1).contains("net.yacy.solr9.jetty.io.Client"));
            }
            try (InputStream stream = jar.getInputStream(jar.getJarEntry(
                    "META-INF/services/net.yacy.solr9.jetty.io.ssl.ALPNProcessor$Client"))) {
                assertEquals("net.yacy.solr9.jetty.client.Client\n", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
            try (InputStream stream = jar.getInputStream(jar.getJarEntry("META-INF/NOTICE.txt"))) {
                assertArrayEquals(notice, stream.readAllBytes());
            }
            try (InputStream stream = jar.getInputStream(jar.getJarEntry(pom))) {
                assertArrayEquals(coordinates, stream.readAllBytes());
            }
            assertNull(jar.getJarEntry("META-INF/ORIGINAL.SF"));
            assertNull(jar.getJarEntry("META-INF/ORIGINAL.RSA"));
        }
        VerifyJettyRelocation.main(new String[] {output.toString()});
    }

    @Test
    public void verifierRejectsRuntimeReferencesIncludingFoldedManifestsAndServiceNames() throws IOException {
        final Map<String, byte[]> fixtures = Map.of(
                "net/yacy/solr9/jetty/client/Client.class", clientClass(),
                "client.xml", "<Configure class=\"org.eclipse.jetty.io.Client\"/>".getBytes(StandardCharsets.UTF_8),
                "META-INF/services/org.eclipse.jetty.io.Client", "net.yacy.solr9.jetty.io.Client\n".getBytes(StandardCharsets.UTF_8),
                JarFile.MANIFEST_NAME, "Manifest-Version: 1.0\r\nMain-Class: org.eclipse.jet\r\n ty.client.Client\r\n\r\n"
                        .getBytes(StandardCharsets.UTF_8));
        for (final Map.Entry<String, byte[]> fixture : fixtures.entrySet()) {
            final Path jar = archive(Map.of(fixture.getKey(), fixture.getValue()));
            final IOException error = assertThrows(IOException.class,
                    () -> VerifyJettyRelocation.main(new String[] {jar.toString()}));
            assertTrue(error.getMessage(), error.getMessage().contains("!" + fixture.getKey()));
        }
    }

    @Test
    public void verifierRejectsUnreadableArchives() throws IOException {
        final Path jar = temporary.newFile("broken.jar").toPath();
        Files.writeString(jar, "not a jar");
        assertThrows(IOException.class, () -> VerifyJettyRelocation.main(new String[] {jar.toString()}));
    }

    private Path archive(final Map<String, byte[]> entries) throws IOException {
        final Path path = temporary.newFolder().toPath().resolve("input.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
            for (final Map.Entry<String, byte[]> entry : entries.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return path;
    }

    private static byte[] clientClass() {
        final ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, "org/eclipse/jetty/client/Client", null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC, "connection", "Lorg/eclipse/jetty/io/Client;", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "reflectiveType", "Ljava/lang/String;", null, "org.eclipse.jetty.io.Client").visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
