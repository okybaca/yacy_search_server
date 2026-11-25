/**
 *  HFClient
 *  Copyright 2025 by Michael Peter Christen
 *  First released 19.08.2025 at https://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.kelondro.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.yacy.cora.protocol.ClientIdentification;
import net.yacy.cora.protocol.http.HTTPClient;


public class HFClient {

    private static final String BASE = "https://huggingface.co";

    /** Repository-relative path and physical file size in bytes; -1 means unknown. */
    public record FileMetadata(String path, long size) { }

    public enum RepoType {
        MODEL("models"),
        DATASET("datasets"),
        SPACE("spaces");

        private final String pluralSegment;
        RepoType(final String s) { this.pluralSegment = s; }
        public String segment() { return this.pluralSegment; }
    }


    private static String textOrNull(final JsonNode n) {
        return (n != null && !n.isNull()) ? n.asText() : null;
    }


    public static List<String> listYaCyPacks() throws IOException, InterruptedException {
        return listRepositories(null, RepoType.DATASET, 20, "YaCy-Pack");
    }

    private static List<String> listRepositories(final String author, final RepoType type, final int limit, final String search) throws IOException, InterruptedException {
        final StringBuilder url = new StringBuilder(BASE).append("/api/").append(type.segment()).append("?limit=").append(limit <= 0 ? 100 : limit);
        if (author != null && !author.isBlank()) url.append("&author=").append(URLEncoder.encode(author, java.nio.charset.StandardCharsets.UTF_8));
        if (search != null && !search.isBlank()) url.append("&search=").append(URLEncoder.encode(search, java.nio.charset.StandardCharsets.UTF_8));

        final byte[] body;
        try (final HTTPClient hc = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent)) {
            body = hc.GETbytes(url.toString(), null, null, false);
        }

        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode arr = mapper.readTree(body);
        final List<String> ids = new ArrayList<>();
        if (arr.isArray()) {
            for (final JsonNode n : arr) {
                // The field is "modelId" for models, "id" for datasets/spaces; normalize both.
                String id = textOrNull(n.get("modelId"));
                if (id == null) id = textOrNull(n.get("id"));
                if (id != null) ids.add(id);
            }
        }
        return ids;
    }

    public static ArrayList<String> listFiles(final String repoId, final boolean only_jsonl) throws IOException, InterruptedException {
        final ArrayList<String> files = listFiles(repoId, RepoType.DATASET);
        if (!only_jsonl) return files;
        for (int i = files.size() - 1; i >= 0; i--) {
            final String file = files.get(i);
            if (!file.contains(".jsonl")) {
                files.remove(i); // remove non-jsonl files
            }
        }
        return files;
    }

    public static ArrayList<String> listFiles(final String repoId, final RepoType type) throws IOException, InterruptedException {
        final ArrayList<String> paths = new ArrayList<>();
        for (final FileMetadata file : listFilesWithMetadata(repoId, type)) paths.add(file.path());
        return paths;
    }

    public static ArrayList<FileMetadata> listFilesWithMetadata(final String repoId, final RepoType type)
            throws IOException, InterruptedException {
        // Request sizes in the repository listing, including LFS files, instead of issuing one HEAD per file.
        final String url = BASE + "/api/" + type.segment() + "/" + repoId + "?blobs=true";
        final byte[] body;
        try (final HTTPClient hc = new HTTPClient(ClientIdentification.yacyInternetCrawlerAgent)) {
            body = hc.GETbytes(url, null, null, false);
            if (hc.getStatusCode() != 200 || body == null) {
                throw new IOException("Hugging Face returned HTTP " + hc.getStatusCode());
            }
        }

        final JsonNode root = new ObjectMapper().readTree(body);
        JsonNode filesArray = root == null ? null : root.get("siblings");
        if (filesArray == null && root != null) filesArray = root.get("files");

        final ArrayList<FileMetadata> files = new ArrayList<>();
        if (filesArray != null && filesArray.isArray()) {
            for (final JsonNode file : filesArray) {
                String path = textOrNull(file.get("rfilename"));
                if (path == null) path = textOrNull(file.get("path"));
                if (path == null) continue;
                JsonNode size = file.get("size");
                if (size == null || !size.isIntegralNumber() || !size.canConvertToLong() || size.longValue() < 0) {
                    size = file.path("lfs").path("size");
                }
                final long bytes = size != null && size.isIntegralNumber() && size.canConvertToLong()
                        && size.longValue() >= 0 ? size.longValue() : -1;
                files.add(new FileMetadata(path, bytes));
            }
        }
        return files;
    }

    public static Map<String, List<String>> getAllPacks(final boolean only_jsonl) throws IOException, InterruptedException {
        final Map<String, List<String>> paths = new LinkedHashMap<>();
        for (final Map.Entry<String, List<FileMetadata>> entry : getAllPacksWithMetadata(only_jsonl).entrySet()) {
            final List<String> files = new ArrayList<>(entry.getValue().size());
            for (final FileMetadata file : entry.getValue()) files.add(file.path());
            paths.put(entry.getKey(), files);
        }
        return paths;
    }

    public static Map<String, List<FileMetadata>> getAllPacksWithMetadata(final boolean only_jsonl)
            throws IOException, InterruptedException {
        final Map<String, List<FileMetadata>> packs = new LinkedHashMap<>();
        for (final String repoId : listYaCyPacks()) {
            final ArrayList<FileMetadata> files = listFilesWithMetadata(repoId, RepoType.DATASET);
            if (only_jsonl) {
                for (int i = files.size() - 1; i >= 0; i--) {
                    if (!files.get(i).path().contains(".jsonl")) files.remove(i);
                }
            }
            if (!files.isEmpty()) packs.put(repoId, files);
        }
        return packs;
    }

    public static byte[] downloadFile(final String repoId, final String filePath) throws IOException, InterruptedException {
        final String url = downloadURL(repoId, filePath);
        try (final HTTPClient hc = new HTTPClient(ClientIdentification.browserAgent)) {
            hc.setRedirecting(true);
            return hc.GETbytes(url, null, null, false);
        }
    }

    /**
     * Stream a dataset file to disk with bounded memory, including files larger than 2 GiB.
     * Only a complete, nonempty download replaces the destination; partial files are removed on failure.
     *
     * @param repoId Hugging Face dataset repository ID
     * @param filePath file path in the repository's main revision
     * @param destination final local file, whose parent directories are created if needed
     * @throws IOException when the request, transfer, validation, or file operation fails
     */
    public static void downloadFile(final String repoId, final String filePath, final File destination) throws IOException {
        final String url = downloadURL(repoId, filePath);
        final Path target = destination.toPath().toAbsolutePath();
        Files.createDirectories(target.getParent());
        // Keep the temporary file on the same filesystem, with a suffix the Pack Manager ignores.
        final Path temporary = Files.createTempFile(target.getParent(), "yacy-pack-", ".part");
        try {
            try (final HTTPClient hc = new HTTPClient(ClientIdentification.browserAgent)) {
                hc.setRedirecting(true);
                hc.GET(url, false);
                if (hc.getStatusCode() != 200) {
                    throw new IOException("Hugging Face returned HTTP " + hc.getStatusCode());
                }
                if (hc.getHttpResponse().getEntity() == null) {
                    throw new IOException("downloaded file is empty");
                }
                final long expectedLength = hc.getHttpResponse().getEntity().getContentLength();
                try (final OutputStream output = new BufferedOutputStream(Files.newOutputStream(temporary), 64 * 1024)) {
                    hc.writeTo(output);
                }
                final long actualLength = Files.size(temporary);
                if (actualLength == 0) throw new IOException("downloaded file is empty");
                if (expectedLength >= 0 && actualLength != expectedLength) {
                    throw new IOException("incomplete download: expected " + expectedLength + " bytes, received " + actualLength);
                }
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (final AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String downloadURL(final String repoId, final String filePath) {
        Objects.requireNonNull(repoId);
        Objects.requireNonNull(filePath);
        return BASE + "/datasets/" + repoId + "/resolve/main/" + filePath + "?download=true";
    }

    // test
    public static void main(final String[] args) throws Exception {
        final List<String> repos = listYaCyPacks();
        System.out.println("Repos: " + repos);
        final String test_repo_id = repos.isEmpty() ? null : repos.get(0);
        final ArrayList<String> all_files = test_repo_id == null ? new ArrayList<>(0) : listFiles(test_repo_id, false);
        final ArrayList<String> jsonl_files = test_repo_id == null ? new ArrayList<>(0) : listFiles(test_repo_id, true);
        System.out.println("all   files in " + test_repo_id + ": " + all_files);
        System.out.println("jsonl files in " + test_repo_id + ": " + jsonl_files);
        if (!jsonl_files.isEmpty()) {
            final byte[] data = downloadFile(test_repo_id, jsonl_files.get(0));
            System.out.println("Downloaded " + data.length + " bytes from " + jsonl_files.get(0) + " in " + test_repo_id);
        }

        final Map<String, List<String>> map = getAllPacks(true);
        for (final Map.Entry<String, List<String>> entry : map.entrySet()) {
            final String repo = entry.getKey();
            final List<String> files = entry.getValue();
            System.out.println("Repo: " + repo + ", Files: " + files);
        }

    }
}
