package io.mosip.packet.core.dto.dbimport;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpMethod;

@Data
@Getter
@Setter
public class ApiDetails {
    private String endPoint;
    private HttpMethod httpMethod;
}
