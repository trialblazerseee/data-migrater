package io.mosip.packet.extractor.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.commons.packet.dto.Document;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.packet.core.constant.BioSubType;
import io.mosip.packet.core.constant.DataFormat;
import io.mosip.packet.core.constant.FieldCategory;
import io.mosip.packet.core.constant.mvel.ParameterType;
import io.mosip.packet.core.dto.dbimport.DocumentAttributes;
import io.mosip.packet.core.dto.dbimport.FieldFormatRequest;
import io.mosip.packet.core.dto.dbimport.FieldName;
import io.mosip.packet.core.dto.dbimport.IndividualBiometricFormat;
import io.mosip.packet.core.dto.mvel.MvelParameter;
import io.mosip.packet.core.dto.packet.BioData;
import io.mosip.packet.core.logger.DataProcessLogger;
import io.mosip.packet.core.service.CustomNativeRepository;
import io.mosip.packet.core.spi.BioConvertorApiFactory;
import io.mosip.packet.core.spi.BioDocApiFactory;
import io.mosip.packet.core.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.SQLException;
import java.util.*;

import static io.mosip.packet.core.constant.GlobalConfig.SESSION_ID;

@Component
public class TableDataMapperUtil implements DataMapperUtil {
    private static final Logger LOGGER = DataProcessLogger.getLogger(TableDataMapperUtil.class);

    @Autowired
    private CustomNativeRepository customNativeRepository;

    @Autowired
    private MvelUtil mvelUtil;

    @Autowired
    private BioConvertorApiFactory bioConvertorApiFactory;

    @Autowired
    private CommonUtil commonUtil;

    @Value("${mosip.id.schema.selected.handles.attribute.name:selectedHandles}")
    private String handleAttribute;

    private String VALUE_SPLITTER = " ";

    @Autowired
    private QueryFormatter formatter;

    @Autowired
    private DataMapperProcessor dataMapperProcessor;

    @Override
    public void dataMapper(FieldFormatRequest fieldFormatRequest, Map<String, Object> resultSet, Map<FieldCategory, HashMap<String, Object>> dataMap2, String tableName, Map<String, HashMap<String, String>> fieldsCategoryMap, Boolean localStoreRequired) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        DataFormat destFormat = fieldFormatRequest.getDestFormat() != null && fieldFormatRequest.getDestFormat().size() > 0 ? fieldFormatRequest.getDestFormat().get(fieldFormatRequest.getDestFormat().size()-1) : null;
        List<FieldName> fieldNames = fieldFormatRequest.getFieldList();
        String fieldToMap = fieldFormatRequest.getFieldToMap() != null ? fieldFormatRequest.getFieldToMap() : fieldNames.get(0).getOriginalFieldName().toLowerCase();
        String originalField = fieldFormatRequest.getFieldName();
        String[] fieldToMapArray = fieldToMap.split(",");

