package io.mosip.packet.core.dto.dbimport;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@Data
public class FieldName implements Serializable {
    private String tableName;
    private String modifiedFieldName;
    private String originalFieldName;
}
