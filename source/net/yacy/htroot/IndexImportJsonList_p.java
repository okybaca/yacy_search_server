/**
 *  IndexImportJsonList_p
 *  Copyright 23.10.2022 by Michael Peter Christen, @orbiterlab
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

package net.yacy.htroot;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.data.TransactionManager;
import net.yacy.document.importer.JsonListImporter;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class IndexImportJsonList_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();
        prop.put("importStatusToken", TransactionManager.getTransactionToken(header, "/IndexImportJsonList_p.html"));
        prop.put("error", 0);
        final JsonListImporter current = JsonListImporter.job;
        if (post != null && post.containsKey("abort")) {
            TransactionManager.checkPostTransaction(header, post);
            // An old browser tab must not stop a newer import.
            if (current != null && current.getJobId().equals(post.get("job", ""))) current.quit();
        } else if ((current == null || !current.isActive()) && post != null) {
            final String filename = post.get("file", "");
            final String url = post.get("url", "");
            File temporary = null;
            try {
                if (!filename.isEmpty()) {
                    new JsonListImporter(new File(filename), false, false, sb.index.fulltext()).start();
                } else if (!url.isEmpty()) {
                    final URI sourceUri = new URI(url);
                    final String sourcePath = sourceUri.getPath();
                    temporary = File.createTempFile("jsonlistimporter", ".jsonl");
                    try (final InputStream source = sourceUri.toURL().openStream();
                            final OutputStream target = Files.newOutputStream(temporary.toPath())) {
                        source.transferTo(target);
                    }
                    new JsonListImporter(temporary, sourcePath != null && sourcePath.endsWith(".gz"), true, sb.index.fulltext()).start();
                    temporary = null; // The importer owns successful temporary downloads.
                }
            } catch (final IOException | URISyntaxException | IllegalArgumentException | IllegalStateException e) {
                prop.put("error", 1);
                prop.putHTML("error_message", e.getMessage());
            } finally {
                if (temporary != null) temporary.delete();
            }
        }
        final JsonListImporter latest = JsonListImporter.job;
        prop.put("importActive", latest != null && latest.isActive());
        return prop;
    }
}
