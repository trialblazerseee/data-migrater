package io.mosip.packet.core.dto.mvel;

import io.mosip.packet.core.constant.mvel.ParameterType;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Data
@Getter
@Setter
public class MvelParameter implements Serializable {
    private String parameterName;
    private ParameterType parameterType;
    private String parameterValue;
}
