// Copyright (c) 2019-2020 Calum Ewart Hutton
// Distributed under the GNU General Public License v3.0+, see the accompanying
// file LICENSE or https://opensource.org/licenses/GPL-3.0.

package lib.chutchut.cpmap.vector;

import android.content.ContentValues;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lib.chutchut.cpmap.payload.BooleanBlindPayload;
import lib.chutchut.cpmap.payload.HeuristicPayload;
import lib.chutchut.cpmap.payload.PathTraversalPayload;
import lib.chutchut.cpmap.payload.ProjectionPayload;
import lib.chutchut.cpmap.payload.SelectionPayload;
import lib.chutchut.cpmap.payload.UnionPayload;
import lib.chutchut.cpmap.payload.base.InjectionPayload;
import lib.chutchut.cpmap.payload.base.Payload;
import lib.chutchut.cpmap.report.CPAuditReport;
import lib.chutchut.cpmap.util.Util;


public class CPVector {

    public static final int UNKNOWN = 470;
    public static final int URI_ID = 471;
    public static final int URI_SEGMENT = 479;
    public static final int PROJECTION = 472;
    public static final int SORT_ORDER = 474;
    public static final int WHERE = 475;
    public static final int CVALS_KEY = 476;
    public static final int QPARAM_KEY = 477;
    public static final int QPARAM_VAL = 478;

    private int vectorType = UNKNOWN;
    public static final String injectionChar = "*";

    private String originalUri;
    private String[] projection;
    private String[] selectionArgs;
    private String sortOrder;
    private String where;
    private HashMap<String, String> valuesMap;
    private HashMap<String, String> qParams;

    private Bundle vectorConfig = new Bundle();

    public static int[] readVectorTypes = new int[] {
            CPVector.URI_ID,
            CPVector.URI_SEGMENT,
            CPVector.PROJECTION,
            CPVector.WHERE,
            CPVector.SORT_ORDER,
            CPVector.QPARAM_KEY,
            CPVector.QPARAM_VAL
    };

    public static int[] writeVectorTypes = new int[] {
            CPVector.URI_ID,
            CPVector.URI_SEGMENT,
            CPVector.CVALS_KEY,
            CPVector.WHERE,
            CPVector.QPARAM_KEY,
            CPVector.QPARAM_VAL
    };

    /*
     * Default constructor for Gson
     */
    private CPVector() {}

    public CPVector(String uri, String[] projection, String[] selection, String sort, String where, ContentValues vals, HashMap<String, String> qParams, ProviderInfo providerInfo) {
        this.originalUri = uri;
        this.projection = projection;
        this.selectionArgs = selection;
        this.sortOrder = sort;
        this.where = where;
        this.valuesMap = (vals != null) ? valsToMap(vals) : null;
        this.qParams = qParams;
        setUri(Uri.parse(uri));
        parse();
        if (providerInfo != null) {
            setProviderProperties(providerInfo);
        }
    }

    public CPVector(String uri, String[] projection, String[] selection, String sort, String where, ContentValues vals, HashMap<String, String> qParams, Bundle vectorConfig) {
        this.originalUri = uri;
        this.projection = projection;
        this.selectionArgs = selection;
        this.sortOrder = sort;
        this.where = where;
        this.valuesMap = (vals != null) ? valsToMap(vals) : null;
        this.qParams = qParams;
        this.vectorConfig = vectorConfig;
        setUri(Uri.parse(uri));
        parse();
    }

    private Object get(String key) {
        return (vectorConfig.containsKey(key)) ? vectorConfig.get(key) : null;
    }

    public void setContentUri(String contentUri) {
        vectorConfig.putString("content_uri", contentUri);
    }

    public String getContentUri() {
        return (String) get("content_uri");
    }

    public void setQuery(String query) {
        vectorConfig.putString("query", query);
    }

    public String getQuery() {
        return (String) get("query");
    }

    public void setProviderClass(String pCls) {
        vectorConfig.putString("provider_class", pCls);
    }

    public String getProviderClass() {
        return (String) get("provider_class");
    }

    public void setProviderPermission(String rPerm) {
        vectorConfig.putString("provider_permission", rPerm);
    }

    public String getProviderPermission() {
        return (String) get("provider_permission");
    }

