package io.mosip.packet.extractor.util;

import io.mosip.packet.core.dto.dbimport.FetchMode;
import io.mosip.packet.core.dto.dbimport.FieldFormatRequest;
import io.mosip.packet.core.spi.BioDocApiFactory;
import io.mosip.packet.core.util.ObjectStoreHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public class DataMapperProcessor {

    @Autowired
    private ObjectStoreHelper objectStoreHelper;

    @Autowired
    private BioDocApiFactory bioDocApiFactory;

    public Map<String, byte[]> processDemoData(FieldFormatRequest fieldFormatRequest, Map<String, Object> resultSet, byte[] byteVal, String fieldToMap) throws Exception {
        if(fieldFormatRequest.getFetchInsruction().getFetchMode() != null) {
            byteVal = fetchData(fieldFormatRequest.getFetchInsruction().getFetchMode(), byteVal);
        }
        Map<String, byte[]> map = null;

        if(fieldFormatRequest.getFetchInsruction().getIsDataFormatRequired())
            map = bioDocApiFactory.getDemoData(byteVal, fieldToMap);

        if(map == null) {
            map = new HashMap<>();
            map.put(fieldToMap, byteVal);
        }

        return map;
    }

    public Map<String, byte[]> processBioData(FieldFormatRequest fieldFormatRequest, Map<String, Object> resultSet, byte[] byteVal, String fieldToMap) throws Exception {
        if(fieldFormatRequest.getFetchInsruction().getFetchMode() != null) {
            byteVal = fetchData(fieldFormatRequest.getFetchInsruction().getFetchMode(), byteVal);
        }

        Map<String, byte[]> map = null;

        if(fieldFormatRequest.getFetchInsruction().getIsDataFormatRequired())
            map = bioDocApiFactory.getBioData(byteVal, fieldToMap);

        if(map == null) {
            map = new HashMap<>();
            map.put(fieldToMap, byteVal);
        }

        return map;
    }

    public Map<String, byte[]> processDocData(FieldFormatRequest fieldFormatRequest, Map<String, Object> resultSet, byte[] byteVal, String fieldToMap) throws Exception {
        if(fieldFormatRequest.getFetchInsruction().getFetchMode() != null) {
            byteVal = fetchData(fieldFormatRequest.getFetchInsruction().getFetchMode(), byteVal);
        }

        Map<String, byte[]> map = null;

        if(fieldFormatRequest.getFetchInsruction().getIsDataFormatRequired())
            map = bioDocApiFactory.getDocData(byteVal, fieldToMap);

        if(map == null) {
            map = new HashMap<>();
            map.put(fieldToMap, byteVal);
        }

        return map;
    }

    private byte[] fetchData(FetchMode fetchMode, byte[] byteVal) throws Exception {
        switch (fetchMode) {
            case API:
                prepareApi();
                executeApi();
                return processResponse();
            case OBJECT_STORE:
                return getObjectFromObjectStore(byteVal);
            default:
                throw new Exception("Implementation not found the Fetch Type " + fetchMode.toString());
        }
    }

    private void prepareApi() {
    }

    private void executeApi() {
    }

    private byte[] processResponse() {
        return null;
    }

    private byte[] getObjectFromObjectStore(byte[] byteVal) throws Exception {
        return objectStoreHelper.getBiometricObject(new String(byteVal, StandardCharsets.UTF_8));
    }
}
