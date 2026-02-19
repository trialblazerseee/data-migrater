package io.mosip.packet.core.dto.dbimport;

import io.mosip.packet.core.constant.FieldType;
import io.mosip.packet.core.constant.FilterCondition;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class QueryFilter {
    private String filterField;
    private FieldType fieldType;
    private String fromValue;
    private String toValue;
    private String initialFromValue;
    private FilterCondition filterCondition;
    private ConjunctionFilter conjunctionFilter;

}
