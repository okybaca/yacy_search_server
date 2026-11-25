---
page: htroot/IndexPackDownloader_p.html
help: help/IndexPackDownloader_p.md
title: Index Pack Downloader
package: import-export-federation
access: admin
kind: admin-page
backend_java: source/net/yacy/htroot/IndexPackDownloader_p.java
---

# Index Pack Downloader

## Purpose

Index Pack Downloader retrieves prepared YaCy index packs from Hugging Face datasets and saves them in `DATA/PACKS/hold`. Downloading a pack does not import it into the index; use Pack Manager to load the completed file.

## What You Can Do Here

- Browse available packs, their Hugging Face repositories and download sizes.
- Download a pack, including archives of 20 GB or more, without buffering the complete file in Java memory.
- See immediate feedback while a download request runs, then follow the import guidance and Pack Manager link.
- Distinguish packs ready for import, queued/importing packs and processed packs, or read a download error.

## Page Architecture

The server still downloads synchronously within one request. With JavaScript, the page submits that request in the background and immediately shows an indeterminate download indicator, the selected filename and a waiting message. It disables all download buttons in that tab to prevent duplicate submissions. The indication is not a byte counter or percentage, and there is no persistent download job or cross-tab monitoring. The existing HTTP connection and socket inactivity timeouts still apply; the browser code adds no total-duration timeout for large files.

When the response arrives, the page updates the result and pack table without navigating or refreshing. A successful download leaves a persistent completion message with the next step and a direct **Import in Pack Manager** link. A handled server failure displays its error and enables another attempt. If the response is lost, has an HTTP error status, or cannot be recognized, the page shows **Download status unavailable** and keeps download buttons disabled because the server may still be transferring. Check Pack Manager before retrying; the browser does not automatically repeat or cancel a download. Without JavaScript, the POST form uses normal page navigation and the result appears when the request returns.

## Correct Use

The **Download size** column shows the physical archive size advertised by Hugging Face, using B, KiB, MiB, GiB or TiB (powers of 1024). Hover over a known size to see the exact byte count. For `.gz` files this is the compressed download size, not the decompressed JSON size or the disk space needed for the imported index. Zero bytes is displayed as `0 B`; unavailable sizes are shown as **Unknown**. File sizes use 64-bit values, including for archives above 2 GiB and 20 GiB.

Sizes come from the repository metadata response (`blobs=true`, including LFS metadata), with no additional HEAD request or file download per table row. The existing filename-only `HFClient.listFiles()` and `HFClient.getAllPacks()` Java interfaces remain available.

Select **Download** beside a pack and keep the page open until the result appears. Ensure the hold filesystem has enough free space for the archive. Files are streamed to a unique `.part` file in the destination directory. Only a successful, nonempty transfer is renamed to the final filename; the advertised content length is checked when available. Temporary files are removed on handled failures, and an existing completed pack is preserved when a transfer fails. A process crash can leave an ignored `.part` file that may be removed manually when no download is running.

After completion, follow **Import in Pack Manager** and select **load** beside the pack in the hold list to import it. Downloading alone does not add documents to the index. Importing requires additional disk space for the index. Avoid refreshing, leaving or repeating a download request while it is running; another tab can start another transfer.

The table guides the next action according to the existing local pack location:

| State | Meaning and next action |
| --- | --- |
| Not stored locally | **Download** starts the transfer. |
| `hold` | **Downloaded; ready to import.** Follow **Import in Pack Manager**, then select **load**. |
| `load` | **Queued or importing.** Follow **View in Pack Manager** to inspect progress. |
| `loaded` | **Pack processed.** The pack has already gone through processing; it does not require another import merely because it appears in the downloader. Follow **View in Pack Manager** for details. This folder state alone does not prove that every record was indexed. |
| Other local locations (`live`, `unload`) | **Stored locally.** No import state is claimed; these folders are outside the manager's hold/load/loaded lists. |

## Access And Safety

Administrator access is required. YaCy protects `_p` pages as administration pages.

Protected related endpoint(s): `/IndexPackDownloader_p.html`.

## Automation And API

Page backend: `source/net/yacy/htroot/IndexPackDownloader_p.java`.

| Endpoint | Method | Access | Backend |
| --- | --- | --- | --- |
| `/IndexPackDownloader_p.html` | `GET or POST` | admin | `source/net/yacy/htroot/IndexPackDownloader_p.java` |

### Parameter Guide

| Parameter | Meaning and valid values | Care |
| --- | --- | --- |
| `dlsource` | `Huggingface` | Case-sensitive. Omit download parameters to list packs only. |
| `dlrepoid` | Dataset repository ID, such as `yacy/YaCy-Pack_scroll-common-web_wikipedia_20261001` | Uses the repository's `main` revision. |
| `dlfile` | Repository-relative pack filename | Must resolve inside the configured hold directory. URL-encode parameter values. |

Example request shape:

```http
GET /IndexPackDownloader_p.html?dlsource=Huggingface&dlrepoid=yacy%2FYaCy-Pack_scroll-common-web_wikipedia_20261001&dlfile=YaCy-Pack_scroll-common-web_wikipedia-de_20261001.jsonl.gz
```

Both GET and POST still accept these parameters and start the same synchronous download. The browser download buttons now use POST. Configure automation clients to allow the full transfer duration. There is no automatic page refresh after completion, so the next-step guidance stays visible. JavaScript removes the download parameters from a completed legacy GET request's address to prevent refresh from replaying it; background POST requests leave the listing URL unchanged. Without JavaScript, manually refreshing a POST result may resubmit the download.

The HTML response displays **Download failed:** followed by an escaped error detail on failure; HTTP status alone does not indicate download success. Template properties include `download-error` (`0` or `1`), `download-error_message` on failure, `download-complete` (`1` after a successful transfer), `download-complete_file` (escaped filename), and the `packs-source` listing. The previous `reload` property and automatic refresh have been removed. Per-row `state` values are `0` (remote/downloadable), `1` (hold), `2` (load), `3` (loaded), and `4` (other local location). Downloadable rows also provide `state_repoid` and `state_file` for the POST form; localized HTML controls replace the old backend-generated `process` markup. Each `packs-source_<n>` row also includes `size` (`1` for a known size, otherwise `0`). Known sizes provide `size_bytes` (exact 64-bit byte count), `size_value` (formatted display number) and `size_unit` (binary unit). These are HTML template properties, not a separate JSON endpoint. For HTML automation, the known size's tooltip contains the exact byte count followed by `B`; do not interpret **Unknown** as zero or use the displayed archive size to estimate final index storage.

Automation should check for the error message and confirm that the final pack appears in the listing or hold directory before requesting an import. `.part` files are not loadable packs.

## What To Expect

Transfers use bounded memory and long file sizes, so the previous byte-array limit of approximately 2 GiB does not apply. Download time depends on the archive size, network, and disk throughput. Downloading leaves the pack in hold until explicitly loaded.

## Related Pages

- [Pack Manager](IndexPackManager_p.md) loads downloaded packs into the index.
- [Pack Generator](IndexPackGenerator_p.md) creates packs from local index data.
