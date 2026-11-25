package net.yacy.document.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import org.apache.solr.common.SolrInputDocument;
import org.junit.Before;
import org.junit.Test;

import net.yacy.cora.document.encoding.ASCII;
import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.storage.Configuration.Entry;
import net.yacy.search.schema.CollectionConfiguration;
import net.yacy.search.schema.CollectionSchema;

public class JsonListImporterTest {

    private CollectionConfiguration configuration;

    @Before
    public void setUp() throws IOException {
        final File schema = File.createTempFile("json-import", ".schema");
        schema.deleteOnExit();
        this.configuration = new CollectionConfiguration(schema, true);
        for (final CollectionSchema field : CollectionSchema.values()) {
            this.configuration.put(field.name(), new Entry(field.name(), false));
        }
        for (final CollectionSchema field : Arrays.asList(
                CollectionSchema.id, CollectionSchema.sku, CollectionSchema.host_s, CollectionSchema.host_id_s,
                CollectionSchema.host_dnc_s, CollectionSchema.host_organization_s,
                CollectionSchema.host_organizationdnc_s, CollectionSchema.host_subdomain_s,
                CollectionSchema.url_chars_i, CollectionSchema.url_protocol_s,
                CollectionSchema.url_paths_sxt, CollectionSchema.url_paths_count_i,
                CollectionSchema.url_file_name_s, CollectionSchema.url_file_name_tokens_t,
                CollectionSchema.url_file_ext_s, CollectionSchema.url_parameter_i,
                CollectionSchema.url_parameter_key_sxt, CollectionSchema.url_parameter_value_sxt)) {
            this.configuration.get(field.name()).setEnable(true);
        }
    }

    @Test
    public void derivesURLMetadataForNewDocuments() throws Exception {
        final String url = "https://www.example.co.uk/legal/design/__31.html?language=de&version=2";
        final SolrInputDocument document = document(url);

        JsonListImporter.addMissingAttributes(document, this.configuration);

        assertEquals(ASCII.String(new DigestURL(url).hash()), value(document, CollectionSchema.id));
        assertEquals(new DigestURL(url).hosthash(), value(document, CollectionSchema.host_id_s));
        assertEquals("www.example.co.uk", value(document, CollectionSchema.host_s));
        assertEquals("co.uk", value(document, CollectionSchema.host_dnc_s));
        assertEquals("example", value(document, CollectionSchema.host_organization_s));
        assertEquals("example.co.uk", value(document, CollectionSchema.host_organizationdnc_s));
        assertEquals("www", value(document, CollectionSchema.host_subdomain_s));
        assertEquals(url.length(), value(document, CollectionSchema.url_chars_i));
        assertEquals("https", value(document, CollectionSchema.url_protocol_s));
        assertEquals(Arrays.asList("legal", "design"), document.getFieldValues(CollectionSchema.url_paths_sxt.getSolrFieldName()));
        assertEquals(2, value(document, CollectionSchema.url_paths_count_i));
        assertEquals("__31", value(document, CollectionSchema.url_file_name_s));
        assertEquals("31", value(document, CollectionSchema.url_file_name_tokens_t));
        assertEquals("html", value(document, CollectionSchema.url_file_ext_s));
        assertEquals(2, value(document, CollectionSchema.url_parameter_i));
        assertEquals(Arrays.asList("language", "version"), document.getFieldValues(CollectionSchema.url_parameter_key_sxt.getSolrFieldName()));
        assertEquals(Arrays.asList("de", "2"), document.getFieldValues(CollectionSchema.url_parameter_value_sxt.getSolrFieldName()));
        assertEquals("Imported text", value(document, CollectionSchema.text_t));
    }

    @Test
    public void derivesContentMetadataFromImportedFields() throws Exception {
        final SolrInputDocument document = document("https://www.example.com/legal/design.html");
        document.setField(CollectionSchema.title.getSolrFieldName(), "Design law");
        document.setField(CollectionSchema.description_txt.getSolrFieldName(), Arrays.asList("First summary", "Second"));
        document.setField(CollectionSchema.h1_txt.getSolrFieldName(), "Main heading");
        document.setField(CollectionSchema.h2_txt.getSolrFieldName(), Arrays.asList("First section", "Second section"));

        JsonListImporter.addMissingAttributes(document, this.configuration);

        assertFalse(this.configuration.contains(CollectionSchema.title_count_i));
        assertEquals(1, value(document, CollectionSchema.title_count_i));
        assertEquals(2, value(document, CollectionSchema.description_count_i));
        assertEquals(2, value(document, CollectionSchema.wordcount_i));
        assertFalse(this.configuration.contains(CollectionSchema.h1_i));
        assertEquals(1, value(document, CollectionSchema.h1_i));
        assertEquals(2, value(document, CollectionSchema.h2_i));
        assertNotNull(value(document, CollectionSchema.exact_signature_l));
        assertNotNull(value(document, CollectionSchema.fuzzy_signature_l));
    }

