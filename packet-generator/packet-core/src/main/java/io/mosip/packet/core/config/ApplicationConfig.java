package io.mosip.packet.core.config;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.packet.core.logger.DataProcessLogger;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import java.util.Scanner;
import java.util.UUID;

import static io.mosip.packet.core.constant.GlobalConfig.getActivityName;
import static io.mosip.packet.core.constant.RegistrationConstants.*;

@ConfigurationProperties(prefix = "mosip.data-extractor.application.config")
@Component
@Setter
@Getter
public class ApplicationConfig {
    Logger LOGGER = DataProcessLogger.getLogger(ApplicationConfig.class);
    private boolean writeBiosdkResponseEnabled = false;
    private boolean trackerEnabled = true;
    private boolean runningAsBatch = false;
    private String predefinedSessionKey = UUID.randomUUID().toString();
    private boolean referInernalJsonRequestFile = false;

    @PostConstruct
    public void init() {
        if(predefinedSessionKey == null || predefinedSessionKey.isEmpty())
            predefinedSessionKey = UUID.randomUUID().toString();

        if(runningAsBatch) {
            System.out.println("This environment running as a 'BATCH' mode");
            LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "This environment running as a 'BATCH' mode");
        } else {
            System.out.println("This environment running as a 'INTERACTIVE' mode");
            LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "This environment running as a 'INTERACTIVE' mode");
        }

        if (!runningAsBatch) {
            do {
                System.out.println("Current Session Key is " + predefinedSessionKey + ". Please Enter New Session Key in-case Change.");
                Scanner scanner = new Scanner(System.in);
                String sessionKey = scanner.next();
                if (sessionKey != null && !sessionKey.isEmpty()) {
                    predefinedSessionKey = sessionKey.trim().toUpperCase();
                    break;
                }
            } while (predefinedSessionKey == null || predefinedSessionKey.isEmpty());
        } else {
            System.out.println("Current Session Key is " + predefinedSessionKey);
        }
        LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Current Session Key is " + predefinedSessionKey);
    }
}
