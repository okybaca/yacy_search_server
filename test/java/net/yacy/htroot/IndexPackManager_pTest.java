package net.yacy.htroot;

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

public class IndexPackManager_pTest {
    @Test
    public void loadRowsShowActiveProgressAndSeparateWaitingPacks() throws Exception {
        final serverObjects properties = listing(2);
        properties.put("packs-load_0_json_phase", 1);
        properties.put("packs-load_0_json_active", 1);
        properties.put("packs-load_0_json_job", "current-job");
        properties.put("packs-load_0_json_percent", "50.0");
        properties.put("packs-load_0_json_percent-label", "50");
        properties.put("packs-load_0_json_submitted", "125000");
        properties.put("packs-load_0_json_speed", "2500");
        final String html = render(properties);
        assertTrue(html.contains("data-pack-file=\"pack0.jsonl.gz\""));
        assertTrue(html.contains("data-pack-job=\"current-job\""));
        assertTrue(html.contains("<p data-pack-state=\"true\">Import running</p>"));
        assertTrue(html.contains("<span data-pack-percent=\"true\">50</span>%"));
        assertTrue(html.contains("value=\"50.0\""));
        assertTrue(html.contains("<span data-pack-submitted=\"true\">125000</span>"));
        assertTrue(html.contains("<span data-pack-speed=\"true\">2500</span>"));
        assertTrue(html.contains("<p data-pack-state=\"true\">Waiting for import.</p>"));
        assertTrue(html.contains("data-pack-progress=\"true\" hidden=\"hidden\""));
        assertFalse(html.contains("#["));
    }

    @Test
    public void fullyReadFileCanStillBeDraining() throws Exception {
        final serverObjects properties = listing(1);
        properties.put("packs-load_0_json_phase", 2);
        properties.put("packs-load_0_json_active", 1);
        properties.put("packs-load_0_json_percent", "100.0");
        final String html = render(properties);
        assertTrue(html.contains("<p data-pack-state=\"true\">Processing last documents</p>"));
        assertTrue(html.contains("value=\"100.0\""));
        assertFalse(html.contains("<p data-pack-state=\"true\">Import completed</p>"));
    }

    @Test
    public void packManagerKeepsOnlyRowProgressAndLinksToDetailedImportPage() throws Exception {
        final String html = render(listing(1));
        assertTrue(html.contains("data-pack-progress-row=\"true\""));
        assertTrue(html.contains("href=\"IndexImportJsonList_p.html\">View import activity</a>"));
        assertTrue(html.contains("js/import-monitor.js?v=3"));
        assertFalse(html.contains("id=\"import-monitor\""));
        assertFalse(html.contains("id=\"import-progress\""));
        assertFalse(html.contains("id=\"import-stop\""));
    }

    @Test
    public void otherFormatsLinkToImportActivityWithoutAPercentage() throws Exception {
        final serverObjects properties = listing(1);
        properties.put("packs-load_0_file", "pack.warc.gz");
        properties.put("packs-load_0_json", 0);
        final String html = render(properties);
        assertTrue(html.contains("Queued or processing.</p>"));
        assertTrue(html.contains("href=\"CrawlResults.html?process=7\">View import activity</a>"));
        assertFalse(html.contains("data-pack-progress-row"));
        assertFalse(html.contains("<progress"));
        assertFalse(html.contains("Waiting for import.</p>"));
    }

    @Test
    public void olderBackendWithoutRowPropertiesStillOffersImportActivity() throws Exception {
        final serverObjects properties = listing(1);
        properties.remove("packs-load_0_json");
        final String html = render(properties);
        assertTrue(html.contains("pack0.jsonl.gz"));
        assertTrue(html.contains("View import activity</a>"));
        assertFalse(html.contains("unavailable for this format"));
    }

    @Test
    public void fileNamesAreEscapedInBothTextAndRowIdentity() throws Exception {
        final serverObjects properties = listing(1);
        properties.putHTML("packs-load_0_file", "pack\"&<script>.jsonl.gz");
        final String html = render(properties);
        assertTrue(html.contains("data-pack-file=\"pack&quot;&amp;&lt;script&gt;.jsonl.gz\""));
        assertFalse(html.contains("<script>.jsonl"));
    }

    @Test
    public void emptyLoadPlaceholderDoesNotLookLikeAQueuedPack() throws Exception {
        final serverObjects properties = listing(1);
        properties.putHTML("packs-load_0_file", "<no packs in load>");
        properties.put("packs-load_0_json", 2);
        final String html = render(properties);
        assertTrue(html.contains("&lt;no packs in load&gt;"));
        assertFalse(html.contains("data-pack-progress-row"));
        assertFalse(html.contains("data-pack-activity"));
        assertFalse(html.contains("Waiting for import.</p>"));
    }

    private static serverObjects listing(final int count) {
        final serverObjects properties = new serverObjects();
        properties.put("clientname", "test");
        properties.put("packs-hold", 0);
        properties.put("packs-loaded", 0);
        properties.put("packs-load", count);
        for (int i = 0; i < count; i++) {
            final String prefix = "packs-load_" + i + "_";
            properties.put(prefix + "file", "pack" + i + ".jsonl.gz");
            properties.put(prefix + "size", 20000000);
            properties.put(prefix + "dark", 0);
            properties.put(prefix + "json", 1);
            properties.put(prefix + "json_phase", 0);
            properties.put(prefix + "json_active", 0);
            properties.put(prefix + "json_job", "");
            properties.put(prefix + "json_percent", "0.0");
            properties.put(prefix + "json_percent-label", "0");
            properties.put(prefix + "json_submitted", "0");
            properties.put(prefix + "json_speed", "0");
        }
        return properties;
    }

    private static String render(final serverObjects properties) throws Exception {
        String source = Files.readString(Path.of("htroot/IndexPackManager_p.html"));
        source = source.replace("#%env/templates/importStatus.template%#",
                Files.readString(Path.of("htroot/env/templates/importStatus.template")));
        source = source.replace("#%env/templates/importStates.template%#",
                Files.readString(Path.of("htroot/env/templates/importStates.template")));
        source = source.replaceAll("#%[^%]+%#", "");
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        TemplateEngine.writeTemplate("IndexPackManager_p.html",
                new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)), output, properties);
        return output.toString(StandardCharsets.UTF_8);
    }
}
