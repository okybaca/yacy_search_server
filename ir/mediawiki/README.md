# Wikipedia word statistics for YaCy

How common is a word across articles? This small toolkit turns Wikipedia dumps
into a shared reference for that question: download an edition, extract its
articles, clean the text, and count the words.

YaCy could use these statistics to give rare query terms more weight when
combining search results from different peers. The tools below work today;
publishing a dataset and using it in YaCy's ranking are still future work.
Wikipedia provides a useful reference corpus, but whether it improves web search
needs to be measured.

## Get started

Run these commands from `ir/mediawiki`. You need Python 3.9+, Bash, `curl`,
and `shasum`. Installed `lbzip2`/`bzip2` and `pigz`/`gzip` are used for streaming
decompression and compression; Python's standard library is the fallback.

```sh
python3 -m venv .venv
source .venv/bin/activate
python3 -m pip install -r requirements.txt
```

Processing dependencies are pinned in [requirements.txt](requirements.txt). If pip builds
the wikitext parser from source, a C compiler is also needed.

## Release shortcuts

| Command | Purpose |
| --- | --- |
| `python3 release.py 2026-10-01` | Downloads all configured languages, then extracts Markdown, computes term/document frequencies, and writes Parquet at ZSTD level 22. Each stage finishes for all languages before the next starts; completed outputs are skipped. |
| `python3 combine_document_frequency.py 2026-10-01` | Sums identical terms and `_` document totals from every configured language's Parquet into `_statistics/wikipedia-combined-<date>-document-frequency.parquet`. |
| `python3 extract_demo_dump.py 2026-10-01` | Selects English articles containing `Computer Science` in their title or text, case-insensitively, preserving Bulk record pairs in `_markdown/YaCy-Pack_scroll-common-web_wikipedia-en-demo_<yyyyMMdd>.jsonl.gz`. |

The release date must match the latest completed dumps selected by the downloader.
Existing Markdown packs skip both download and extraction. Combining and demo
extraction refuse existing outputs. See [README_FILES.md](README_FILES.md) for
file formats, import commands and query examples.

## From Wikipedia to statistics

First, download an edition:

```sh
./download_wikipedia_dump.sh de
```

