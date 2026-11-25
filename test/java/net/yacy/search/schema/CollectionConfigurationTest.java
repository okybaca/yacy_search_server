package net.yacy.search.schema;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import org.apache.solr.common.SolrInputDocument;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.cybozu.labs.langdetect.DetectorFactory;

import net.yacy.cora.document.id.DigestURL;
import net.yacy.cora.storage.Configuration.Entry;
import net.yacy.document.Condenser;
import net.yacy.document.Document;

public class CollectionConfigurationTest {

    private static boolean loadedProfiles;
    private CollectionConfiguration configuration;
    private DigestURL url;

    @BeforeClass
    public static void loadLanguageProfiles() throws Exception {
        if (DetectorFactory.getLangList().isEmpty()) {
            DetectorFactory.loadProfile("langdetect");
            loadedProfiles = true;
        }
    }

    @AfterClass
    public static void releaseLanguageProfiles() {
        if (loadedProfiles) DetectorFactory.clear();
    }

    @Before
    public void setUp() throws Exception {
        final File schema = File.createTempFile("collection-attributes", ".schema");
        schema.deleteOnExit();
        this.configuration = new CollectionConfiguration(schema, true);
        for (final CollectionSchema field : CollectionSchema.values()) {
            this.configuration.put(field.name(), new Entry(field.name(), false));
        }
        this.url = new DigestURL("https://www.example.com/legal/design.html");
    }

    @Test
    public void derivesTitleAndDescriptionStatisticsWithDisabledFields() {
        final SolrInputDocument document = new SolrInputDocument();
        document.setField(CollectionSchema.title.getSolrFieldName(), Arrays.asList("Design law", "Alternate design"));
        document.setField(CollectionSchema.description_txt.getSolrFieldName(), Arrays.asList("First summary", "Second"));

        this.configuration.addURIAttributes(document, false, this.url);

        assertFalse(this.configuration.contains(CollectionSchema.title_count_i));
        assertEquals(2, value(document, CollectionSchema.title_count_i));
        assertEquals(Arrays.asList(10, 16), document.getFieldValues(CollectionSchema.title_chars_val.getSolrFieldName()));
        assertEquals(Arrays.asList(2, 2), document.getFieldValues(CollectionSchema.title_words_val.getSolrFieldName()));
        assertEquals(2, value(document, CollectionSchema.description_count_i));
        assertEquals(Arrays.asList(13, 6), document.getFieldValues(CollectionSchema.description_chars_val.getSolrFieldName()));
        assertEquals(Arrays.asList(2, 1), document.getFieldValues(CollectionSchema.description_words_val.getSolrFieldName()));
        assertNotNull(value(document, CollectionSchema.title_exact_signature_l));
        assertNotNull(value(document, CollectionSchema.description_exact_signature_l));
    }

    @Test
    public void usesTheSameTextAnalysisAsTheCondenser() {
        final String text = "Das Gesetz tritt am 1. Januar 2020 in Kraft. "
                + "Die Rechte des Eigentümers werden durch dieses Gesetz geschützt. "
                + "Eine Lizenz kann für das gesamte Gebiet oder einen Teil des Gebiets erteilt werden. "
                + "Die Parteien vereinbaren die Dauer der Nutzung und die Art der geschützten Erzeugnisse.";
        final Document source = new Document(this.url, "text/plain", "UTF-8", null, null, null,
                null, null, null, null, null, 0.0, 0.0, text, null, null, null, false, null);
        final Condenser condenser = new Condenser(source, null, true, false, null, false, true, 0);
        final SolrInputDocument document = new SolrInputDocument();
        document.setField(CollectionSchema.text_t.getSolrFieldName(), text);

        this.configuration.addTextAttributes(document);

        assertFalse(this.configuration.contains(CollectionSchema.exact_signature_l));
        assertEquals(condenser.exactSignature(), value(document, CollectionSchema.exact_signature_l));
        assertEquals(condenser.fuzzySignature(), value(document, CollectionSchema.fuzzy_signature_l));
        assertEquals(condenser.fuzzySignatureText(), value(document, CollectionSchema.fuzzy_signature_text_t));
        assertEquals("de", value(document, CollectionSchema.language_s));
        assertEquals(condenser.dates_in_content.size(), value(document, CollectionSchema.dates_in_content_count_i));
        assertEquals(Arrays.asList(condenser.dates_in_content.toArray()),
                document.getFieldValues(CollectionSchema.dates_in_content_dts.getSolrFieldName()));
        assertEquals(text.split(" ", -1).length, value(document, CollectionSchema.wordcount_i));
    }

