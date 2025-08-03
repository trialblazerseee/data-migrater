package io.mosip.packet.core.dto.mvel;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Data
@Getter
@Setter
public class MvelDto implements Serializable {
    private String mvelFile;
    private List<MvelParameter> parameters;
}
