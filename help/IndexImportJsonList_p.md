---
page: htroot/IndexImportJsonList_p.html
help: help/IndexImportJsonList_p.md
title: JsonList Import
package: import-export-federation
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/IndexImportJsonList_p.java
---

# JsonList Import

## Purpose

JsonList Import indexes prepared documents with a URL and nonempty full text from JSON/JSONL files, with one JSON object per line.

Use it when another tool has already supplied the document text and metadata as JSON. URL-only lists and documents containing only metadata are not indexed by this importer.

## What You Can Do Here

- Import prepared JSON/JSONL documents with a URL and nonempty full text.
- Select a server file path or download URL before starting.
- Monitor progress because large transfers continue beyond the initial request.

## Page Architecture

Import and export pages translate external data formats into YaCy documents or move YaCy index data into another store. Most long-running actions create background work that should be monitored afterward.

| Control | Meaning | Values or examples |
| --- | --- | --- |
| `file` | Path to a readable file on the YaCy server. | Local filesystem path; not a browser upload. |
| `url` | URL to inspect, crawl, import, or act on. | URL or URL-derived value; use the exact format shown by the page. |
| `submit` | Submits the form. | `Import JsonList File` |
| `abort` | Request controlled stopping of the identified import. | `1`, with `job` and `transactionToken`. |

## Correct Use

Choose a readable server file or a download URL. Indexing runs in the background; monitor it on this page or in Pack Manager.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected endpoints: `/IndexImportJsonList_p.html`, `/api/importstatus_p.json`.

## Automation And API

Page backend: `source/net/yacy/htroot/IndexImportJsonList_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/IndexImportJsonList_p.html` | `GET` for viewing/starting; `POST` for controlled stop | admin | `source/net/yacy/htroot/IndexImportJsonList_p.java` |
| `/api/importstatus_p.json` | `GET`, read-only | admin | `source/net/yacy/htroot/api/importstatus_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `file` | Readable path on the YaCy server. | Takes precedence over `url` when both are supplied. |
| `url` | URL of a JSON/JSONL source. | Downloaded before indexing starts; `.gz` URL paths enable GZIP decompression. |
| `submit` | Submit action for the form. Its meaning depends on the surrounding fields. | Changes stored data, configuration, or a running job. Use the authenticated action flow where required and verify the result. |
| `abort` | `1` requests a controlled stop. | Send by POST with the matching `job` ID and `transactionToken`. |
| `job` | Job ID from the monitor/status endpoint. | Required for stopping; stale IDs have no effect. |
| `transactionToken` | Token from the shared stop form. | Required by remote POST transaction checks. |

Example request shape:

```http
GET /IndexImportJsonList_p.html?file=...
GET /api/importstatus_p.json
POST /IndexImportJsonList_p.html

