package io.mosip.packet.core.util;

import io.mosip.packet.core.constant.FieldCategory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class QueryFormatter {
    public String replaceColumntoDataIfAny(String query, Map<FieldCategory, HashMap<String, Object>> dataMap) throws Exception {
        if(dataMap != null) {
            do {
                int startIndex = query.indexOf("${", 0);
                int endIndex = query.indexOf("}", 0);

                if (endIndex > 0) {
                    String columnText = query.substring(startIndex, endIndex+1).replace("${", "").replace("}", "");
                    String[] columnVal = columnText.split(":");
                    FieldCategory category = FieldCategory.valueOf(columnVal[0]);
                    String column = columnVal[1];
                    String type = columnVal.length > 2 ? columnVal[2] : "STRING";

                    validateType(type);
                    Object val = dataMap.get(category).get(column);
                    String fval = formatValueBasedOnType(type, val);
                    query = query.replace("'${" + columnText + "}'",  fval );
                    query = query.replace("${" + columnText + "}", fval);
                }
            } while(query.contains("${"));
        }

        return query;
    }

    private void validateType(String type) throws Exception {
        switch (type) {
            case "STRING":
                break;
            case "NUMBER":
                break;
            default:
                throw  new Exception("Invalid Value Type " + type);
        }
    }

    private String formatValueBasedOnType(String type, Object val) {
        if(val == null) return "NULL";

        switch (type) {
            case "NUMBER":
                return String.valueOf(val);
            default:
                return "'" + val + "'";
        }
    }

    public String queryFormatter(String query, Map<String, String> map) {
        for(Map.Entry<String, String> entry : map.entrySet()) {
            if(entry.getValue() == null) {
                query = query.replace("'<" + entry.getKey() + ">'", "NULL");
                query = query.replace("<" + entry.getKey() + ">", "NULL");
            } else {
                query = query.replace("<" + entry.getKey() + ">", entry.getValue());
            }
        }
        return query;
    }
}
