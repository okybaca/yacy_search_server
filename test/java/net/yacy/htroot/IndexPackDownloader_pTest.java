package net.yacy.htroot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

import net.yacy.server.http.TemplateEngine;
import net.yacy.server.serverObjects;

public class IndexPackDownloader_pTest {
    @Test
    public void rendersLargeZeroAndUnknownSizesInTheirOwnTableRows() throws Exception {
        final serverObjects properties = listing(3);
        properties.put("packs-source_0_size", 1);
        properties.put("packs-source_0_size_bytes", 21_474_836_480L);
        properties.put("packs-source_0_size_value", "20.00");
        properties.put("packs-source_0_size_unit", "GiB");
        properties.put("packs-source_1_size", 1);
        properties.put("packs-source_1_size_bytes", 0);
        properties.put("packs-source_1_size_value", "0");
        properties.put("packs-source_1_size_unit", "B");
        final String html = render(properties);
        assertTrue(html.contains("Download size</th>"));
        assertTrue(html.contains("title=\"21474836480 B\">20.00 GiB</span>"));
        assertTrue(html.contains("title=\"0 B\">0 B</span>"));
        assertTrue(html.contains("<td class=\"text-end\">Unknown</td>"));
        assertFalse(html.contains("#["));
    }

    @Test
    public void distinguishesDownloadReadyQueuedProcessedAndOtherLocalPacks() throws Exception {
        final serverObjects properties = listing(5);
        for (int i = 0; i < 5; i++) properties.put("packs-source_" + i + "_state", i);
        properties.putHTML("packs-source_0_state_repoid", "yacy/test&special");
        properties.putHTML("packs-source_0_state_file", "pack \"quoted\".jsonl.gz");
        final String html = render(properties);
        assertEquals(1, html.split("class=\"pack-download-form\"", -1).length - 1);
        assertTrue(html.contains("method=\"post\""));
        assertTrue(html.contains("yacy/test&amp;special"));
        assertTrue(html.contains("pack &quot;quoted&quot;.jsonl.gz"));
        assertTrue(html.contains("Downloaded; ready to import."));
        assertTrue(html.contains("Queued or importing."));
        assertTrue(html.contains("Pack processed."));
        assertTrue(html.contains("Stored locally."));
        assertTrue(html.contains("href=\"IndexPackManager_p.html\">Import in Pack Manager</a>"));
        assertFalse(html.contains("file is downloaded; location:"));
        assertFalse(html.contains("#["));
    }

    @Test
    public void completionStaysVisibleWithImportGuidanceAndEscapedFileName() throws Exception {
        final serverObjects properties = listing(0);
        properties.put("download-complete", 1);
        properties.putHTML("download-complete_file", "<script>alert(1)</script>.jsonl.gz");
        final String html = render(properties);
        assertTrue(html.contains("id=\"pack-download-complete\""));
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;.jsonl.gz"));
        assertTrue(html.contains("Next step: open Pack Manager and select load beside this pack"));
        assertTrue(html.contains("href=\"IndexPackManager_p.html\">Import in Pack Manager</a>"));
        assertFalse(html.contains("http-equiv=\"REFRESH\""));
        assertFalse(html.contains("id=\"pack-download-error\""));
    }

    @Test
    public void serverFailureDoesNotClaimCompletion() throws Exception {
        final serverObjects properties = listing(1);
        properties.put("download-error", 1);
        properties.putHTML("download-error_message", "<failure>");
        final String html = render(properties);
        assertTrue(html.contains("id=\"pack-download-error\""));
        assertTrue(html.contains("&lt;failure&gt;"));
        assertFalse(html.contains("id=\"pack-download-complete\""));
        assertTrue(html.contains("class=\"pack-download-form\""));
    }

    private static serverObjects listing(final int count) {
        final serverObjects properties = new serverObjects();
        properties.put("clientname", "test");
        properties.put("download-complete", 0);
        properties.put("download-error", 0);
        properties.put("packs-source", count);
        for (int i = 0; i < count; i++) {
            final String prefix = "packs-source_" + i + "_";
            properties.put(prefix + "source", "Huggingface");
            properties.put(prefix + "repoid", "yacy/test");
            properties.put(prefix + "repourl", "https://huggingface.co/datasets/yacy/test");
            properties.put(prefix + "file", "pack" + i + ".jsonl.gz");
            properties.put(prefix + "state", 0);
            properties.put(prefix + "state_repoid", "yacy/test");
            properties.put(prefix + "state_file", "pack" + i + ".jsonl.gz");
            properties.put(prefix + "dark", 0);
            properties.put(prefix + "size", 0);
        }
        return properties;
    }

    private static String render(final serverObjects properties) throws Exception {
        final String source = Files.readString(Path.of("htroot/IndexPackDownloader_p.html"))
                .replaceAll("#%[^%]+%#", "");
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        TemplateEngine.writeTemplate("IndexPackDownloader_p.html",
                new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)), output, properties);
        return output.toString(StandardCharsets.UTF_8);
    }
}
