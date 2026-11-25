// IndexPackManager_p.java
// -----------------------
// (C) 2025 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// LICENSE
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.htroot;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.json.JSONException;
import org.json.JSONObject;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.document.importer.JsonListImporter;
import net.yacy.search.Switchboard;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class IndexPackManager_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) {
        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        if (post != null) {
            final String mvfrom = post.get("mvfrom", "");
            final String mvto = post.get("mvto", "");
            final String delete = post.get("delete", "0");
            final String file = post.get("file", "");

            if (!mvfrom.isEmpty() && !mvto.isEmpty() && !file.isEmpty()) {
                // move as requested
                if ("loaded".equals(mvfrom) && "hold".equals(mvto)) {
                    new File(sb.packsLoadedPath, file).renameTo(new File(sb.packsHoldPath, file));
                }
                if ("hold".equals(mvfrom) && "load".equals(mvto)) {
                    new File(sb.packsHoldPath, file).renameTo(new File(sb.packsLoadPath, file));
                }
            }
            if (delete.equals("1") && !file.isEmpty()) {
                // delete as requested, files to delete can only be in hold
                new File(sb.packsHoldPath, file).delete();
            }
        }

        final JsonListImporter importer = JsonListImporter.job;
        JSONObject importStatus = null;
        if (importer != null) {
            try {
                importStatus = importer.snapshot();
            } catch (final JSONException e) {
                sb.log.warn("Could not read JSON import status: " + e.getMessage());
            }
        }

        // Show pack folders, with an initial progress snapshot for the active JSON pack.
        int i = 0; boolean dark = true;
        for (final String file: sb.packsInHold()) {
            prop.putHTML("packs-hold_" + i + "_file", file);
            prop.put("packs-hold_" + i + "_size", new File(sb.packsHoldPath, file).length() / 1024);
            prop.put("packs-hold_" + i + "_process",
                "<a class=\"btn btn-primary\" href=\"IndexPackManager_p.html?mvfrom=hold&mvto=load&file=" + URLEncoder.encode(file, StandardCharsets.UTF_8) + "\" role=\"button\">load</a>&nbsp;" +
                "<a class=\"btn btn-primary\" href=\"IndexPackManager_p.html?delete=1&file=" + URLEncoder.encode(file, StandardCharsets.UTF_8) + "\" role=\"button\">delete</a>"
            );
            prop.put("packs-hold_" + i + "_dark", dark ? "1" : "0");
            i++;
            dark = !dark;
        }
        if (i == 0) {
            prop.put("packs-hold_0_file", "&lt;no packs in hold&gt;");
            prop.put("packs-hold_0_size", "");
            prop.put("packs-hold_" + i + "_process", "");
            prop.put("packs-hold_0_dark", true);
            i = 1;
        }
        prop.put("packs-hold", i);
        i = 0; dark = true;
        for (final String file: sb.packsInLoad()) {
            prop.putHTML("packs-load_" + i + "_file", file);
            prop.put("packs-load_" + i + "_size", new File(sb.packsLoadPath, file).length() / 1024);
            final String progressPrefix = "packs-load_" + i + "_json_";
            final boolean jsonPack = file.endsWith(".jsonl") || file.endsWith(".jsonl.gz")
                    || file.endsWith(".jsonlist") || file.endsWith(".jsonlist.gz")
                    || file.endsWith(".flatjson") || file.endsWith(".flatjson.gz");
            prop.put("packs-load_" + i + "_json", jsonPack);
            if (jsonPack) {
                final boolean sourceMatches = importer != null && importer.isSourceFile(new File(sb.packsLoadPath, file));
                final boolean active = sourceMatches && importStatus != null && importStatus.optBoolean("active");
                final int phase;
                if (active) {
                    phase = switch (importStatus.optString("state")) {
                        case "DRAINING" -> 2;
                        case "STOPPING" -> 3;
                        default -> 1;
                    };
                } else if (sourceMatches && importer.isActive() && importStatus == null) {
                    phase = 4;
                } else {
                    phase = 0;
                }
                final long total = active ? importStatus.optLong("totalBytes") : 0;
                final double percent = total > 0 ? Math.min(100, importStatus.optLong("bytesRead") * 100.0 / total) : 0;
                prop.put(progressPrefix + "phase", phase);
                prop.put(progressPrefix + "active", active);
                prop.put(progressPrefix + "job", active ? importer.getJobId() : "");
                prop.put(progressPrefix + "percent", Double.toString(percent));
                prop.putNum(progressPrefix + "percent-label", percent);
                prop.putNum(progressPrefix + "submitted", active ? importStatus.optLong("submitted") : 0);
                prop.putNum(progressPrefix + "speed", active ? importStatus.optDouble("documentsPerSecond") : 0);
            }
            prop.put("packs-load_" + i + "_dark", dark ? "1" : "0");
            i++;
            dark = !dark;
        }
        if (i == 0) {
            prop.put("packs-load_0_file", "&lt;no packs in load&gt;");
            prop.put("packs-load_0_size", "");
            prop.put("packs-load_0_json", 2);
            prop.put("packs-load_0_dark", true);
            i = 1;
        }
        prop.put("packs-load", i);
        i = 0; dark = true;
        for (final String file: sb.packsInLoaded()) {
            prop.putHTML("packs-loaded_" + i + "_file", file);
            prop.put("packs-loaded_" + i + "_size", new File(sb.packsLoadedPath, file).length() / 1024);
            prop.put("packs-loaded_" + i + "_process", "<a class=\"btn btn-primary\" href=\"IndexPackManager_p.html?mvfrom=loaded&mvto=hold&file=" + URLEncoder.encode(file, StandardCharsets.UTF_8) + "\" role=\"button\">to: hold</a>");
            prop.put("packs-loaded_" + i + "_dark", dark ? "1" : "0");
            i++;
            dark = !dark;
        }
        if (i == 0) {
            prop.put("packs-loaded_0_file", "&lt;no packs in loaded&gt;");
            prop.put("packs-loaded_0_size", "");
            prop.put("packs-loaded_0_process", "");
            prop.put("packs-loaded_0_dark", true);
            i = 1;
        }
        prop.put("packs-loaded", i);

        return prop;
    }

}