    @Test
    public void preservesSuppliedContentMetadata() {
        final SolrInputDocument document = new SolrInputDocument();
        document.setField(CollectionSchema.text_t.getSolrFieldName(), "Imported document text");
        document.setField(CollectionSchema.title.getSolrFieldName(), "Design law");
        document.setField(CollectionSchema.wordcount_i.getSolrFieldName(), 99);
        document.setField(CollectionSchema.exact_signature_l.getSolrFieldName(), 42L);
        document.setField(CollectionSchema.fuzzy_signature_l.getSolrFieldName(), 17L);
        document.setField(CollectionSchema.fuzzy_signature_text_t.getSolrFieldName(), "");
        document.setField(CollectionSchema.language_s.getSolrFieldName(), "fr");
        final Date suppliedDate = new Date(0L);
        document.setField(CollectionSchema.dates_in_content_dts.getSolrFieldName(), Arrays.asList(suppliedDate));
        document.setField(CollectionSchema.dates_in_content_count_i.getSolrFieldName(), 0);
        document.setField(CollectionSchema.title_count_i.getSolrFieldName(), 11);

        this.configuration.addTextAttributes(document);
        this.configuration.addURIAttributes(document, false, this.url);

        assertEquals(99, value(document, CollectionSchema.wordcount_i));
        assertEquals(42L, value(document, CollectionSchema.exact_signature_l));
        assertEquals(17L, value(document, CollectionSchema.fuzzy_signature_l));
        assertEquals("", value(document, CollectionSchema.fuzzy_signature_text_t));
        assertEquals("fr", value(document, CollectionSchema.language_s));
        assertEquals(Arrays.asList(suppliedDate), document.getFieldValues(CollectionSchema.dates_in_content_dts.getSolrFieldName()));
        assertEquals(0, value(document, CollectionSchema.dates_in_content_count_i));
        assertEquals("Imported document text", value(document, CollectionSchema.text_t));
        assertEquals(11, value(document, CollectionSchema.title_count_i));
        assertEquals(Arrays.asList(10), document.getFieldValues(CollectionSchema.title_chars_val.getSolrFieldName()));
    }

    @Test
    public void derivesBodyAttributesOnlyFromText() {
        final SolrInputDocument document = new SolrInputDocument();
        document.setField(CollectionSchema.text_t.getSolrFieldName(), "No date here");
        document.setField(CollectionSchema.sku.getSolrFieldName(), this.url.toNormalform(true));
        document.setField(CollectionSchema.title.getSolrFieldName(), "1. Januar 2020");
        document.setField(CollectionSchema.description_txt.getSolrFieldName(), "2. Februar 2021");
        document.setField(CollectionSchema.h1_txt.getSolrFieldName(), "Heading");

        this.configuration.addTextAttributes(document);

        assertEquals(3, value(document, CollectionSchema.wordcount_i));
        assertEquals(0, value(document, CollectionSchema.dates_in_content_count_i));
        assertFalse(document.containsKey(CollectionSchema.dates_in_content_dts.getSolrFieldName()));
        assertFalse(document.containsKey(CollectionSchema.title_count_i.getSolrFieldName()));
        assertFalse(document.containsKey(CollectionSchema.description_count_i.getSolrFieldName()));
        assertEquals(1, value(document, CollectionSchema.h1_i));
        assertFalse(document.containsKey(CollectionSchema.url_protocol_s.getSolrFieldName()));
    }

