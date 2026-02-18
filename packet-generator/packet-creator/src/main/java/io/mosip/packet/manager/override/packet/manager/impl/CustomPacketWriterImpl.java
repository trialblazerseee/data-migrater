package io.mosip.packet.manager.override.packet.manager.impl;

import org.assertj.core.util.Lists;
import io.mosip.commons.packet.constants.ErrorCode;
import io.mosip.commons.packet.constants.LoggerFileConstant;
import io.mosip.commons.packet.constants.PacketManagerConstants;
import io.mosip.commons.packet.dto.Document;
import io.mosip.commons.packet.dto.Packet;
import io.mosip.commons.packet.dto.PacketInfo;
import io.mosip.commons.packet.dto.packet.BiometricsType;
import io.mosip.commons.packet.dto.packet.DocumentType;
import io.mosip.commons.packet.dto.packet.HashSequenceMetaInfo;
import io.mosip.commons.packet.dto.packet.RegistrationPacket;
import io.mosip.commons.packet.exception.PacketCreatorException;
import io.mosip.commons.packet.impl.PacketWriterImpl;
import io.mosip.commons.packet.keeper.PacketKeeper;
import io.mosip.commons.packet.spi.IPacketWriter;
import io.mosip.commons.packet.util.PacketManagerHelper;
import io.mosip.commons.packet.util.PacketManagerLogger;
import io.mosip.kernel.biometrics.entities.BiometricRecord;
import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.kernel.core.util.JsonUtils;
import io.mosip.kernel.core.util.StringUtils;
import io.mosip.kernel.core.util.exception.JsonProcessingException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
@Primary
public class CustomPacketWriterImpl implements IPacketWriter {
    private static final Logger LOGGER = PacketManagerLogger.getLogger(PacketWriterImpl.class);
    private static Map<String, String> categorySubpacketMapping = new HashMap();
    private static final String UNDERSCORE = "_";
    private static final String HASHSEQUENCE1 = "hashSequence1";
    private static final String HASHSEQUENCE2 = "hashSequence2";
    @Autowired
    private PacketManagerHelper packetManagerHelper;
    @Autowired
    private PacketKeeper packetKeeper;
    @Value("${mosip.kernel.packet.default_subpacket_name:id}")
    private String defaultSubpacketName;
    @Value("${default.provider.version:v1.0}")
    private String defaultProviderVersion;
    @Value("${mosip.utc-datetime-pattern:yyyy-MM-dd'T'HH:mm:ss.SSS'Z'}")
    private String dateTimePattern;
    @Value("${packetmanager.zip.datetime.pattern:yyyyMMddHHmmss}")
    private String zipDatetimePattern;
    private Map<String, RegistrationPacket> registrationPacketMap = new HashMap();

    public RegistrationPacket initialize(String id) {
        if (this.registrationPacketMap.get(id) == null) {
            RegistrationPacket registrationPacket = new RegistrationPacket(this.dateTimePattern);
            registrationPacket.setRegistrationId(id);
            this.registrationPacketMap.put(id, registrationPacket);
        }

        return (RegistrationPacket)this.registrationPacketMap.get(id);
    }

    public void setField(String id, String fieldName, String value) {
        this.initialize(id).setField(fieldName, value);
    }

    public void setFields(String id, Map<String, String> fields) {
        this.initialize(id).setFields(fields);
    }

    public void setBiometric(String id, String fieldName, BiometricRecord value) {
        this.initialize(id).setBiometricField(fieldName, value);
    }

    public void setDocument(String id, String fieldName, Document value) {
        this.initialize(id).setDocumentField(fieldName, value);
    }

    public void addAudits(String id, List<Map<String, String>> auditList) {
        this.initialize(id).setAudits(auditList);
    }

    public void addAudit(String id, Map<String, String> audit) {
        this.initialize(id).setAudit(audit);
    }

    public void addMetaInfo(String id, Map<String, String> metaInfo) {
        this.initialize(id).setMetaData(metaInfo);
    }

    public void addMetaInfo(String id, String key, String value) {
        this.initialize(id).addMetaData(key, value);
    }

