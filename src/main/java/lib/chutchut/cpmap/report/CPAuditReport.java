// Copyright (c) 2019-2020 Calum Ewart Hutton
// Distributed under the GNU General Public License v3.0+, see the accompanying
// file LICENSE or https://opensource.org/licenses/GPL-3.0.

package lib.chutchut.cpmap.report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import lib.chutchut.cpmap.vector.CPVector;
import lib.chutchut.cpmap.vector.QueryParser;

public class CPAuditReport {

    private HashMap<String, HashSet<String>> vectorTables = new HashMap<>();
    private HashMap<String, String> vectorTableSql = new HashMap<>();

    /*
     * Default constructor for Gson
     */
    private CPAuditReport() {}

    public CPAuditReport(HashMap<String, HashSet<String>> vectorTables) {
        this.vectorTables = vectorTables;
    }

    public CPAuditReport(HashMap<String, HashSet<String>> vectorTables, HashMap<String, String> vectorTableSql) {
        this.vectorTables = vectorTables;
        this.vectorTableSql = vectorTableSql;
    }

    public void update(CPAuditReport auditReport) {
        if (auditReport != null) {
            for (String vKey : auditReport.getVectorTables().keySet()) {
                if (vectorTables.containsKey(vKey)) {
                    vectorTables.get(vKey).addAll(auditReport.getAccessibleTables(vKey));
                } else {
                    vectorTables.put(vKey, auditReport.getAccessibleTables(vKey));
                }
            }

            if (auditReport.getVectorTableCreateSqlMap() != null && auditReport.getVectorTableCreateSqlMap().size() > 0) {
                for (String key : auditReport.getVectorTableCreateSqlMap().keySet()) {
                    vectorTableSql.put(key, auditReport.getVectorTableCreateSqlMap().get(key));
                }
            }
        }
    }

    public HashMap<String, HashSet<String>> getVectorTables() {
        return vectorTables;
    }

    public HashSet<String> getAccessibleTables(String vKey) {
        if (vectorTables.containsKey(vKey)) {
            return vectorTables.get(vKey);
        }
        return null;
    }

    public HashSet<String> getAccessibleTables(CPVector vector) {
        return getAccessibleTables(getVectorKey(vector));
    }

    public HashSet<String> getAllAccessibleTables() {
        HashSet<String> allTables = new HashSet<>();
        for (HashSet<String> vTables : vectorTables.values()) {
            allTables.addAll(vTables);
        }
        return allTables;
    }

    public static String getVectorKey(CPVector vector) {
        String vectorAuth = vector.getUri().getAuthority() != null ? vector.getUri().getAuthority() : String.valueOf(vector.hashCode());
        String vectorKey = vector.getTypeString() + "_" + vectorAuth;
        // Distinguish between query/update vectors
        if (vector.isQuery()) {
            vectorKey = "QUERY_" + vectorKey;
        } else {
            vectorKey = "UPDATE_" + vectorKey;
        }
        return vectorKey;
    }

    public static String getVectorTableKey(CPVector vector, String table) {
        return String.format("%s::%s", getVectorKey(vector), table);
    }

    public HashMap<String, String> getVectorTableCreateSqlMap() {
        return vectorTableSql;
    }

    public String getVectorTableCreateSql(CPVector vector, String table) {
        String key = getVectorTableKey(vector, table);
        if (vectorTableSql.containsKey(key) && vectorTableSql.get(key) != null) {
            return vectorTableSql.get(key);
        }
        return null;
    }

    public void setVectorTableCreateSql(CPVector vector, String table, String sql) {
        String key = getVectorTableKey(vector, table);
        vectorTableSql.put(key, sql);
    }

    public ArrayList<String> getVectorTableFields(CPVector vector, String table) {
        String key = getVectorTableKey(vector, table);
        if (vectorTableSql.containsKey(key) && vectorTableSql.get(key) != null) {
            return new QueryParser(vectorTableSql.get(key)).getCols(null);
        }
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("All accessible tables:\n\n");
        for (String table : getAllAccessibleTables()) {
            sb.append("[TABLE]: " + table + "\n");
        }
        return sb.toString();
    }
}