    @Test
    public void preservesSuppliedMetadata() throws Exception {
        final SolrInputDocument document = document("https://www.example.com/legal/__31.html");
        document.setField(CollectionSchema.id.getSolrFieldName(), "imported-id");
        document.setField(CollectionSchema.url_paths_sxt.getSolrFieldName(), Arrays.asList("supplied", "paths"));
        document.setField(CollectionSchema.url_chars_i.getSolrFieldName(), 123);
        document.setField(CollectionSchema.wordcount_i.getSolrFieldName(), 0);
        document.setField(CollectionSchema.exact_signature_l.getSolrFieldName(), 42L);
        document.setField(CollectionSchema.language_s.getSolrFieldName(), "fr");
        document.setField(CollectionSchema.h1_txt.getSolrFieldName(), "Main heading");
        document.setField(CollectionSchema.h1_i.getSolrFieldName(), 0);

        JsonListImporter.addMissingAttributes(document, this.configuration);

        assertEquals("imported-id", value(document, CollectionSchema.id));
        assertEquals(Arrays.asList("supplied", "paths"), document.getFieldValues(CollectionSchema.url_paths_sxt.getSolrFieldName()));
        assertEquals(123, value(document, CollectionSchema.url_chars_i));
        assertEquals(0, value(document, CollectionSchema.wordcount_i));
        assertEquals(42L, value(document, CollectionSchema.exact_signature_l));
        assertEquals("fr", value(document, CollectionSchema.language_s));
        assertEquals(0, value(document, CollectionSchema.h1_i));
        assertEquals("html", value(document, CollectionSchema.url_file_ext_s));
    }

    @Test
    public void derivesMissingDateCountFromPreservedDateList() throws Exception {
        final SolrInputDocument document = document("https://www.example.com/legal/design.html");
        final Date suppliedDate = new Date(0L);
        document.setField(CollectionSchema.dates_in_content_dts.getSolrFieldName(), Arrays.asList(suppliedDate));

        JsonListImporter.addMissingAttributes(document, this.configuration);

        assertEquals(Arrays.asList(suppliedDate), document.getFieldValues(CollectionSchema.dates_in_content_dts.getSolrFieldName()));
        assertEquals(1, value(document, CollectionSchema.dates_in_content_count_i));
    }

    @Test
    public void derivesZeroDateCountFromPreservedEmptyDateList() throws Exception {
        final SolrInputDocument document = document("https://www.example.com/legal/design.html");
        document.setField(CollectionSchema.text_t.getSolrFieldName(), "Das Gesetz tritt am 1. Januar 2020 in Kraft.");
        document.setField(CollectionSchema.dates_in_content_dts.getSolrFieldName(), Collections.emptyList());

        JsonListImporter.addMissingAttributes(document, this.configuration);

        assertEquals(Collections.emptyList(), document.getFieldValues(CollectionSchema.dates_in_content_dts.getSolrFieldName()));
        assertEquals(0, value(document, CollectionSchema.dates_in_content_count_i));
    }

    @Test
    public void respectsDisabledFields() throws Exception {
        this.configuration.get(CollectionSchema.url_file_name_tokens_t.name()).setEnable(false);
        this.configuration.get(CollectionSchema.host_subdomain_s.name()).setEnable(false);
        final SolrInputDocument document = document("https://www.example.com/legal/__31.html");

        JsonListImporter.addMissingAttributes(document, this.configuration);

        assertFalse(document.containsKey(CollectionSchema.url_file_name_tokens_t.getSolrFieldName()));
        assertFalse(document.containsKey(CollectionSchema.host_subdomain_s.getSolrFieldName()));
        assertFalse(document.containsKey(CollectionSchema.ip_s.getSolrFieldName()));
        assertNotNull(value(document, CollectionSchema.url_paths_sxt));
    }

    @Test
    public void rootURLHasNoDirectoriesOrParameters() throws Exception {
        final SolrInputDocument document = document("https://www.example.com/");

        JsonListImporter.addMissingAttributes(document, this.configuration);

        assertEquals(0, value(document, CollectionSchema.url_paths_count_i));
        assertEquals(0, value(document, CollectionSchema.url_parameter_i));
        assertFalse(document.containsKey(CollectionSchema.url_paths_sxt.getSolrFieldName()));
        assertFalse(document.containsKey(CollectionSchema.url_parameter_key_sxt.getSolrFieldName()));
        assertFalse(document.containsKey(CollectionSchema.url_file_ext_s.getSolrFieldName()));
    }

    private static SolrInputDocument document(final String url) {
        final SolrInputDocument document = new SolrInputDocument();
        document.setField(CollectionSchema.sku.getSolrFieldName(), url);
        document.setField(CollectionSchema.text_t.getSolrFieldName(), "Imported text");
        return document;
    }

    private static Object value(final SolrInputDocument document, final CollectionSchema field) {
        return document.getFieldValue(field.getSolrFieldName());
    }
}