    public void setPathPermission(String rPathPerm) {
        vectorConfig.putString("path_permission", rPathPerm);
    }

    public String getPathPermission() {
        return (String) get("path_permission");
    }

    public void setQueryFields(Set<String> fields) {
        if (isQuery()) {
            vectorConfig.putStringArrayList("query_fields", new ArrayList<>(fields));
        }
    }

    public Set<String> getFields() {
        if (isUpdate()) {
            return valuesMap.keySet();
        } else {
            Set<String> fields = new HashSet<>();
            ArrayList<String> fieldList = (ArrayList<String>) get("query_fields");
            if (fieldList != null) {
                fields.addAll(fieldList);
            }
            return fields;
        }
    }

    public CPVector copy() {
        return copy(getUri(false));
    }

    public CPVector copy(Uri uri) {
        return new CPVector(uri.toString(), projection, selectionArgs, sortOrder, where, mapToVals(), qParams, new Bundle(vectorConfig));
    }

    public Uri getUri() {
        // Default to true
        return getUri(true);
    }

    public Uri getUri(boolean withQuery) {
        try {
            String qStr = getQueryParamString();
            if (qStr != null && withQuery) {
                return Uri.parse(getContentUri() + qStr);
            } else {
                return Uri.parse(getContentUri());
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void setUri(Uri uri) {
        // If query params present, and not already set explicitly, strip them from the uri
        if (uri.getQuery() != null && qParams == null) {
            HashMap<String, String> paramMap = new HashMap<>();
            for (String key : uri.getQueryParameterNames()) {
                String val = uri.getQueryParameter(key);
                paramMap.put(key, val != null ? val : "");
            }
            qParams = paramMap;
        }
        String contentUri = uri.toString().contains("?") ? uri.toString().substring(0, uri.toString().indexOf("?")) : uri.toString();
        if (vectorType == URI_ID && !contentUri.endsWith(injectionChar)) {
            // If this is a URI vector ensure setting the URI doesnt clobber the substitution char
            contentUri += injectionChar;
        }
        setContentUri(contentUri);
    }

    public void updateUri(ProviderInfo providerInfo, Uri uri) {
        setUri(uri);
        if (providerInfo != null) {
            setProviderProperties(providerInfo);
        }
    }

    public void setValues(ContentValues vals) {
        valuesMap = valsToMap(vals);
    }

    public String[] getProjection() {
        return projection;
    }

    public String getWhere() {
        return where;
    }

    public void setWhere(String whr) {
        where = whr;
    }

    public String getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(String sort) {
        sortOrder = sort;
    }

    public ContentValues getValues() {
        return mapToVals();
    }

    public String[] getSelectionArgs() {
        return selectionArgs;
    }

    public int getType() {
        return vectorType;
    }

    private void setType(int type) {
        vectorType = type;
    }

    private String getVectorTableFromSql() {
        String vectorTable = null;
        if (getQuery() != null) {
            Pattern tblPatternQuery = Pattern.compile("FROM\\s+([\\w-_<>]+)\\s?", Pattern.CASE_INSENSITIVE);
            Pattern tblPatternUpdate = Pattern.compile("UPDATE\\s+([\\w-_<>]+)\\sSET", Pattern.CASE_INSENSITIVE);
            Matcher matchQuery = tblPatternQuery.matcher(getQuery());
            Matcher matchUpd = tblPatternUpdate.matcher(getQuery());
            if (matchQuery.find()) {
                vectorTable = matchQuery.group(1);
            } else if (matchUpd.find()) {
                vectorTable = matchUpd.group(1);
            }
        }
        return vectorTable;
    }

    public boolean isTableInjectionVector() {
        String vectorTable = getVectorTableFromSql();
        return vectorTable != null && vectorTable.trim().equals("<injection>");
    }

    public boolean isTableInInjectionVector() {
        String vectorTable = getVectorTableFromSql();
        return !isTableInjectionVector() && vectorTable != null && vectorTable.trim().contains("<injection>");
    }

    public String getTable() {
        String vectorTable = getVectorTableFromSql();
        if (vectorTable != null) {
            // Replace <injection> marker depending on context
            if (vectorTable.trim().equals("<injection>")) {
                // Exact match, replace with default table
                vectorTable = InjectionPayload.getDefaultTable();
            } else if (vectorTable.trim().contains("<injection>")) {
                // Contains the marker, strip it
                vectorTable = vectorTable.replace("<injection>", "");
            }
        }
        return vectorTable;
    }

    private void setProviderProperties(ProviderInfo providerInfo) {
        setProviderClass(providerInfo.name);
        if (isQuery()) {
            setProviderPermission(providerInfo.readPermission);
            setPathPermission(Util.getPathPermission(providerInfo, true, getUri()));
        } else if (isUpdate()) {
            setProviderPermission(providerInfo.writePermission);
            setPathPermission(Util.getPathPermission(providerInfo, false, getUri()));
        }
    }

    public static ContentValues getBasicContentValuesForUpdateVector() {
        int randint = Util.getRandomInt();
        ContentValues vals = new ContentValues();
        vals.put(InjectionPayload.getDefaultField(), String.valueOf(randint));
        return vals;
    }

    public boolean isUpdate() {
        if (getQuery() == null) {
            // If query is null rely on vector type list
            return Util.listContains(writeVectorTypes, vectorType) && valuesMap != null && valuesMap.keySet().size() > 0;
        }
        return getQuery().trim().toLowerCase().startsWith("update ");
    }

    public boolean isQuery() {
        if (getQuery() == null) {
            // If query is null rely on vector type list
            return Util.listContains(readVectorTypes, vectorType) && (valuesMap == null || valuesMap.keySet().size() == 0);
        }
        return getQuery().trim().toLowerCase().startsWith("select ");
    }

    public void addQueryParam(String key, String val) {
        if (qParams != null) {
           qParams.put(key, val);
        }
    }

    public void removeQueryParam(String key) {
        if (qParams != null) {
            qParams.remove(key);
        }
    }

    public void clearQueryParams() {
        if (qParams != null) {
            qParams.clear();
        }
    }

    private ContentValues mapToVals() {
        if (valuesMap == null) {
            return null;
        }
        ContentValues vals = new ContentValues();
        for (String valKey : valuesMap.keySet()) {
            vals.put(valKey, valuesMap.get(valKey));
        }
        return vals;
    }

    private HashMap<String, String> valsToMap(ContentValues contentValues) {
        if (contentValues == null) {
            return valsToMap(CPVector.getBasicContentValuesForUpdateVector());
        }
        HashMap<String, String> valMap = new HashMap<>();
        for (String valKey : contentValues.keySet()) {
            valMap.put(valKey, contentValues.getAsString(valKey));
        }
        return valMap;
    }

    private String getQueryParamString() {
        if (qParams == null || qParams.size() == 0) {
            return null;
        }
        StringBuilder qParamStr = new StringBuilder();
        boolean first = true;
        for (String key : qParams.keySet()) {
            if (first) {
                qParamStr.append("?");
                first = false;
            } else {
                qParamStr.append("&");
            }

            // URL-encode the key and the val (cos there might be some *invalid* chars in the key..)
            String encodedKey = Util.urlEncodeString(key);
            String encodedVal = Util.urlEncodeString(qParams.get(key));
            qParamStr.append(String.format("%s=%s", encodedKey, encodedVal));
        }
        return qParamStr.toString();
    }

    public String getQueryParamField() {
        if (originalUri != null && qParams != null) {
            Set<String> paramFields = Uri.parse(originalUri).getQueryParameterNames();
            for (String field : paramFields) {
                if (!qParams.containsKey(field) || qParams.get(field).contains(injectionChar)) {
                    return field;
                }
            }
        }
        return null;
    }

    public void setProjection(String[] proj) {
        projection = proj;
    }

    public void setSelection(String[] selection) {
        selectionArgs = selection;
    }

    public ArrayList<String> getColsWithFilter(String filter) {
        QueryParser queryParser = new QueryParser(this);
        ArrayList<String> colsFromQuery = queryParser.getCols(filter);
        // Assumes there is a query to parse.. there might not be so use a fallback
        ArrayList<String> fallback = new ArrayList<>(getFields());
        if (colsFromQuery.size() > 0) {
            return colsFromQuery;
        } else if (filter == null) {
            return fallback;
        } else {
            ArrayList<String> filteredFallback = new ArrayList<>();
            for (String fbCol : fallback) {
                if (fbCol.contains(filter)) {
                    filteredFallback.add(fbCol);
                }
            }
            return filteredFallback;
        }
    }

    public static ArrayList<CPVector> getVectorsFromUri(ProviderInfo providerInfo, String uri) {
        Uri parsedUri = Uri.parse(uri);

        // If the parsed uri or path of the uri cannot be obtained, assume invalid
        if (parsedUri == null || parsedUri.getPath() == null) {
            return null;
        }

        // If the uri contains a placeholder segment (/*/) strip it
        if (parsedUri.toString().contains("/" + injectionChar + "/")) {
            parsedUri = Uri.parse(parsedUri.toString().replace("/" + injectionChar + "/", "/"));
        }

        // If the uri path ends in a placeholder strip it
        if (parsedUri.getPath().trim().endsWith(injectionChar)) {
            parsedUri = Uri.parse(parsedUri.toString().replace(injectionChar, ""));
        }

        ArrayList<CPVector> genVectors = new ArrayList<>();

        // Add Uri vectors for uris which do not end a slash
        if (!parsedUri.getPath().endsWith("/")) {
            Uri uriPathWithInj = parsedUri.buildUpon().encodedPath(parsedUri.getPath() + injectionChar).build();
            CPVector idVector = new CPVector(uriPathWithInj.toString(), null, null, null, null, null, null, providerInfo);
            genVectors.add(idVector);
            // For vectors with at least two path segments, create uri vectors out of path segments (excluding the final segment)
            if (parsedUri.getPathSegments().size() > 1) {
                for (int i = 0; i < parsedUri.getPathSegments().size() - 1; i++) {
                    String segment = parsedUri.getPathSegments().get(i);
                    String path = "";
                    for (String pathSeg : parsedUri.getPathSegments()) {
                        path += "/";
                        if (pathSeg.equals(segment)) {
                            path += injectionChar;
                        } else {
                            path += pathSeg;
                        }
                    }
                    Uri uriPathWithSegmentInj = parsedUri.buildUpon().encodedPath(path).build();
                    CPVector uriSegmentVector = new CPVector(uriPathWithSegmentInj.toString(), null, null, null, null, null, null, providerInfo);
                    genVectors.add(uriSegmentVector);
                }
            }
        }

        // Projection vector
        genVectors.add(new CPVector(parsedUri.toString(), new String[] {injectionChar}, null, null, null, null, null, providerInfo));
        // Sort vector
        genVectors.add(new CPVector(parsedUri.toString(), null, null, injectionChar, null, null, null, providerInfo));
        // Where vector
        genVectors.add(new CPVector(parsedUri.toString(), null, null, null, injectionChar, null, null, providerInfo));
        // ContentValues keys
        ContentValues valKey = new ContentValues();
        valKey.put(injectionChar, "123");
        genVectors.add(new CPVector(parsedUri.toString(), null, null, null, null, valKey, null, providerInfo));

        // Check for tainted query params before generating them
        boolean generateQueryVectors = true;
        if (parsedUri.getQuery() != null) {
            for (String key : parsedUri.getQueryParameterNames()) {
                if (key.endsWith("'") || key.endsWith("\"")) {
                    generateQueryVectors = false;
                    break;
                }
                if (parsedUri.getQueryParameter(key) != null && (parsedUri.getQueryParameter(key).endsWith("'") || parsedUri.getQueryParameter(key).endsWith("\""))) {
                    generateQueryVectors = false;
                    break;
                }
            }
        }

        if (generateQueryVectors) {
            // No query string, use dummy vals
            if (parsedUri.getQuery() == null || parsedUri.getQuery().trim().length() == 0) {
                // Query param keys
                HashMap<String, String> qParamKeyMap = new HashMap<>();
                qParamKeyMap.put(injectionChar, "123");
                genVectors.add(new CPVector(parsedUri.toString(), null, null, null, null, null, qParamKeyMap, providerInfo));
                // Query param vals
                HashMap<String, String> qParamValMap = new HashMap<>();
                qParamValMap.put(InjectionPayload.getDefaultField(), injectionChar);
                genVectors.add(new CPVector(parsedUri.toString(), null, null, null, null, null, qParamValMap, providerInfo));
            } else {
                // Make a vector for each actual query string key/val
                for (String key : parsedUri.getQueryParameterNames()) {
                    HashMap<String, String> qParamKeyMap = new HashMap<>();
                    HashMap<String, String> qParamValMap = new HashMap<>();
                    for (String innerKey : parsedUri.getQueryParameterNames()) {
                        String val = parsedUri.getQueryParameter(innerKey);
                        // If val is undefined use a dummy
                        if (val == null || val.trim().length() == 0) {
                            val = "123";
                        }

                        if (innerKey.equals(key)) {
                            qParamKeyMap.put(injectionChar, val);
                            qParamValMap.put(innerKey, injectionChar);
                        } else {
                            qParamKeyMap.put(innerKey, val);
                            qParamValMap.put(innerKey, val);
                        }
                    }
                    // Query param keys
                    genVectors.add(new CPVector(parsedUri.toString(), null, null, null, null, null, qParamKeyMap, providerInfo));
                    // Query param vals
                    genVectors.add(new CPVector(parsedUri.toString(), null, null, null, null, null, qParamValMap, providerInfo));
                }
            }
        }
        return genVectors;
    }

    private Payload initPayloadWithVector(Payload payload) {
        switch (payload.getType()) {
            default:
            case HeuristicPayload.TYPE:
                return new HeuristicPayload.Builder(((HeuristicPayload) payload), this).build();
            case ProjectionPayload.TYPE:
                return new ProjectionPayload.Builder(((ProjectionPayload) payload), this).build();
            case BooleanBlindPayload.TYPE:
                return new BooleanBlindPayload.Builder(((BooleanBlindPayload) payload), this).build();
            case UnionPayload.TYPE:
                return new UnionPayload.Builder(((UnionPayload) payload), this).build();
            case SelectionPayload.TYPE:
                return new SelectionPayload.Builder(((SelectionPayload) payload), this).build();
            case PathTraversalPayload.TYPE:
                return new PathTraversalPayload.Builder(((PathTraversalPayload) payload), this).build();
        }
    }

    public CPVector getWithPayload(Payload payload) {
        CPVector copy = copy();
        payload = initPayloadWithVector(payload);
        return copy.getWithPayloadString(payload.getPayload());
    }

    public CPVector getWithPayloadString(String payload) {
        if (!isValid() || isUnknownType()) {
            return null;
        }
        CPVector plVector = null;
        switch (vectorType) {
            case URI_ID:
            case URI_SEGMENT:
                String uri = getContentUri().replace(injectionChar, payload);
                plVector = new CPVector(uri, projection, selectionArgs, sortOrder, where, mapToVals(), qParams, vectorConfig);
                break;
            case PROJECTION:
                if (!Util.nullOrEmpty(projection)) {
                    String[] proj = new String[projection.length];
                    for (int i = 0; i < projection.length; i++) {
                        if (projection[i].contains(injectionChar)) {
                            proj[i] = projection[i].replace(injectionChar, payload);
                        } else {
                            proj[i] = projection[i];
                        }
                    }
                    plVector = new CPVector(getContentUri(), proj, selectionArgs, sortOrder, where, mapToVals(), qParams, vectorConfig);
                }
                break;
            case SORT_ORDER:
                if (sortOrder != null) {
                    String sort = sortOrder.replace(injectionChar, payload);
                    plVector = new CPVector(getContentUri(), projection, selectionArgs, sort, where, mapToVals(), qParams, vectorConfig);
                }
                break;
            case WHERE:
                if (where != null) {
                    String whr = where.replace(injectionChar, payload);
                    plVector = new CPVector(getContentUri(), projection, selectionArgs, sortOrder, whr, mapToVals(), qParams, vectorConfig);
                }
                break;
            case CVALS_KEY:
                if (!Util.nullOrEmpty(mapToVals())) {
                    ContentValues valKey = new ContentValues();
                    for (String key : valuesMap.keySet()) {
                        if (key.contains(injectionChar)) {
                            valKey.put(key.replace(injectionChar, payload), valuesMap.get(key));
                        } else {
                            valKey.put(key, valuesMap.get(key));
                        }
                    }
                    plVector = new CPVector(getContentUri(), projection, selectionArgs, sortOrder, where, valKey, qParams, vectorConfig);
                }
                break;
            case QPARAM_KEY:
                if (getQueryParamString() != null) {
                    HashMap<String, String>  qparamKey = new HashMap<>();
                    for (String key : qParams.keySet()) {
                        if (key.contains(injectionChar)) {
                            qparamKey.put(key.replace(injectionChar, payload), qParams.get(key));
                        } else {
                            qparamKey.put(key, qParams.get(key));
                        }
                    }
                    plVector = new CPVector(getContentUri(), projection, selectionArgs, sortOrder, where, mapToVals(), qparamKey, vectorConfig);
                }
                break;
            case QPARAM_VAL:
                if (getQueryParamString() != null) {
                    HashMap<String, String> qparamVal = new HashMap<>();
                    for (String key : qParams.keySet()) {
                        if (qParams.get(key).contains(injectionChar)) {
                            qparamVal.put(key, qParams.get(key).replace(injectionChar, payload));
                        } else {
                            qparamVal.put(key, qParams.get(key));
                        }
                    }
                    plVector = new CPVector(getContentUri(), projection, selectionArgs, sortOrder, where, mapToVals(), qparamVal, vectorConfig);
                }
                break;
        }

        if (plVector != null) {
            plVector.setType(vectorType);
        }

        return plVector;
    }

    private void parse() {
        // Try parsing the URI
        if (getUri() == null || !isUnknownType()) {
            return;
        }
        // Assumes only one injection marker..
        if (getContentUri() != null && getContentUri().endsWith(injectionChar)) {
            vectorType = URI_ID;
        } else if (Util.listContains(getUri().getPathSegments(), injectionChar, true)) {
            vectorType = URI_SEGMENT;
        } else if (Util.listContains(projection, injectionChar, false)) {
            vectorType = PROJECTION;
        } else if (sortOrder != null && sortOrder.contains(injectionChar)) {
            vectorType = SORT_ORDER;
        } else if (where != null && where.contains(injectionChar)) {
            vectorType = WHERE;
        } else if (valuesMap != null && Util.listContains(valuesMap.keySet().toArray(), injectionChar, false)) {
            vectorType = CVALS_KEY;
        } else if (qParams != null && Util.listContains(qParams.keySet().toArray(), injectionChar, false)) {
            vectorType = QPARAM_KEY;
        } else if (qParams != null && Util.listContains(qParams.values().toArray(), injectionChar, false)) {
            vectorType = QPARAM_VAL;
        }
    }

    public boolean isValid() {
        return getUri() != null;
    }

    public boolean isUnknownType() {
        return vectorType == UNKNOWN;
    }

    public String getIdentifier() {
        // Use tables as identifiers for vectors with queries set, otherwise use a hash of the read/write fields
        if (getTable() != null) {
            return CPAuditReport.getVectorTableKey(this, getTable());
        } else {
            return Util.getHashStr(getFields());
        }
    }

    public String getTypeString() {
        return getTypeString(vectorType);
    }

    public static String getTypeString(int vType) {
        switch (vType) {
            case URI_ID:
                return "URI_ID";
            case URI_SEGMENT:
                return "URI_SEGMENT";
            case PROJECTION:
                return "PROJECTION";
            case SORT_ORDER:
                return "SORT_ORDER";
            case WHERE:
                return "WHERE";
            case CVALS_KEY:
                return "CONTENT_VALS_KEY";
            case QPARAM_KEY:
                return "QUERY_PARAM_KEY";
            case QPARAM_VAL:
                return "QUERY_PARAM_VAL";
            default:
            case UNKNOWN:
                return "UNKNOWN";
        }
    }

    private String getOpTypeString() {
        if (isQuery()) {
            return "QUERY";
        } else {
            return "UPDATE";
        }
    }

    public String toString() {
        if (getUri() == null) {
            return null;
        }
        return String.format("[%s] Uri: %s Type: %s", getOpTypeString(), getUri(), getTypeString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(Util.getHashStr(getOpTypeString(), getContentUri(), vectorType, projection, where, selectionArgs, sortOrder, valuesMap, qParams, isQuery(), isUpdate(), getFields()));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (!(obj instanceof CPVector)) {
            return false;
        }

        CPVector vector = (CPVector) obj;
        return hashCode() == vector.hashCode();
    }
}