    @Test
    public void completesEachMissingFuzzySignatureFieldIndependently() {
        final SolrInputDocument hashSupplied = new SolrInputDocument();
        hashSupplied.setField(CollectionSchema.text_t.getSolrFieldName(), "Imported document text");
        hashSupplied.setField(CollectionSchema.fuzzy_signature_l.getSolrFieldName(), 17L);
        this.configuration.addTextAttributes(hashSupplied);
        assertEquals(17L, value(hashSupplied, CollectionSchema.fuzzy_signature_l));
        assertNotNull(value(hashSupplied, CollectionSchema.fuzzy_signature_text_t));

        final SolrInputDocument textSupplied = new SolrInputDocument();
        textSupplied.setField(CollectionSchema.text_t.getSolrFieldName(), "Imported document text");
        textSupplied.setField(CollectionSchema.fuzzy_signature_text_t.getSolrFieldName(), "");
        this.configuration.addTextAttributes(textSupplied);
        assertEquals("", value(textSupplied, CollectionSchema.fuzzy_signature_text_t));
        assertNotNull(value(textSupplied, CollectionSchema.fuzzy_signature_l));
    }

    @Test
    public void doesNotDeriveBodyAttributesWithoutStringText() {
        final SolrInputDocument document = new SolrInputDocument();
        document.setField(CollectionSchema.title.getSolrFieldName(), "1. Januar 2020");
        document.setField(CollectionSchema.dates_in_content_dts.getSolrFieldName(), Arrays.asList(new Date(0L)));

        this.configuration.addTextAttributes(document);

        assertEquals(2, document.size());
        assertFalse(document.containsKey(CollectionSchema.dates_in_content_count_i.getSolrFieldName()));
        document.setField(CollectionSchema.text_t.getSolrFieldName(), 123);
        this.configuration.addTextAttributes(document);
        assertEquals(3, document.size());
        assertFalse(document.containsKey(CollectionSchema.wordcount_i.getSolrFieldName()));
    }

    @Test
    public void derivesAllHeadingCountsWithoutBodyTextOrEnabledFields() {
        final SolrInputDocument document = new SolrInputDocument();
        document.setField(CollectionSchema.h1_txt.getSolrFieldName(), "Main heading");
        document.setField(CollectionSchema.h2_txt.getSolrFieldName(), Arrays.asList("First section", "Second section"));
        document.setField(CollectionSchema.h3_txt.getSolrFieldName(), Collections.emptyList());
        document.setField(CollectionSchema.h4_txt.getSolrFieldName(), "Subsection");
        document.setField(CollectionSchema.h5_txt.getSolrFieldName(), Arrays.asList("First detail", "Second detail"));
        document.setField(CollectionSchema.h6_txt.getSolrFieldName(), "Last detail");

        this.configuration.addTextAttributes(document);

        assertFalse(this.configuration.contains(CollectionSchema.h1_i));
        assertEquals(1, value(document, CollectionSchema.h1_i));
        assertEquals(2, value(document, CollectionSchema.h2_i));
        assertEquals(0, value(document, CollectionSchema.h3_i));
        assertEquals(1, value(document, CollectionSchema.h4_i));
        assertEquals(2, value(document, CollectionSchema.h5_i));
        assertEquals(1, value(document, CollectionSchema.h6_i));
        assertFalse(document.containsKey(CollectionSchema.wordcount_i.getSolrFieldName()));
    }

    @Test
    public void preservesAllSuppliedHeadingCounts() {
        final SolrInputDocument document = new SolrInputDocument();
        final CollectionSchema[] sources = {CollectionSchema.h1_txt, CollectionSchema.h2_txt, CollectionSchema.h3_txt,
                CollectionSchema.h4_txt, CollectionSchema.h5_txt, CollectionSchema.h6_txt};
        final CollectionSchema[] counts = {CollectionSchema.h1_i, CollectionSchema.h2_i, CollectionSchema.h3_i,
                CollectionSchema.h4_i, CollectionSchema.h5_i, CollectionSchema.h6_i};
        for (int i = 0; i < sources.length; i++) {
            document.setField(sources[i].getSolrFieldName(), Arrays.asList("First heading", "Second heading"));
            document.setField(counts[i].getSolrFieldName(), i * 11);
        }

        this.configuration.addTextAttributes(document);

        for (int i = 0; i < counts.length; i++) assertEquals(i * 11, value(document, counts[i]));
    }

