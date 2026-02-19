package io.mosip.packet.data.datapostprocessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.packet.core.constant.FieldCategory;
import io.mosip.packet.core.constant.tracker.TrackerStatus;
import io.mosip.packet.core.dto.DataProcessorResponseDto;
import io.mosip.packet.core.dto.dbimport.DBImportRequest;
import io.mosip.packet.core.dto.packet.BioData;
import io.mosip.packet.core.exception.ExceptionUtils;
import io.mosip.packet.core.logger.DataProcessLogger;
import io.mosip.packet.core.service.thread.ResultSetter;
import io.mosip.packet.core.spi.dataprocessor.DataProcessor;
import io.mosip.packet.core.util.TrackerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static io.mosip.packet.core.constant.GlobalConfig.*;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_ID;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_NAME;

@Component
public class UgandaECExtractor implements DataProcessor {

    private static final Logger LOGGER = DataProcessLogger.getLogger(UgandaECExtractor.class);

    @Value("${mosip.extractor.application.id.column:}")
    private String applicationIdColumn;

    @Autowired
    private TrackerUtil trackerUtil;

    @Value("${mosip.extractor.uganda.ec.table.mapping:{}}")
    private String tableMapping;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public DataProcessorResponseDto process(DBImportRequest dbImportRequest, Object data, ResultSetter setter) throws Exception {
        DataProcessorResponseDto responseDto = new DataProcessorResponseDto();
        responseDto.setProcess(dbImportRequest.getProcess());
        responseDto.setResponses(new HashMap<>());

        String trackerColumn = dbImportRequest.getTrackerInfo().getTrackerColumn();
        Map<FieldCategory, HashMap<String, Object>> dataHashMap = (Map<FieldCategory, HashMap<String, Object>>) data;

        if ( dataHashMap != null) {
            String uinRefId = null;

            if(applicationIdColumn != null && !applicationIdColumn.isEmpty()) {
                if(dataHashMap.get(FieldCategory.DEMO).containsKey(applicationIdColumn)) {
                    uinRefId = dataHashMap.get(FieldCategory.DEMO).get(applicationIdColumn).toString();
                } else {
                    LOGGER.error("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Application ID : " + applicationIdColumn + " not found in DataMap");
                    throw new Exception("Application ID : " + applicationIdColumn + " not found in DataMap");
                }
            }

            trackerUtil.addTrackerLocalEntry(dataHashMap.get(FieldCategory.DEMO).get(dbImportRequest.getTrackerInfo().getTrackerColumn()).toString(), uinRefId, TrackerStatus.STARTED, dbImportRequest.getProcess(), null, SESSION_KEY, getActivityName());

            HashMap<String, Object> mapDetails = dataHashMap.get(FieldCategory.DEMO);
            mapDetails.putAll(dataHashMap.get(FieldCategory.BIO));
            mapDetails.putAll(dataHashMap.get(FieldCategory.DOC));

            responseDto.setRefId(mapDetails.get(trackerColumn).toString());
            responseDto.setTrackerRefId(uinRefId);
            LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Thread - " + uinRefId + " Process Started");
            Long startTime = System.nanoTime();

            responseDto.setResponses(extractDataFromMap(objectMapper.readTree(tableMapping), mapDetails));
            LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Thread - " + uinRefId + " Time taken to Complete extractDataFromMap" + TimeUnit.MILLISECONDS.convert(System.nanoTime()-startTime, TimeUnit.NANOSECONDS));
            return responseDto;
        }

        return null;
    }

    private Map<String, Object> extractDataFromMap(JsonNode node, HashMap<String, Object> map) throws Exception {
        Map<String, Object> response = new HashMap<>();
        String fieldName = null;
        try {
            // If object → loop fields
            if (node.isObject()) {
                ObjectNode obj = (ObjectNode) node;

                Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> entry = it.next();

                    fieldName = entry.getKey();
                    JsonNode value = entry.getValue();

                    Object objval = map.get(value.asText());
                    if(objval instanceof BioData) {
                        BioData data = (BioData) objval;
                        response.put(fieldName, Base64.getEncoder().encode(data.getBioData()));
                    } else {
                        response.put(fieldName, map.get(value.asText()));
                    }
                }
            }

            if (node.isArray()) {
                ArrayNode arr = (ArrayNode) node;
                for (int i = 0; i < arr.size(); i++) {
                    extractDataFromMap(arr.get(i), map);
                }
            }
        } catch (Exception e) {
            throw new Exception("Error Occured while extract Data for field " + fieldName + ExceptionUtils.getStackTrace(e));
        }

        return response;
    }
}
