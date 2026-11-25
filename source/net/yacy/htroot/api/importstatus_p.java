/**
 * Copyright 2026 by contributors to the YaCy project.
 * This program is free software under the GNU General Public License,
 * version 2 or (at your option) any later version. See gpl.txt.
 */

package net.yacy.htroot.api;

import org.json.JSONObject;
import org.json.JSONException;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.document.importer.JsonListImporter;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

/** Read-only import monitoring. The _p endpoint requires administrator access. */
public class importstatus_p {

    public static serverObjects respond(final RequestHeader header, final serverObjects post, final serverSwitch env) throws JSONException {
        final JsonListImporter current = JsonListImporter.job;
        final JSONObject job = current == null ? null : current.snapshot();
        final JSONObject status = new JSONObject().put("job", job == null ? JSONObject.NULL : job);
        final serverObjects prop = new serverObjects();
        prop.put("status", status.toString());
        return prop;
    }
}
