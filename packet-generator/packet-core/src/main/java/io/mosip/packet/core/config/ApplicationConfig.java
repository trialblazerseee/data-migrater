package io.mosip.packet.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "mosip.data-extractor.application.config")
@Component
@Setter
@Getter
public class ApplicationConfig {
    private boolean writeBiosdkResponseEnabled = false;
    private boolean trackerEnabled = true;
    private boolean runningAsBatch = false;
    private String predefinedSessionKey;
    private boolean referInernalJsonRequestFile = false;
}
