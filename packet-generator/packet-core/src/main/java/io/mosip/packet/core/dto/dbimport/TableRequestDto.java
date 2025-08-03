package io.mosip.packet.core.dto.dbimport;

import io.mosip.packet.core.constant.FieldCategory;
import io.mosip.packet.core.constant.QuerySelection;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Data
@Getter
@Setter
public class TableRequestDto implements Comparable<TableRequestDto>, Serializable {
    private QuerySelection queryType;
    private Integer executionOrderSequence;
    private String tableName;
    private String sqlQuery;
    private String[] nonIdSchemaTableFields;
    private String[] nonIdSchemaNonTableFields;
    private List<QueryFilter> filters;
    private String[] orderBy;
    private String tableNameWithOutSchema;

    @Override
    public int compareTo(TableRequestDto o) {
        return this.executionOrderSequence.compareTo(o.executionOrderSequence);
    }

    public String getTableNameWithOutSchema() {
        if(tableNameWithOutSchema == null) {
            if(tableName.contains("."))
                tableNameWithOutSchema=tableName.split("\\.")[1];
            else
                tableNameWithOutSchema=tableName;
        }
        return tableNameWithOutSchema;
    }
}
