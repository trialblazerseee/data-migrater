package io.mosip.packet.data.convertion;

import io.mosip.packet.core.spi.BioDocApiFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(value = "mosip.packet.bio.doc.data.converter.classname", havingValue = "BioDocDataConverter")
public class BioDocDataConverter implements BioDocApiFactory {
    @Override
    public Map<String, byte[]> getBioData(byte[] byteval, String fieldName) {
        Map<String, byte[]> map = new HashMap<>();
        map.put(fieldName, byteval);
        return map;
    }

    @Override
    public Map<String, byte[]> getDocData(byte[] byteval, String fieldName) {
        Map<String, byte[]> map = new HashMap<>();
        map.put(fieldName, byteval);
        return map;
    }

    @Override
    public Map<String, byte[]> getDemoData(byte[] byteval, String fieldName) throws Exception {
        return null;
    }
}
