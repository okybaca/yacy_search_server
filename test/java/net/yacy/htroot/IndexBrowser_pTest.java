package net.yacy.htroot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.solr.common.SolrDocument;
import org.junit.Test;

import net.yacy.search.schema.CollectionSchema;

/** Focused coverage for browser metadata that may be absent in imported documents. */
public class IndexBrowser_pTest {

    @Test
    public void missingMetadataCanBeRenderedWithoutFetchingReferenceReports() {
        final IndexBrowser_p.InfoCacheEntry entry = metadata(new SolrDocument());

        assertEquals("crawldepth: 0, refs: 0 hosts, 0 ext, 0 int", entry.toString());
        assertTrue(entry.references_internal_urls.isEmpty());
        assertTrue(entry.references_external_urls.isEmpty());
    }

    @Test
    public void negativeCountersDoNotAppearInRenderedMetadata() {
        final SolrDocument document = new SolrDocument();
        document.setField(CollectionSchema.crawldepth_i.getSolrFieldName(), -1);
        document.setField(CollectionSchema.references_i.getSolrFieldName(), -1);
        document.setField(CollectionSchema.references_internal_i.getSolrFieldName(), -1);
        document.setField(CollectionSchema.references_external_i.getSolrFieldName(), -1);
        document.setField(CollectionSchema.references_exthosts_i.getSolrFieldName(), -1);

        assertEquals("crawldepth: 0, refs: 0 hosts, 0 ext, 0 int", metadata(document).toString());
    }

    @Test
    public void availableCountersAndUnknownCrawlDepthAreRendered() {
        final SolrDocument document = new SolrDocument();
        document.setField(CollectionSchema.crawldepth_i.getSolrFieldName(), 998);
        document.setField(CollectionSchema.references_i.getSolrFieldName(), 12);
        document.setField(CollectionSchema.references_internal_i.getSolrFieldName(), 7);
        document.setField(CollectionSchema.references_external_i.getSolrFieldName(), 5);
        document.setField(CollectionSchema.references_exthosts_i.getSolrFieldName(), 3);

        assertEquals("unknown crawldepth, refs: 3 hosts, 5 ext, 7 int", metadata(document).toString());
    }

    private static IndexBrowser_p.InfoCacheEntry metadata(final SolrDocument document) {
        return new IndexBrowser_p.InfoCacheEntry(null, null, document, "000000000000", false);
    }
}