Use `de` for German, `it` for Italian, or another Wikipedia language code.
The downloader selects the latest completed
[Wikimedia current-content export](https://wikitech.wikimedia.org/wiki/MediaWiki_Content_File_Exports)
and checks its SHA-256 checksums. Large exports arrive in several parts; all
parts belong to the same snapshot and are needed.
Rerunning the command skips files whose checksums match, resumes `.part` downloads,
and downloads completed files again only if their checksums fail.

Then use the date in the downloaded filenames:

```sh
python3 extract_wikipedia_dump_to_markdown.py de 2026-09-01
python3 markdown_to_term_frequency.py de 2026-09-01
python3 term_frequency_to_document_frequency.py de 2026-09-01
```

The direct converter reads every dump part and writes one Markdown YaCy Pack,
without creating wikitext files. A reader streams page records to worker processes;
one writer collects the results. Bounded queues limit memory use, and completion
signals drain all pending work before the final pack is published.

Shared helpers and the Markdown renderer live inside
`extract_wikipedia_dump_to_markdown.py`; the statistics scripts import its helpers.
There is no separate wikitext extraction step.

Each step leaves its results available to inspect:

| Location | Contents |
| --- | --- |
| `_dumps/` | Compressed dump parts and checksums, stored directly in this folder |
| `_markdown/` | One gzip-compressed YaCy Pack per language and snapshot |
| `_statistics/dewiki-2026-09-01-term-frequency.jsonl` | Word counts within each article |
| `_statistics/dewiki-2026-09-01-document-frequency.csv` | Number of articles containing each retained word |

Files stay directly in these folders. Dump filenames include their
page range; the Markdown pack combines all parts of the selected language/date:

```text
_dumps/dewiki-2026-09-01-p3442045p7820211.xml.bz2
_markdown/YaCy-Pack_scroll-common-web_wikipedia-de_20260901.jsonl.gz
```

The Markdown output is a gzip-compressed YaCy Pack, matching the existing packs:
Elasticsearch Bulk action/document pairs using YaCy's field names. `title` is a
one-element array; the other fields are strings. `last_modified` comes from the
dump's `<revision><timestamp>` and is omitted when absent.

```jsonl
{"index":{}}
{"url_s":"https://de.wikipedia.org/wiki/YaCy","title":["YaCy"],"text_t":"# YaCy\n\nEine Suchmaschine …\n","language_s":"de","last_modified":"2026-08-01T08:49:22Z"}
```

No `collection_sxt` or Wikipedia page ID is written. YaCy derives its own document
ID from `url_s`.

Pack names follow `YaCy-Pack_<category>-<tier>-<origin>_<slug>_<yyyyMMdd>.jsonl.gz`.
We use `scroll` (encyclopedia), `web`, and the generator's default tier `common`.
Use `--tier uncommon`, `rare`, `epic`, or `legendary` to choose another tier;
the tier is not calculated from file size. The slug is `wikipedia-<language>`;
the date is the dump snapshot date for reproducible names.
Keep only one tier per language/date when computing statistics.

The converter streams all dump parts into one compressed pack: after interruption,
the entire unfinished `.part` pack restarts. Completed packs are skipped.
Use `--overwrite` to regenerate a pack, including older packs without revision
timestamps. The dump date is never substituted for a missing revision timestamp.
The final pack is replaced only after successful conversion.

Dump discovery rejects unfinished downloads, invalid or overlapping part ranges,
and missing or extra parts relative to the downloaded checksum manifest, when present.
The downloader verifies checksums; extraction does not hash the dump files again.
Gaps between page IDs are valid. Without a manifest, missing parts cannot reliably
be detected.
Statistics require exactly one completed combined pack for the language/date;
old packs with page ranges are ignored and are not deleted.

Both statistics stages refuse existing outputs and unfinished `.part` files.
Move these aside before rerunning. Regenerating a Markdown pack does not regenerate
its statistics automatically.

YaCy can import these packs through `DATA/PACKS/load/`. For Elasticsearch, decompress
and submit complete action/document pairs to `/<index>/_bulk` as NDJSON, in batches
within the server's request-size limit. Empty `index` actions use generated IDs
unless an ingest pipeline supplies them. The OpenSearch setup in
[README_FILES.md](README_FILES.md) derives IDs from `url_s`, so repeat imports
replace documents for the same URL.
See the [Bulk API documentation](https://www.elastic.co/docs/api/doc/elasticsearch/operation/operation-bulk).

Markdown conversion and both statistics stages use all available CPU cores by default.
Add `--jobs 4` to limit concurrency. These conversion and counting scripts show usage
with no arguments. Every script accepts `-h` or `--help`.
Term counting streams the pack through a bounded queue; workers decode JSON and
count each article independently. Completed counts are written continuously,
without keeping the whole corpus in memory.
TF-to-DF also decodes and counts batches in parallel, then merges their counters
in one process. Its final vocabulary stays in RAM; `--jobs 1` runs it sequentially.

### Extract a Computer Science demo pack

```sh
python3 extract_demo_dump.py
```

This streams the completed English Markdown pack for `2026-10-01` and selects
every article whose title or Markdown text contains the literal phrase
`Computer Science`, case-insensitively. Matching is a substring search, so
`computer sciences` also matches; separate occurrences of `computer` and `science`
do not. Matches in URLs or other metadata do not select an article.

The result is
`_markdown/YaCy-Pack_scroll-common-web_wikipedia-en-demo_20261001.jsonl.gz`.
It preserves each selected article's original Elasticsearch Bulk
`{"index":{}}` action and document line, including URLs, titles, text, language,
timestamps and any other fields. Copy the pack to `DATA/PACKS/load/` for YaCy,
or use the same Elasticsearch Bulk import procedure described above.

An optional date argument selects the snapshot:
`python3 extract_demo_dump.py 2026-10-01`. No new downloads or XML conversion are
needed. The script reads one article pair at a time, checks the input format,
and reports scanned and selected article counts. Completed outputs and existing
`.part` outputs are refused. The final file is published only after the source
has been fully read and the gzip output has been closed successfully.

### Which articles and words are included?

Extraction includes namespace `0` pages with available wikitext, including
redirects, disambiguation pages, and empty articles. Talk pages, user pages,
templates, categories, and other namespaces are skipped. The progress display
therefore shows more pages read than articles written.

Titles are stored as UTF-8 data rather than filenames, so filesystem restrictions
and filename collisions do not affect article titles.

Markdown keeps prose, headings, emphasis, lists, table text, and TeX formulas.
Every Markdown record's `text_t` starts with the original article title as a `#` heading.
It removes reference tags, URL targets, file/category embeds, recognized navigation
containers, and decorative markup. Localized file/category namespace names are
read from dump metadata. Link labels are retained. Common reference-section titles
in English, German, French, Italian, and Spanish are omitted; other languages and
unrecognized section names may retain that material.
Only a small set of presentation templates (`lang`, `nowrap`, `nobr`, `small`,
`smaller`, `big`, `larger`, `math`, `mvar`, `var`, `abbr`) is supported. Other
templates are omitted, so some template-generated content is lost. Templates are
not fetched or expanded through MediaWiki; the original dumps remain available
for comparison.

Counting reads only the Pack document records, excluding the `index` action lines
and metadata. It lowercases `text_t` and treats sequences of Unicode letters,
numbers, and attached combining marks as words. Punctuation, apostrophes,
hyphens, and underscores separate words. The token pattern is
`[\p{L}\p{N}][\p{L}\p{M}\p{N}]*`.
There is no stemming, stopword removal, accent removal, or Unicode normalization.
Article titles, section headings, numbers, and TeX command names also contribute tokens.
Languages without word spaces may need a more suitable tokenizer before these
counts are used for language-specific evaluation.

## Reading the results

**Term frequency** counts occurrences inside one article. **Document frequency**
counts articles containing the term, once per article regardless of repetition.

For example, the term-frequency JSONL could contain:

```jsonl
{"Solar":{"solar":2,"energy":1}}
{"Wind":{"wind":1,"energy":1}}
{"Battery":{"solar":1,"battery":1,"energy":1}}
{"Empty":{}}
```

With the current filtering thresholds, its document-frequency CSV is:

```text
term;count
_;4
```

Here, `solar` occurs three times across two articles, so its document frequency
is `2`. All four terms are omitted from the CSV because their counts are below 10.
The reserved `_` row records all four documents, including the empty one.
Rows sort by document frequency from lowest to highest, alphabetically for ties,
with the `_` row last.

The generated CSV files start with `term;count` and use UTF-8, semicolons,
integer counts, LF line endings, and no byte-order mark.
A nonnumeric term is retained when **its document frequency is at least 10 AND it
sorts at or after `aa`, OR its document frequency exceeds 100** (Python's Unicode
lexicographic order). For example, `aa` with count 9 and `a` with count 100 are
omitted; `aa` with count 10 and `a` with count 101 are retained.
Full occurrence counts remain in the JSONL. Evaluation should use a count of
**10 for any missing term**, matching the long-tail cutoff. No document-frequency
JSON is generated.

The TF-to-DF step also excludes terms consisting only of decimal digits after
removing dots and commas, including `42`, `3.14`, `3,14`, and `1.234,56`.
Unicode decimal digits are included in this rule; mixed terms such as `3d` are
subject to the count-and-order filter above.
The term-frequency JSONL and total document count are unaffected.

JSONL article keys retain their original titles and case. Word keys are lowercase.
Record order can vary between runs; the counts are independent of worker order.

## Export document frequencies as Parquet

```sh
python3 write_parquet.py de 2026-09-01
```

This reads the document-frequency CSV and writes
`_statistics/dewiki-2026-09-01-document-frequency.parquet` using PyArrow.
The Parquet rows are sorted by term; the CSV keeps its count ordering.
Both columns (`term`: UTF-8 string, `count`: 64-bit integer) and the `_` row are
preserved. Parquet uses prefix encoding for terms and ZSTD compression at level 22.
Use `--compression-level 19` for faster writing at a lower compression level.

The converter loads and sorts the full table in RAM, including extra sorting
workspace. It writes to `.part` and publishes the final filename only on success.
Existing outputs are refused; move them aside before rerunning.

## Combine languages as a document-frequency heuristic

```sh
python3 combine_document_frequency.py 2026-10-01
```

This reads the document-frequency Parquet file for every language listed in
`release.py` (currently 30). Every input for the requested date must exist;
missing files and unfinished `.parquet.part` inputs stop the merge.
The default output is
`_statistics/wikipedia-combined-2026-10-01-document-frequency.parquet`.
Use `--input-dir PATH` for another statistics directory or `--output FILE`
for another output path. The combined filename is separate from language inputs,
so it does not contribute to a later merge.

Matching term strings contribute the **sum of their stored counts**. For example,
`solar: 12` in one language and `solar: 25` in another produce `solar: 37`.
Terms present in only one language keep their original counts. The reserved `_`
row is also summed, giving the combined document total. Missing terms contribute
zero; no fallback count, language weighting, normalization or further filtering
is applied. Terms omitted during the original per-language filtering remain
unavailable to this heuristic.

Inputs must have unique terms sorted by term, as produced by `write_parquet.py`,
with `term` as a UTF-8 string and `count` as an int64. The script merges sorted
input batches and writes output row groups, without holding the combined
vocabulary in memory. It checks schemas, ordering, counts and corpus totals;
int64 sum overflow stops the merge.

Output retains the same two columns, is sorted by term and uses prefix encoding
and ZSTD compression at the maximum level **22**. Parquet metadata records the
snapshot, contributing languages and aggregation rule. Existing output and
`.part` files are refused. The final filename is published only after every
input has been consumed and validated successfully.

## Analyze document frequencies

After completing the document-frequency stage, run:

```sh
python3 -m pip install -r requirements-analysis.txt
python3 analyze_document_frequency.py 2026-10-01
```

The analyzer discovers all matching `*wiki-<date>-document-frequency.csv` files
directly in `_statistics/`. It reads one language at a time, validates the export
rules and computes statistics over every retained term. It does not read the
much larger term-frequency JSONL files. CSV input is the source of analysis;
Parquet files supply optional file-size comparisons, without content validation.

Results are saved in `_statistics/_analysis/2026-10-01/`:

| Location | Contents |
| --- | --- |
| `report.html` | Offline report with findings, figures, limitations and next evaluation steps |
| `summary.json` | Metrics, source SHA-256 hashes, software versions and measurement definitions |
| `graphs/` | PNG and SVG figures comparing languages, plus four diagnostic plots per language |
| `data/summary.csv` | Corpus size, retained vocabulary, DF quantiles, IDF, token diagnostics and file sizes |
| `data/threshold_sensitivity.csv` | Vocabulary and term–document incidence remaining after DF or prevalence cutoffs |
| `data/top_terms.csv` | Top 100 retained terms per language, with DF, prevalence and IDF |
| `data/longest_terms.csv`, `data/marker_terms.csv` | Samples and literal tokens for manual rendering/tokenizer audits |
| Other `data/*.csv` | Plot data: sampled ranks, DF survival curves, IDF histograms/formulas, term lengths and prevalence bands |

All analysis tables use UTF-8 and semicolon delimiters. Rank curves retain up to
800 exact ranks per language for plotting; scalar metrics and threshold counts
use the complete retained vocabulary. Token lengths count Unicode code points.
Both classic `ln(N/DF)` and positive BM25 IDF
`ln(1 + (N - DF + 0.5)/(DF + 0.5))` are evaluated as weighting scenarios.

The vocabulary was already filtered during document-frequency generation.
These outputs cannot recover excluded terms, measure query coverage or establish
ranking improvements. The reserved `_` corpus count includes redirects and
empty articles. The report distinguishes retained term–document incidence from
word occurrences and document coverage, and proposes follow-up retrieval
experiments requiring query data and relevance judgments.

Use `--languages de en` to select editions, `--input-dir PATH` to read another
statistics directory, or `--output-dir PATH` to save a separate experiment.
Existing output directories are refused. Results are first written to a sibling
directory ending in `.part` and renamed only after the whole analysis succeeds.
An interrupted run leaves that directory for inspection; choose a new output
path or move the unfinished directory aside before retrying.