abort=1&job=...&transactionToken=...
```

## What To Expect

Small operations may finish during the request; large imports, exports, harvests, and package operations usually need monitoring. Expect progress, logs, queue entries, or generated files rather than instant final search quality.

The indexing worker requires nonempty `text_t` and a present `id`. Supply `text_t` as a nonempty JSON string. A missing `id` is derived when a URL is read from `url`, `url_s`, or `sku`. Documents with missing or empty `text_t` are skipped before metadata enrichment and indexing, even if they contain titles, descriptions, or headings. The same requirement applies to JSON/JSONL packs loaded through Pack Manager.

For accepted documents, the JSON importer calls `CollectionConfiguration.addTextAttributes()` and `CollectionConfiguration.addURIAttributes()` before indexing. Supplied `id`, `host_s`, and `host_id_s` values, including empty strings, are preserved regardless of their position before or after the URL key in the JSON object; only missing fields are derived. URL fields follow the schema configuration and include protocol, URL length, directory path components and their count, filename and extension, host components, and query parameter names, values, and count. IP metadata requires DNS resolution when `ip_s` is enabled.

`addTextAttributes()` uses only `text_t` to derive missing `wordcount_i`, `exact_signature_l`, `fuzzy_signature_l`, `fuzzy_signature_text_t`, and `language_s`. Language detection requires loaded language profiles. If `dates_in_content_dts` is absent, dates are detected from `text_t` with a timezone offset of zero and missing date fields are filled. If a date list is supplied, it is preserved and a missing `dates_in_content_count_i` is computed from that list, including zero for an empty list; dates are not re-detected from the text. An existing date count is preserved.

Missing `h1_i` through `h6_i` are computed from the corresponding `h1_txt` through `h6_txt` fields. A supplied empty heading list produces a zero count; an absent list leaves the count unset. Heading levels cannot be reconstructed from plain `text_t`. The helper method can count supplied headings even without body text, but this does not bypass the importer's nonempty-text requirement.

When the other source fields are available, additional content metadata is derived: title and description counts, lengths, word counts and signatures, and counts of supplied heading, link and image lists. A missing list is not assumed to be empty. Content metadata derivations run independently of target-field schema activation and preserve supplied values, including empty values and zero counts. Synonym and vocabulary enrichment uses the configured libraries. Existing index entries are not backfilled, and these derivations do not reconstruct crawl history or calculate index-wide uniqueness or reference statistics.

## Related Pages

- Related transfer work is usually reached through the import/export page for the same format, the index-pack pages, or the queue/status page that reports progress.

## Live Import Monitoring

JsonList Import shows the detailed monitor for the current or most recent JSON/JSONL import. Pack Manager uses the same status endpoint for compact progress in each matching pack's **Process** cell and links here for full counters, errors and controlled stopping. It has no duplicate large status card. One JSON import can run at a time, including synchronous Pack Manager imports. The last result remains available until another JSON import starts or YaCy restarts. It is held in memory, not persisted across restarts. XML, WARC and ZIM imports and downloads are outside this monitor.

The monitor refreshes every two seconds without reloading the page. Polling pauses while the browser tab is hidden. A failed refresh keeps the previous values and shows that they may be outdated; it does not imply that the import stopped. Without JavaScript, reload the page after enabling JavaScript to use the live monitor.

- **File read** counts physical file bytes before GZIP decompression. For compressed files the denominator is the compressed file size. Buffering can make reading reach 100% before document processing has finished; this is not an overall completion percentage. The file is read once, with no preliminary document-count scan. Byte and document counters use 64-bit values, including for files of 20 GiB or more.
- **Documents read** counts document records, excluding empty objects and Elasticsearch-style index headers. A malformed record which terminates parsing is included.
- **Submitted to index** counts documents whose Solr write call returned without an error. Submissions may replace existing IDs. They do not imply new index entries, immediate search visibility or a durable commit. The importer does not force a Solr commit for monitoring.
- **Skipped** counts records without nonempty `text_t` or a present `id`, plus a reader record discarded by thread interruption before enqueueing. Skipped records do not increment the error counter.
- **Errors** counts failed records and fatal reader/worker errors. **Ignored fields** counts occurrences of unknown schema fields; these fields are ignored without rejecting the document.
- **Documents/s** measures completed records (submitted, skipped or failed) over an approximately 15-second polling window. The final result shows the overall average. **Estimated read time left** estimates file reading only from elapsed time and physical bytes read. It is unavailable initially, during stopping or after all file bytes have been read; it does not estimate the final queue drain.
- **Queue** shows waiting documents, excluding documents already being processed. **Busy workers** counts documents currently being processed, out of the configured worker count. No progress for at least 60 seconds raises a warning without aborting the job.
- **Recent errors** retains up to ten error-message groups, with the last line/document and occurrence count within the retained group. Messages and document identifiers are length-limited. Source-level errors use line zero. Error details are displayed as text, including when source data contains HTML.

States are `RUNNING`, `DRAINING`, `STOPPING`, `COMPLETED`, `COMPLETED_WITH_ERRORS`, `ABORTED` and `FAILED`. `DRAINING` means the reader has finished and accepted work is still being processed; the job remains active until all workers exit. `COMPLETED_WITH_ERRORS` means reading finished but one or more documents failed. Fatal parsing, input or worker errors produce `FAILED`, after draining accepted work where possible.

The file field accepts a path on the YaCy server, not a browser upload. A URL source is downloaded to a temporary file synchronously before indexing starts; download progress is not included. Successfully imported temporary downloads are deleted. Temporary sources with an unsuccessful import are retained.

### Status Endpoint And Controlled Stop

`GET /api/importstatus_p.json` is an administrator-only, read-only endpoint and takes no parameters. It returns `{"job": null}` before the first import, otherwise `job` contains:

| Fields | Meaning |
| --- | --- |
| `id`, `source`, `state`, `active` | Job identifier, source filename, lifecycle state and active flag. |
| `bytesRead`, `totalBytes` | Physical file bytes read and file size. |
| `read`, `submitted`, `skipped`, `failed`, `errors`, `ignoredFields` | 64-bit counters; `failed` counts failed document records, whereas `errors` also includes source/worker errors. |
| `documentsPerSecond`, `elapsedSeconds`, `remainingSeconds` | Throughput and durations in seconds; `remainingSeconds = -1` means the estimate is unavailable. Elapsed time is frozen at completion. |
| `lastProgressSeconds`, `stalled` | Seconds since the last reader/worker progress and the 60-second warning flag. |
| `queueSize`, `queueCapacity`, `activeWorkers`, `workers` | Waiting and currently processed work. |
| `recentErrors` | Bounded list of `{line, document, message, occurrences}`. |

Poll about every two seconds, use the lifecycle fields to determine completion, and retain the job ID when issuing a stop. Do not infer completion from file bytes, index growth or browser connectivity. A nonzero error count requires inspection even if the job is no longer active.

If the endpoint returns the literal template placeholder instead of JSON, its Java backend is missing from the running classpath. Ant normally builds the JAR and classes under `build/`; Eclipse launches use `gen/`. Updating the JAR alone does not update an Eclipse launch. When Java files were added outside Eclipse, select the YaCy project, use **Refresh**, then **Build Project** so Eclipse registers and compiles the new sources. Manually copying a class into `gen/` is temporary: a later Eclipse rebuild can remove it if its source has not been registered. After existing imports finish, restart with the current classes if already-loaded import/page classes have changed. A missing status class can be loaded on the next request once Eclipse has built it. The monitor retries failed requests automatically, with a delay of up to 15 seconds, and recovers without reloading the page. Polling resumes immediately when a hidden tab becomes visible.

The shared stop form submits `POST /IndexImportJsonList_p.html` with `abort=1`, `job=<id>` and `transactionToken=<token from the form>`. Remote requests require a valid POST transaction token under YaCy's existing transaction checks. A missing or outdated job ID does not stop a newer import. Stopping prevents further source reading and finishes already accepted/queued work; the terminal state is `ABORTED`. It does not remove already written documents or provide a resume checkpoint. Existing file/URL start requests remain supported; starts are rejected while another JSON import is active.