        if((!dataMap2.get(fieldFormatRequest.getFieldCategory()).containsKey(originalField) || !dataMap2.get(fieldFormatRequest.getFieldCategory()).keySet().containsAll(Arrays.asList(fieldToMapArray))) && commonUtil.isFieldPresentInTable(tableName, fieldsCategoryMap, fieldNames)) {
            String mvelValue = null;
            if (fieldFormatRequest.getMvelExpressions() != null) {
                Map map = new HashMap();
                for (MvelParameter parameter : fieldFormatRequest.getMvelExpressions().getParameters()) {
                    if (parameter.getParameterType().equals(ParameterType.STRING))
                        if(parameter.getParameterValue().contains("${")) {
                            String value = parameter.getParameterValue().toUpperCase();
 //                           for(FieldCategory fieldCategory : FieldCategory.values()) {
 //                               value = value.replace(fieldCategory.toString() + ":", "");
 //                           }

                            try {
                                map.put(parameter.getParameterName(), formatter.replaceColumntoDataIfAny(value, dataMap2));
                            } catch (Exception e) {
                                String param = value.replace("${", "").replace("}", "");
                                map.put(parameter.getParameterName(), resultSet.get(param));
                            }
                        } else {
                            map.put(parameter.getParameterName(), parameter.getParameterValue());
                        }
                    else if (parameter.getParameterType().equals(ParameterType.SQL)){
                        List<Object> list = (List<Object>) customNativeRepository.runNativeQuery(parameter.getParameterValue());
                        map.put(parameter.getParameterName(), list);
                    }
                }

                mvelValue = mvelUtil.processViaMVEL(fieldFormatRequest.getMvelExpressions().getMvelFile(), map);
            }

            if (fieldFormatRequest.getFieldCategory().equals(FieldCategory.DEMO)) {
                Object demoValue = null;
                if(fieldFormatRequest.getMvelExpressions() != null) {
                    demoValue = mvelValue;
                } else {
                    demoValue = null;
                    boolean initialEntry = true;

                    for(FieldName field : fieldNames) {
                        if(initialEntry)
                            demoValue = dataMap2.get(fieldFormatRequest.getFieldCategory()).get(fieldToMap);

                        initialEntry=false;

                        if(fieldsCategoryMap.get(tableName).containsKey(field.getOriginalFieldName()))
                            if(demoValue == null)
                                demoValue = resultSet.get(field.getOriginalFieldName());
                            else {
                                if(demoValue.toString().contains("<" + field.getOriginalFieldName() + ">"))
                                    demoValue = demoValue.toString().replace("<" + field.getOriginalFieldName() + ">", resultSet.get(field.getOriginalFieldName()).toString());
                                else
                                    demoValue += VALUE_SPLITTER + resultSet.get(field.getOriginalFieldName());
                            }
                        else
                            demoValue += " <" + field.getOriginalFieldName() + ">";

                        dataMap2.get(fieldFormatRequest.getFieldCategory()).put(field.getTableName() + "." + field.getOriginalFieldName(), resultSet.get(field.getOriginalFieldName()));
                    }
                }

                if(demoValue != null) {
                    if (destFormat != null) {
                        Date dateVal = DateUtils.findDateFormat(demoValue.toString());
                        demoValue = DateUtils.parseDate(dateVal, destFormat.getFormat());
                    }

                    if(fieldToMapArray.length > 1) {
                        int arrayLength = fieldToMapArray.length;
                        String[] mapArray = demoValue.toString().split(VALUE_SPLITTER);
                        int maplength = mapArray.length;

                        if(arrayLength >= maplength) {
                            for(int i = 0; i < arrayLength; i++)
                                dataMap2.get(fieldFormatRequest.getFieldCategory()).put(fieldToMapArray[i], (mapArray.length < i+1) ? null : mapArray[i]);
                        } else {
                            int difference = Double.valueOf(Math.ceil((float) maplength / (float) arrayLength)).intValue();

                            String[] newArray = new String[arrayLength];
                            int i = 0;
                            int k = 0;
                            do {
                                String val = "";
                                for(int j = 0; j < difference; j++)
                                    val+= (mapArray.length < k+1) ? "" : mapArray[k++] + VALUE_SPLITTER;
                                newArray[i] = val;
                            } while(++i < arrayLength);

                            for(int z = 0; z < arrayLength; z++)
                                dataMap2.get(fieldFormatRequest.getFieldCategory()).put(fieldToMapArray[z], (newArray.length < z+1) ? null : newArray[z]);
                        }
                    } else {
                        dataMap2.get(fieldFormatRequest.getFieldCategory()).put(fieldToMap, demoValue);
                    }

                    if(fieldFormatRequest.getFetchInsruction() != null) {
                        Map<String, byte[]> map = null;

                        if(demoValue instanceof byte[]) {
                            map = dataMapperProcessor.processDemoData(fieldFormatRequest, resultSet, (byte[]) demoValue, fieldToMap);
                        } else {
                            try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                                 ObjectOutputStream oos = new ObjectOutputStream(bos)) {
                                oos.writeObject(demoValue);
                                oos.flush();
                                map = dataMapperProcessor.processDemoData(fieldFormatRequest, resultSet, bos.toByteArray(), fieldToMap);
                            }
                        }

                        map.forEach((s, bytes) -> {
                            dataMap2.get(fieldFormatRequest.getFieldCategory()).put(s, new String(bytes));
                        });
                    }

                    dataMap2.get(fieldFormatRequest.getFieldCategory()).put(originalField, demoValue);
                }

                if(fieldFormatRequest.getUseAsHandle() != null && fieldFormatRequest.getUseAsHandle()) {
                    dataMap2.get(fieldFormatRequest.getFieldCategory()).put(handleAttribute, fieldToMap);
                }
            } else if (fieldFormatRequest.getFieldCategory().equals(FieldCategory.BIO)) {
                String fieldName = fieldFormatRequest.getFieldList().get(0).getOriginalFieldName();
                LOGGER.debug(SESSION_ID, "DATA_READER", "dataMapper()", "FieldName for Biometric Read " + fieldName);

                Map<String, byte[]> map = new HashMap<>();

                if(fieldsCategoryMap.get(tableName).containsKey(fieldName))  {
                    byte[] byteVal = null;
                    if(fieldFormatRequest.getMvelExpressions() != null && mvelValue != null) {
                        byteVal = convertObjectToByteArray(mvelValue);;
                    } else {
                        if(resultSet.get(fieldName) != null) {
                            byteVal = convertObjectToByteArray(resultSet.get(fieldName));
                        }
                    }

                    LOGGER.debug(SESSION_ID, "DATA_READER", "dataMapper()", "Value for Biometric Read for Field : " + fieldName + " is : " + String.valueOf(byteVal));
                    if(byteVal != null) {
                        map =  dataMapperProcessor.processBioData(fieldFormatRequest, resultSet, byteVal, fieldToMap);
                    }

                    HashMap<BioSubType, IndividualBiometricFormat> formatMap = new HashMap<>();
                    if(fieldFormatRequest.getIndividualBiometricFormat() != null && !fieldFormatRequest.getIndividualBiometricFormat().isEmpty()) {
                        for(IndividualBiometricFormat format : fieldFormatRequest.getIndividualBiometricFormat())
                            formatMap.put(format.getSubType(), format);
                    }

                    for(String field : fieldToMap.split(",")) {
                        byte[] convertedImageData = null;
                        byte[] bytes = map.get(field);
                        if(bytes != null) {
                            convertedImageData = convertBiometric(dataMap2.get(FieldCategory.DEMO).get(fieldFormatRequest.getPrimaryField()).toString(), fieldFormatRequest, bytes, localStoreRequired, field, formatMap);
                        }
                        BioData bioData = new BioData();
                        bioData.setBioData(convertedImageData);
                        bioData.setFormat(fieldFormatRequest.getDestFormat().get(fieldFormatRequest.getDestFormat().size()-1));
                        bioData.setQualityScore(fieldFormatRequest.getSrcFieldForQualityScore() != null ? resultSet.get(fieldFormatRequest.getFieldNameWithoutSchema(fieldFormatRequest.getSrcFieldForQualityScore())).toString() : "");
                        dataMap2.get(fieldFormatRequest.getFieldCategory()).put(field, bioData);
                    }
                    dataMap2.get(fieldFormatRequest.getFieldCategory()).put(originalField, "");
                }
            } else if (fieldFormatRequest.getFieldCategory().equals(FieldCategory.DOC)) {
                String fieldName = fieldFormatRequest.getFieldList().get(0).getModifiedFieldName().toUpperCase();
                String searchField = fieldFormatRequest.getFieldToMap().toUpperCase();

                if(resultSet.containsKey(fieldName))  {
                    Document document = new Document();
                    byte[] byteVal = null;
                    if(fieldFormatRequest.getMvelExpressions() != null && mvelValue != null) {
                        byteVal = convertObjectToByteArray(mvelValue);;
                    } else {
                        byteVal = convertObjectToByteArray(resultSet.get(fieldName));
                    }

                    byteVal = dataMapperProcessor.processDocData(fieldFormatRequest, resultSet, byteVal, fieldToMap).get(fieldToMap);

                    if(byteVal != null) {
                    document.setDocument(byteVal);
                    if(fieldFormatRequest.getDocumentAttributes() != null) {
                        DocumentAttributes documentAttributes = fieldFormatRequest.getDocumentAttributes();
                        String refField = documentAttributes.getDocumentRefNoField().contains("STATIC") ? "STATIC_" +  commonUtil.getDocumentAttributeStaticValue(documentAttributes.getDocumentRefNoField())
                                :  fieldFormatRequest.getFieldNameWithoutSchema(documentAttributes.getDocumentRefNoField());
                        document.setRefNumber(String.valueOf(resultSet.get(searchField + "_" + refField)));
                        dataMap2.get(fieldFormatRequest.getFieldCategory()).put(fieldToMap + ":" + refField, document.getRefNumber());

                        String formatField = documentAttributes.getDocumentFormatField().contains("STATIC") ? "STATIC_" + commonUtil.getDocumentAttributeStaticValue(documentAttributes.getDocumentFormatField())
                                :  fieldFormatRequest.getFieldNameWithoutSchema(documentAttributes.getDocumentFormatField());
                        document.setFormat(String.valueOf(resultSet.get(searchField + "_" + formatField.toUpperCase())));
                        dataMap2.get(fieldFormatRequest.getFieldCategory()).put(fieldToMap + ":" + formatField, document.getFormat());

                        String codeField = documentAttributes.getDocumentCodeField().contains("STATIC") ? "STATIC_" + commonUtil.getDocumentAttributeStaticValue(documentAttributes.getDocumentCodeField())
                                :  fieldFormatRequest.getFieldNameWithoutSchema(documentAttributes.getDocumentCodeField());
                        document.setType(String.valueOf(resultSet.get(searchField + "_" + codeField.toUpperCase())));
                        dataMap2.get(fieldFormatRequest.getFieldCategory()).put(fieldToMap + ":" + codeField, document.getType());
                    }

                    dataMap2.get(fieldFormatRequest.getFieldCategory()).put(fieldToMap, mapper.writeValueAsString(document));
                    dataMap2.get(fieldFormatRequest.getFieldCategory()).put(fieldFormatRequest.getFieldToMap() + "_" +originalField, "");
                }
            }
        }
    }
    }

    private byte[] convertObjectToByteArray(Object obj) throws IOException, SQLException {
        if (obj instanceof String)
            return ((String) obj).getBytes(StandardCharsets.UTF_8);

        if (obj instanceof Clob) {
            Clob clobObj = (Clob) obj;
            return clobObj.getSubString(1, (int) clobObj.length()).getBytes(StandardCharsets.UTF_8);
        }

        if (obj instanceof Blob) {
            Blob blobObj = (Blob) obj;
            return blobObj.getBytes(1, (int) blobObj.length());
        }
        return (byte[]) obj;
    }

    public byte[] convertBiometric(String fileNamePrefix, FieldFormatRequest fieldFormatRequest, byte[] bioValue, Boolean localStoreRequired, String fieldName, HashMap<BioSubType, IndividualBiometricFormat> formatMap) throws Exception {
        String bioSubType = fieldName.split("_")[1];
        DataFormat srcFormat= fieldFormatRequest.getSrcFormat();
        List<DataFormat> destFormat = fieldFormatRequest.getDestFormat();

        if(formatMap != null && !formatMap.isEmpty()) {
            IndividualBiometricFormat format = formatMap.get(BioSubType.getBioSubType(bioSubType));

            if(format != null) {
                if(format.getSrcImageFormat() != null)
                    srcFormat = format.getSrcImageFormat();

                if(format.getDestImageFormat() != null && !format.getDestImageFormat().isEmpty())
                    destFormat = format.getDestImageFormat();
            }
        }

        if (localStoreRequired) {
            bioConvertorApiFactory.writeFile(fileNamePrefix + "-" + fieldFormatRequest.getFieldList().get(0).getOriginalFieldName() , bioValue, srcFormat);
            return bioConvertorApiFactory.writeFile(fileNamePrefix + "-" + fieldFormatRequest.getFieldList().get(0).getOriginalFieldName(), bioConvertorApiFactory.convertImage(srcFormat, destFormat, bioValue, fieldName), destFormat.get(destFormat.size()-1));
        } else {
            return bioConvertorApiFactory.convertImage(srcFormat, destFormat, bioValue, fieldName);
        }
    }
}