    private List<PacketInfo> createPacket(String id, String version, String schemaJson, String source, String process, String additionalInfoReqId, String refId, boolean offlineMode) throws PacketCreatorException {
        LOGGER.info(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.ID.toString(), id, "Started packet creation");
        if (this.registrationPacketMap.get(id) == null) {
            throw new PacketCreatorException(ErrorCode.INITIALIZATION_ERROR.getErrorCode(), ErrorCode.INITIALIZATION_ERROR.getErrorMessage());
        } else {
            List<PacketInfo> packetInfos = new ArrayList();
            Map<String, List<Object>> identityProperties = this.loadSchemaFields(schemaJson);

            try {
                int counter = 1;
                String packetId = (StringUtils.isNotBlank(additionalInfoReqId) ? additionalInfoReqId : id) + "-" + refId + "-" + this.getcurrentTimeStamp();

                for(String subPacketName : identityProperties.keySet()) {
                    LOGGER.info(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.ID.toString(), id, "Started Subpacket: " + subPacketName);
                    List<Object> schemaFields = (List)identityProperties.get(subPacketName);
                    byte[] subpacketBytes = this.createSubpacket(Double.valueOf(version), schemaFields, this.defaultSubpacketName.equalsIgnoreCase(subPacketName), id, offlineMode);
                    PacketInfo packetInfo = new PacketInfo();
                    packetInfo.setProviderName(this.getClass().getSimpleName());
                    packetInfo.setSchemaVersion((new Double(version)).toString());
                    if (offlineMode) {
                        packetInfo.setId(packetId);
                    } else {
                        packetInfo.setId(id);
                    }

                    packetInfo.setRefId(refId);
                    packetInfo.setSource(source);
                    packetInfo.setProcess(process);
                    packetInfo.setPacketName(id + "_" + subPacketName);
                    packetInfo.setCreationDate(DateUtils.getUTCCurrentDateTimeString());
                    packetInfo.setProviderVersion(this.defaultProviderVersion);
                    Packet packet = new Packet();
                    packet.setPacketInfo(packetInfo);
                    packet.setPacket(subpacketBytes);
                    this.packetKeeper.putPacket(packet);
                    packetInfos.add(packetInfo);
                    LOGGER.info(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.ID.toString(), id, "Completed Subpacket: " + subPacketName);
                    if (counter == identityProperties.keySet().size()) {
                        boolean res = this.packetKeeper.pack(packetInfo.getId(), packetInfo.getSource(), packetInfo.getProcess(), packetInfo.getRefId());
                        if (!res) {
                            this.packetKeeper.deletePacket(id, source, process);
                        }
                    }

                    ++counter;
                }
            } catch (Exception e) {
                LOGGER.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.ID.toString(), id, "Exception occured. Deleting the packet.");
                this.packetKeeper.deletePacket(id, source, process);
                throw new PacketCreatorException(ErrorCode.PKT_ZIP_ERROR.getErrorCode(), ErrorCode.PKT_ZIP_ERROR.getErrorMessage().concat(ExceptionUtils.getStackTrace(e)));
            } finally {
                this.registrationPacketMap.remove(id);
                LOGGER.debug("registrationPacketMap size ====================================> " + this.registrationPacketMap.size());
            }

            LOGGER.info(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.ID.toString(), id, "Exiting packet creation");
            return packetInfos;
        }
    }

    private byte[] createSubpacket(double version, List<Object> schemaFields, boolean isDefault, String id, boolean offlineMode) throws PacketCreatorException {
        RegistrationPacket registrationPacket = (RegistrationPacket)this.registrationPacketMap.get(id);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (ZipOutputStream subpacketZip = new ZipOutputStream(new BufferedOutputStream(out))) {
            LOGGER.info(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.ID.toString(), id, "Identified fields >>> " + schemaFields.size());
            Map<String, Object> identity = new HashMap();
            Map<String, HashSequenceMetaInfo> hashSequences = new HashMap();
            identity.put("IDSchemaVersion", version);
            registrationPacket.getMetaData().put("registrationId", id);
            registrationPacket.getMetaData().put("creationDate", registrationPacket.getCreationDate());

            for(Object obj : schemaFields) {
                Map<String, Object> field = (Map)obj;
                String fieldName = (String)field.get("id");
                LOGGER.info(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.ID.toString(), id, "Adding field : " + fieldName);
                switch ((String)field.get("type")) {
                    case "#/definitions/biometricsType":
                        if (registrationPacket.getBiometrics().get(fieldName) != null) {
                            this.addBiometricDetailsToZip(registrationPacket, fieldName, identity, subpacketZip, hashSequences, offlineMode);
                        }
                        break;
                    case "#/definitions/documentType":
                        if (registrationPacket.getDocuments().get(fieldName) != null) {
                            this.addDocumentDetailsToZip(registrationPacket, fieldName, identity, subpacketZip, hashSequences, offlineMode);
                        }
                        break;
                    default:
                        if (registrationPacket.getDemographics().get(fieldName) != null) {
                            identity.put(fieldName, registrationPacket.getDemographics().get(fieldName));
                        }
                }
            }

            byte[] identityBytes = this.getIdentity(identity).getBytes();
            this.addEntryToZip(registrationPacket, "ID.json", identityBytes, subpacketZip);
            this.addHashSequenceWithSource("demographicSequence", "ID", identityBytes, hashSequences);
            this.addOtherFilesToZip(registrationPacket, isDefault, subpacketZip, hashSequences, offlineMode);
        } catch (JsonProcessingException e) {
            throw new PacketCreatorException(ErrorCode.OBJECT_TO_JSON_ERROR.getErrorCode(), ErrorCode.BIR_TO_XML_ERROR.getErrorMessage().concat(ExceptionUtils.getStackTrace(e)));
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new PacketCreatorException(ErrorCode.PKT_ZIP_ERROR.getErrorCode(), ErrorCode.PKT_ZIP_ERROR.getErrorMessage().concat(ExceptionUtils.getStackTrace(e)));
        }

        return out.toByteArray();
    }

    private void addDocumentDetailsToZip(RegistrationPacket registrationPacket, String fieldName, Map<String, Object> identity, ZipOutputStream zipOutputStream, Map<String, HashSequenceMetaInfo> hashSequences, boolean offlineMode) throws PacketCreatorException {
        Document document = (Document)registrationPacket.getDocuments().get(fieldName);
        identity.put(fieldName, new DocumentType(fieldName, document.getType(), document.getFormat(), document.getRefNumber()));
        String fileName = String.format("%s.%s", fieldName, document.getFormat());
        this.addEntryToZip(registrationPacket, fileName, document.getDocument(), zipOutputStream);
        registrationPacket.getMetaData().put(fieldName, document.getType());
        this.addHashSequenceWithSource("demographicSequence", fieldName, document.getDocument(), hashSequences);
    }

    private void addBiometricDetailsToZip(RegistrationPacket registrationPacket, String fieldName, Map<String, Object> identity, ZipOutputStream zipOutputStream, Map<String, HashSequenceMetaInfo> hashSequences, boolean offlineMode) throws PacketCreatorException {
        BiometricRecord birType = (BiometricRecord)registrationPacket.getBiometrics().get(fieldName);
        if (birType != null && birType.getSegments() != null && !birType.getSegments().isEmpty()) {
            byte[] xmlBytes;
            try {
                xmlBytes = this.packetManagerHelper.getXMLData(birType, offlineMode);
            } catch (Exception e) {
                throw new PacketCreatorException(ErrorCode.BIR_TO_XML_ERROR.getErrorCode(), ErrorCode.BIR_TO_XML_ERROR.getErrorMessage().concat(ExceptionUtils.getStackTrace(e)));
            }

            this.addEntryToZip(registrationPacket, String.format(PacketManagerConstants.CBEFF_FILENAME_WITH_EXT, fieldName), xmlBytes, zipOutputStream);
            identity.put(fieldName, new BiometricsType("cbeff", (double)1.0F, String.format("%s_bio_CBEFF", fieldName)));
            this.addHashSequenceWithSource("biometricSequence", String.format("%s_bio_CBEFF", fieldName), xmlBytes, hashSequences);
        }

    }

    private void addHashSequenceWithSource(String sequenceType, String name, byte[] bytes, Map<String, HashSequenceMetaInfo> hashSequences) {
        if (!hashSequences.containsKey(sequenceType)) {
            hashSequences.put(sequenceType, new HashSequenceMetaInfo(sequenceType));
        }

        ((HashSequenceMetaInfo)hashSequences.get(sequenceType)).addHashSource(name, bytes);
    }

    private void addOtherFilesToZip(RegistrationPacket registrationPacket, boolean isDefault, ZipOutputStream zipOutputStream, Map<String, HashSequenceMetaInfo> hashSequences, boolean offlineMode) throws JsonProcessingException, PacketCreatorException, IOException, NoSuchAlgorithmException {
        if (isDefault) {
            this.addOperationsBiometricsToZip(registrationPacket, "officer_bio_cbeff", zipOutputStream, hashSequences, offlineMode);
            this.addOperationsBiometricsToZip(registrationPacket, "supervisor_bio_cbeff", zipOutputStream, hashSequences, offlineMode);
            if (registrationPacket.getAudits() == null || registrationPacket.getAudits().isEmpty()) {
                throw new PacketCreatorException(ErrorCode.AUDITS_REQUIRED.getErrorCode(), ErrorCode.AUDITS_REQUIRED.getErrorMessage());
            }

            byte[] auditBytes = JsonUtils.javaObjectToJsonString(registrationPacket.getAudits()).getBytes();
            this.addEntryToZip(registrationPacket, "audit.json", auditBytes, zipOutputStream);
            this.addHashSequenceWithSource("otherFiles", "audit", auditBytes, hashSequences);
            HashSequenceMetaInfo hashSequenceMetaInfo = (HashSequenceMetaInfo)hashSequences.get("otherFiles");
            this.addEntryToZip(registrationPacket, "packet_operations_hash.txt", PacketManagerHelper.generateHash(hashSequenceMetaInfo.getValue(), hashSequenceMetaInfo.getHashSource()), zipOutputStream);
            registrationPacket.getMetaData().put("hashSequence2", Lists.newArrayList(new HashSequenceMetaInfo[]{hashSequenceMetaInfo}));
        }

        this.addPacketDataHash(registrationPacket, hashSequences, zipOutputStream);
        this.addEntryToZip(registrationPacket, "packet_meta_info.json", this.getIdentity(registrationPacket.getMetaData()).getBytes(), zipOutputStream);
    }

    private void addPacketDataHash(RegistrationPacket registrationPacket, Map<String, HashSequenceMetaInfo> hashSequences, ZipOutputStream zipOutputStream) throws PacketCreatorException, IOException, NoSuchAlgorithmException {
        LinkedList<String> sequence = new LinkedList();
        List<HashSequenceMetaInfo> hashSequenceMetaInfos = new ArrayList();
        Map<String, byte[]> data = new HashMap();
        if (hashSequences.containsKey("biometricSequence")) {
            sequence.addAll(((HashSequenceMetaInfo)hashSequences.get("biometricSequence")).getValue());
            data.putAll(((HashSequenceMetaInfo)hashSequences.get("biometricSequence")).getHashSource());
            hashSequenceMetaInfos.add((HashSequenceMetaInfo)hashSequences.get("biometricSequence"));
        }

        if (hashSequences.containsKey("demographicSequence")) {
            sequence.addAll(((HashSequenceMetaInfo)hashSequences.get("demographicSequence")).getValue());
            data.putAll(((HashSequenceMetaInfo)hashSequences.get("demographicSequence")).getHashSource());
            hashSequenceMetaInfos.add((HashSequenceMetaInfo)hashSequences.get("demographicSequence"));
        }

        if (hashSequenceMetaInfos.size() > 0) {
            registrationPacket.getMetaData().put("hashSequence1", hashSequenceMetaInfos);
        }

        this.addEntryToZip(registrationPacket, "packet_data_hash.txt", PacketManagerHelper.generateHash(sequence, data), zipOutputStream);
    }

    private Map<String, List<Object>> loadSchemaFields(String schemaJson) throws PacketCreatorException {
        Map<String, List<Object>> packetBasedMap = new HashMap();

        try {
            JSONObject schema = new JSONObject(schemaJson);
            schema = schema.getJSONObject("properties");
            schema = schema.getJSONObject("identity");
            schema = schema.getJSONObject("properties");
            JSONArray fieldNames = schema.names();

            for(int i = 0; i < fieldNames.length(); ++i) {
                String fieldName = fieldNames.getString(i);
                JSONObject fieldDetail = schema.getJSONObject(fieldName);
                String fieldCategory = fieldDetail.has("fieldCategory") ? fieldDetail.getString("fieldCategory") : "none";
                String packets = (String)categorySubpacketMapping.get(fieldCategory.toLowerCase());
                String[] packetNames = packets.split(",");

                for(String packetName : packetNames) {
                    if (!packetBasedMap.containsKey(packetName)) {
                        packetBasedMap.put(packetName, new ArrayList());
                    }

                    Map<String, String> attributes = new HashMap();
                    attributes.put("id", fieldName);
                    attributes.put("type", fieldDetail.has("$ref") ? fieldDetail.getString("$ref") : fieldDetail.getString("type"));
                    ((List)packetBasedMap.get(packetName)).add(attributes);
                }
            }

            return packetBasedMap;
        } catch (JSONException e) {
            throw new PacketCreatorException(ErrorCode.JSON_PARSE_ERROR.getErrorCode(), "Error While Parsing idschema Json : ".concat(ExceptionUtils.getStackTrace(e)));
        }
    }

    private void addEntryToZip(RegistrationPacket registrationPacket, String fileName, byte[] data, ZipOutputStream zipOutputStream) throws PacketCreatorException {
        LOGGER.info(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.ID.toString(), registrationPacket.getRegistrationId(), "Adding file : " + fileName);

        try {
            if (data != null) {
                ZipEntry zipEntry = new ZipEntry(fileName);
                zipOutputStream.putNextEntry(zipEntry);
                zipOutputStream.write(data);
            }

        } catch (IOException e) {
            throw new PacketCreatorException(ErrorCode.ADD_ZIP_ENTRY_ERROR.getErrorCode(), ErrorCode.ADD_ZIP_ENTRY_ERROR.getErrorMessage().concat(ExceptionUtils.getStackTrace(e)));
        }
    }

    private String getIdentity(Object object) throws JsonProcessingException {
        return "{ \"identity\" : " + JsonUtils.javaObjectToJsonString(object) + " } ";
    }

    private void addOperationsBiometricsToZip(RegistrationPacket registrationPacket, String operationType, ZipOutputStream zipOutputStream, Map<String, HashSequenceMetaInfo> hashSequences, boolean offlineMode) throws PacketCreatorException {
        BiometricRecord biometrics = (BiometricRecord)registrationPacket.getBiometrics().get(operationType);
        if (biometrics != null && biometrics.getSegments() != null && !biometrics.getSegments().isEmpty()) {
            byte[] xmlBytes;
            try {
                xmlBytes = this.packetManagerHelper.getXMLData(biometrics, offlineMode);
            } catch (Exception e) {
                throw new PacketCreatorException(ErrorCode.BIR_TO_XML_ERROR.getErrorCode(), ErrorCode.BIR_TO_XML_ERROR.getErrorMessage().concat(ExceptionUtils.getStackTrace(e)));
            }

            if (xmlBytes != null) {
                String fileName = operationType + ".xml";
                this.addEntryToZip(registrationPacket, fileName, xmlBytes, zipOutputStream);
                registrationPacket.getMetaData().put(String.format("%sBiometricFileName", operationType), fileName);
                this.addHashSequenceWithSource("otherFiles", operationType, xmlBytes, hashSequences);
            }
        }

    }

    public List<PacketInfo> persistPacket(String id, String version, String schemaJson, String source, String process, String additionalInfoReqId, String refId, boolean offlineMode) {
        try {
            return this.createPacket(id, version, schemaJson, source, process, additionalInfoReqId, refId, offlineMode);
        } catch (PacketCreatorException e) {
            LOGGER.error("SESSION_ID", "REGISTRATION_ID", id, ExceptionUtils.getStackTrace(e));
            throw e;
        }
    }

    private String getcurrentTimeStamp() {
        DateTimeFormatter format = DateTimeFormatter.ofPattern(this.zipDatetimePattern);
        return LocalDateTime.now(ZoneId.of("UTC")).format(format);
    }

    public void removePacket(String id) {
        if (this.registrationPacketMap.get(id) != null) {
            this.registrationPacketMap.remove(id);
        }

    }

    static {
        categorySubpacketMapping.put("pvt", "id");
        categorySubpacketMapping.put("kyc", "id");
        categorySubpacketMapping.put("none", "id,evidence,optional");
        categorySubpacketMapping.put("evidence", "evidence");
        categorySubpacketMapping.put("optional", "optional");
    }
}
