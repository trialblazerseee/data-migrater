package io.mosip.packet.extractor.validator.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.HMACUtils2;
import io.mosip.packet.core.config.ApplicationConfig;
import io.mosip.packet.core.dto.dbimport.DBImportRequest;
import io.mosip.packet.core.logger.DataProcessLogger;
import io.mosip.packet.core.util.TrackerUtil;
import io.mosip.packet.extractor.validator.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_ID;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_NAME;

@Component
public class RequestHashValidator implements Validator {

    @Autowired
    private TrackerUtil trackerUtil;

    @Autowired
    private ApplicationConfig applicationConfig;

    private static Logger LOGGER = DataProcessLogger.getLogger(RequestHashValidator.class);

    @Override
    public Boolean validate(DBImportRequest dbImportRequest) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        String canonicalJson = mapper.writeValueAsString(dbImportRequest);
        String hmacValue1 = HMACUtils2.digestAsPlainText(canonicalJson.getBytes(StandardCharsets.UTF_8));
        List<String> hmacList = trackerUtil.getOffsetHash();

        for(String hash : hmacList) {
            if(hash.equals(hmacValue1)) {
                LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Existing request HASH value found : " + hmacValue1);
                System.out.println("Existing request HASH value found : " + hmacValue1);
                applicationConfig.setHashValue(hmacValue1);
                return true;
            }
        }

        LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Request HASH value not found. Creating new : " + hmacValue1);
        System.out.println("Request HASH value not found. Creating new : " + hmacValue1);
        applicationConfig.setHashValue(hmacValue1);
        return true;
    }
}
