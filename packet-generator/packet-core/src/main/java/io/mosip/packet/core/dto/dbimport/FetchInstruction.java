package io.mosip.packet.core.dto.dbimport;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Setter
@Getter
public class FetchInstruction {
    private FetchMode fetchMode;
    private Boolean isDataFormatRequired;
    private ApiDetails apiDetails;
}
