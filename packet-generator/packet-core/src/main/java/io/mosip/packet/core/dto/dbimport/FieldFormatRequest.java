package io.mosip.packet.core.dto.dbimport;

import io.mosip.packet.core.dto.mvel.MvelDto;
import io.mosip.packet.core.constant.FieldCategory;
import io.mosip.packet.core.constant.DataFormat;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

import static io.mosip.packet.core.constant.RegistrationConstants.DEFAULT_TABLE;

@Data
@Getter
@Setter
public class FieldFormatRequest {
    private String fieldName;
    private FieldCategory fieldCategory;
    private String primaryField;
    private DataFormat srcFormat;
    private DataFormat destFormat;
    private String fieldToMap;
    private String srcFieldForQualityScore;
    private DocumentAttributes documentAttributes;
    private MvelDto mvelExpressions;
    private List<FieldName> fieldList;

    public List<FieldName> getFieldList() {
        if(fieldList != null && fieldList.size() > 0)
            return fieldList;
        else {
            String[] fields = fieldName.split(",");
            fieldList = new ArrayList<>();

            for(int i = 0; i < fields.length; i++) {
                String field = fields[i];
                FieldName fieldName = new FieldName();
                if(field.contains(".")) {
                    fieldName.setFieldName(field.split("\\.")[1]);
                    fieldName.setTableName(field.split("\\.")[0]);
                } else {
                    fieldName.setFieldName(field);
                    fieldName.setTableName(DEFAULT_TABLE);
                }

                fieldList.add(fieldName);
            }
        }

        return fieldList;
    }

    public String getFieldNameWithoutSchema(String fieldName){
        if(fieldName.contains(".")) {
            return fieldName.split("\\.")[1];
        } else {
            return fieldName;
        }
    }
}
