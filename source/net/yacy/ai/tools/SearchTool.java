/**
 *  SearchTool
 *  Copyright 2026 by Michael Peter Christen
 *  First released 28.03.2026 at https://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 */

package net.yacy.ai.tools;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import net.yacy.ai.RAGAugmentor;
import net.yacy.ai.ToolHandler;

public class SearchTool implements ToolHandler {

    private static final String NAME = "search";
    private static final int RESULT_COUNT = 10;

    @Override
    public JSONObject definition() throws JSONException {
        final JSONObject tool = new JSONObject(true);
        tool.put("type", "function");
        final JSONObject fn = new JSONObject(true);
        fn.put("name", NAME);
        fn.put("description", "Search for documents in the YaCy index. This is like web search.");

        final JSONObject params = new JSONObject(true);
        params.put("type", "object");
        final JSONObject props = new JSONObject(true);

        final JSONObject query = new JSONObject(true);
        query.put("type", "string");
        query.put("description", "Search query to run against YaCy.");
        props.put("query", query);

        params.put("properties", props);
        params.put("required", new JSONArray().put("query"));
        fn.put("parameters", params);
        tool.put("function", fn);
        return tool;
    }

    @Override
    public int maxCallsPerTurn() {
        return 3;
    }

    @Override
    public String execute(final String arguments) {
        final JSONObject args;
        try {
            args = (arguments == null || arguments.isEmpty()) ? new JSONObject(true) : new JSONObject(arguments);
        } catch (final JSONException e) {
            return ToolHandler.errorJson("Invalid arguments JSON");
        }

        final String query = args.optString("query", "").trim();
        if (query.isEmpty()) return ToolHandler.errorJson("Missing query");

        try {
            final JSONObject result = RAGAugmentor.searchResultsDocument(query, RESULT_COUNT, RAGAugmentor.defaultSearchIsGlobal());
            result.put("tool", NAME);
            result.put("query", query);
            result.put("global", RAGAugmentor.defaultSearchIsGlobal());
            return result.toString();
        } catch (final JSONException e) {
            return ToolHandler.errorJson("Failed to build search result document");
        }
    }
}
