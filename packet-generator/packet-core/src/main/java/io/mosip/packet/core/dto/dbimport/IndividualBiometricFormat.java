package io.mosip.packet.core.dto.dbimport;

import io.mosip.packet.core.constant.BioSubType;
import io.mosip.packet.core.constant.DataFormat;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Data
@Getter
@Setter
public class IndividualBiometricFormat {
    private BioSubType subType;
    private DataFormat srcImageFormat;
    private List<DataFormat> destImageFormat;
}
