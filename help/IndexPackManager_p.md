---
page: htroot/IndexPackManager_p.html
help: help/IndexPackManager_p.md
title: Index Pack Manager
package: import-export-federation
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/IndexPackManager_p.java
---

# Index Pack Manager

## Purpose

Index Pack Manager supervises installed or available index packages.

Use it to see which packages are present and how they affect local search.

## What You Can Do Here

- Index Pack Manager supervises installed or available index packages.
- Define the source, target, format, collection, and expected size before starting.
- Monitor progress because large transfers continue beyond the initial request.

## Page Architecture

Import and export pages translate external data formats into YaCy documents or move YaCy index data into another store. Most long-running actions create background work that should be monitored afterward.

## Correct Use

Use the manager to inspect packages before installing or removing them. Package changes can alter what local searches find without running a new crawl.

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/IndexPackManager_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/IndexPackManager_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/IndexPackManager_p.html` | `GET or POST` | admin | `source/net/yacy/htroot/IndexPackManager_p.java` |

### Parameter Guide

The table explains values that an agent or script must set deliberately. Parameters not relevant to a task should be omitted or left at the page default.

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `delete` | Deletes the selected URL, path, or index scope. Confirm the scope first. | Can remove data, stop work, expose access, or make a broad operational change. Use only with explicit confirmation and an exact target. |
| `file` | Selected or uploaded file. | Set only when this option is part of the intended request; otherwise omit it and let YaCy use the page default. |

Example request shape:

```http
GET or POST /IndexPackManager_p.html?delete=...&file=...&mvfrom=...&mvto=...
```

## What To Expect

The page should show package state, installed packages, or available package actions. Search results may change after package installation or removal.

JSON/JSONL packs use the same importer as [JsonList Import](IndexImportJsonList_p.md). Its indexing worker requires nonempty `text_t` and a present `id`; a missing ID is derived from the document URL. Documents with missing or empty `text_t` are skipped before metadata enrichment and indexing, even if titles, descriptions, or headings are supplied. A pack being moved to the loaded folder therefore does not mean that every document in it was indexed.

For accepted documents, missing enabled URL fields are computed from the document URL. Supplied `id`, `host_s`, and `host_id_s` values, including empty strings, are preserved regardless of the JSON field order.

`CollectionConfiguration.addTextAttributes()` derives missing word count, signatures and language from `text_t`. Dates are detected from `text_t` only when no date list is supplied. A supplied date list is preserved, and a missing date count comes from that list, including zero for an empty list; an existing count is preserved. Heading counts `h1_i` through `h6_i` come from the corresponding `h1_txt` through `h6_txt` fields. The helper's ability to count headings without body text does not bypass the importer's nonempty-text requirement.

Additional statistics and signatures from supplied titles, descriptions, text and lists are computed independently of target-field schema activation. Supplied metadata, including empty values, is preserved. Existing indexed documents are not backfilled.

## Related Pages

- Related transfer work is usually reached through the import/export page for the same format, the index-pack pages, or the queue/status page that reports progress.

## JSON Pack Monitoring And Results

Pack Manager shows JSON/JSONL progress in the **Process** column of **Packs: Load List**. There is no separate large status card above the tables. Row counters update every two seconds without reloading the page; pack folder tables refresh approximately every ten seconds. Polling pauses in hidden browser tabs.

The **Process** column shows the active JSON/JSONL pack's import phase, percentage of file bytes read, documents submitted to the index and documents per second. Other JSON packs show **Waiting for import.** until the importer starts them. The page backend matches progress by source path and gives the row its job ID, so a direct JSON import of a different file with the same name does not appear in the pack row. **View import activity** opens [JsonList Import](IndexImportJsonList_p.md) for full counters, recent errors and the controlled stop action. Its detailed status card shows the current or most recent JSON import, including imports started directly from that page.

Other formats show **Queued or processing.** with a **View import activity** link. With JavaScript enabled, WARC and ZIM links open their existing import pages, which display source, processed-record count, throughput and runtime while their importer is active. XML links open the pack-import results list (`/CrawlResults.html?process=7`). This list shows recent processed pack URLs across imports, rather than a per-file completion percentage; absence of new entries alone does not prove that an import stopped. An older backend without JSON row snapshots links to the JSON import page rather than describing a JSON filename as an unsupported format.

Row counters update from the same status response every two seconds, including after folder-table refreshes. A failed refresh retains the values and displays an outdated-status warning. A changed job ID or completion triggers an additional folder refresh to associate a newly started job or show its pack in `loaded` or `hold` promptly. Before JavaScript updates the page, the server renders an initial snapshot in the active row. Reading 100% does not finish indexing: the row continues to show the drain or stop phase while queued documents are processed.

An outdated-status warning does not disable automatic updates: failed requests are retried with a delay of up to 15 seconds. The warning clears after a successful refresh. If manual page reloads update the row but the warning persists, check `/api/importstatus_p.json`: it must return JSON, not an unprocessed template. For Eclipse launches, refresh and build the project to include externally added Java sources in `gen/`; see [status endpoint troubleshooting](IndexImportJsonList_p.md#status-endpoint-and-controlled-stop). Reloading the page cannot repair a missing backend class.

Physical bytes read provide compressed-file progress, while separate counters show documents read, submitted to the index, skipped and failed. Reading 100% is followed by draining any accepted work. Index submissions can update existing documents and do not guarantee immediate search visibility or a durable Solr commit. The last result remains until the next JSON import or YaCy restart.

Only a JSON pack with state `COMPLETED` is moved from `load` to `loaded`. Skipped records do not make the pack fail. A pack with document errors (`COMPLETED_WITH_ERRORS`), a fatal error (`FAILED`) or a controlled stop (`ABORTED`) is returned to `hold` for inspection and an explicit retry. Successfully written documents are retained. Retrying reprocesses the entire pack and may replace existing IDs. If moving a pack fails, inspect file permissions and any conflicting filename in the target folder.

The stop control on the linked JSON import page uses `POST /IndexImportJsonList_p.html` with `abort=1`, the displayed job ID and its transaction token. It stops further reading and drains accepted work. The current pack is kept active throughout that drain. Pack Manager itself has no stop form or transaction token for this action.

For automation, poll the administrator-only, read-only `GET /api/importstatus_p.json`. Its state is independent of Java thread liveness. The full response fields, counters, error retention and stop parameters are documented in [JsonList Import — Live Import Monitoring](IndexImportJsonList_p.md#live-import-monitoring). Pack folder actions and the status endpoint are separate: polling status has no side effects.

The page adds no request parameters or import actions for row monitoring. Its server template uses `packs-load_<i>_json` (`0`: activity link, `1`: JSON pack, `2`: empty-list placeholder). Within the JSON branch, `phase` selects waiting, running, draining, stopping or status unavailable (`0` through `4`); `active`, `job`, `percent`, `percent-label`, `submitted` and `speed` provide the initial display. The browser only attaches counters when the status job ID matches the row's server-provided job ID. Use the JSON status endpoint for automation instead of parsing these HTML fields.