    @Test
    public void doesNotInferHeadingCountsFromPlainText() {
        final SolrInputDocument document = new SolrInputDocument();
        document.setField(CollectionSchema.text_t.getSolrFieldName(), "Main heading\nSection heading\nSome body text");

        this.configuration.addTextAttributes(document);

        for (final CollectionSchema count : Arrays.asList(CollectionSchema.h1_i, CollectionSchema.h2_i,
                CollectionSchema.h3_i, CollectionSchema.h4_i, CollectionSchema.h5_i, CollectionSchema.h6_i)) {
            assertFalse(document.containsKey(count.getSolrFieldName()));
        }
    }

    @Test
    public void countsSuppliedDatesInsteadOfReplacingThem() {
        final SolrInputDocument document = new SolrInputDocument();
        final Date suppliedDate = new Date(0L);
        document.setField(CollectionSchema.dates_in_content_dts.getSolrFieldName(), Arrays.asList(suppliedDate));

        this.configuration.addURIAttributes(document, false, this.url);

        assertEquals(1, value(document, CollectionSchema.dates_in_content_count_i));
        assertEquals(Arrays.asList(suppliedDate), document.getFieldValues(CollectionSchema.dates_in_content_dts.getSolrFieldName()));
    }

    @Test
    public void derivesCountsOnlyFromAvailableLists() {
        final SolrInputDocument document = new SolrInputDocument();
        document.setField(CollectionSchema.h1_txt.getSolrFieldName(), "Design law");
        document.setField(CollectionSchema.images_urlstub_sxt.getSolrFieldName(), Arrays.asList("example.com/a.png", "example.com/b.png"));
        document.setField(CollectionSchema.outboundlinks_urlstub_sxt.getSolrFieldName(), Arrays.asList("other.example.com/"));

        this.configuration.addURIAttributes(document, false, this.url);

        assertEquals(1, value(document, CollectionSchema.h1_i));
        assertEquals(2, value(document, CollectionSchema.imagescount_i));
        assertEquals(1, value(document, CollectionSchema.outboundlinkscount_i));
        assertFalse(document.containsKey(CollectionSchema.inboundlinkscount_i.getSolrFieldName()));
        assertFalse(document.containsKey(CollectionSchema.linkscount_i.getSolrFieldName()));

        document.setField(CollectionSchema.inboundlinks_urlstub_sxt.getSolrFieldName(), Arrays.asList("example.com/a", "example.com/b"));
        this.configuration.addURIAttributes(document, false, this.url);
        assertEquals(3, value(document, CollectionSchema.linkscount_i));
    }

    @Test
    public void doesNotInventUnavailableContentOrIndexStatistics() {
        final SolrInputDocument document = new SolrInputDocument();

        this.configuration.addURIAttributes(document, false, this.url);

        assertFalse(document.containsKey(CollectionSchema.wordcount_i.getSolrFieldName()));
        assertFalse(document.containsKey(CollectionSchema.title_count_i.getSolrFieldName()));
        assertFalse(document.containsKey(CollectionSchema.language_s.getSolrFieldName()));
        assertFalse(document.containsKey(CollectionSchema.dates_in_content_count_i.getSolrFieldName()));
        assertFalse(document.containsKey(CollectionSchema.exact_signature_unique_b.getSolrFieldName()));
        assertFalse(document.containsKey(CollectionSchema.references_i.getSolrFieldName()));
        assertFalse(document.containsKey(CollectionSchema.load_date_dt.getSolrFieldName()));
    }

    @Test
    public void handlesEmptyText() {
        final SolrInputDocument document = new SolrInputDocument();
        document.setField(CollectionSchema.text_t.getSolrFieldName(), "");

        this.configuration.addTextAttributes(document);

        assertEquals(0, value(document, CollectionSchema.wordcount_i));
        assertEquals(0, value(document, CollectionSchema.dates_in_content_count_i));
        assertFalse(document.containsKey(CollectionSchema.language_s.getSolrFieldName()));
        assertFalse(document.containsKey(CollectionSchema.dates_in_content_dts.getSolrFieldName()));
    }

    private static Object value(final SolrInputDocument document, final CollectionSchema field) {
        return document.getFieldValue(field.getSolrFieldName());
    }
}
