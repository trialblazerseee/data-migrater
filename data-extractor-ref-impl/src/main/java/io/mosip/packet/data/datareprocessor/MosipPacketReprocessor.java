package io.mosip.packet.data.datareprocessor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.mosip.kernel.clientcrypto.service.impl.ClientCryptoFacade;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.packet.core.config.ApplicationConfig;
import io.mosip.packet.core.constant.GlobalConfig;
import io.mosip.packet.core.constant.activity.ActivityName;
import io.mosip.packet.core.constant.tracker.TrackerStatus;
import io.mosip.packet.core.dto.dbimport.PacketCreatorResponse;
import io.mosip.packet.core.dto.tracker.TrackerRequestDto;
import io.mosip.packet.core.dto.upload.PacketUploadDTO;
import io.mosip.packet.core.dto.upload.PacketUploadResponseDTO;
import io.mosip.packet.core.entity.PacketTracker;
import io.mosip.packet.core.logger.DataProcessLogger;
import io.mosip.packet.core.repository.PacketTrackerRepository;
import io.mosip.packet.core.service.thread.*;
import io.mosip.packet.core.spi.datareprocessor.DataReProcessor;
import io.mosip.packet.core.util.TrackerUtil;
import io.mosip.packet.core.util.regclient.ConfigUtil;
import io.mosip.packet.uploader.service.PacketUploaderService;
import org.apache.commons.io.IOUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_ID;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_NAME;

@Component
public class MosipPacketReprocessor implements DataReProcessor {

    @Autowired
    private PacketTrackerRepository packetTrackerRepository;

    @Value("${mosip.packet.creater.max-threadpool-count:1}")
    private Integer maxThreadPoolCount;

    @Value("${mosip.packet.creater.max-records-process-per-threadpool:100}")
    private Integer maxRecordsCountPerThreadPool;

    @Value("${mosip.packet.creater.max-thread-execution-count:100}")
    private Integer maxThreadExecCount;

    @Value("${packet.manager.account.name}")
    private String packetUploadPath;

    @Value("${mosip.selected.languages}")
    private String primaryLanguage;

    @Value("${mosip.packet.reprocess.status.list}")
    private String reprocessStatusList;

    @Autowired
    PacketUploaderService packetUploaderService;

    @Autowired
    TrackerUtil trackerUtil;

    @Autowired
    ApplicationConfig appConfig;

    @Autowired
    private ClientCryptoFacade clientCryptoFacade;

    private ObjectMapper objectMapper = new ObjectMapper();

    private static final Logger LOGGER = DataProcessLogger.getLogger(MosipPacketReprocessor.class);

    @Override
    public void reProcess() throws Exception {
        System.out.println("Process : RE-PROCESSOR started");
        LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Process : RE-PROCESSOR started");

        PacketCreatorResponse packetCreatorResponse = new PacketCreatorResponse();
        packetCreatorResponse.setRID(new ArrayList<>());

        ResultSetter setter = new ResultSetter() {
            @Override
            public void setResult(Object obj) throws SQLException, IOException, InterruptedException {
                ResultDto resultDto = (ResultDto) obj;
                packetCreatorResponse.getRID().add(resultDto.getRegNo());
                TrackerRequestDto trackerRequestDto = new TrackerRequestDto();
                trackerRequestDto.setRegNo(resultDto.getRegNo());
                trackerRequestDto.setRefId(resultDto.getRefId());
                trackerRequestDto.setSessionId(GlobalConfig.getSessionId());
                trackerRequestDto.setRunInstanceId(appConfig.getPredefinedRunInstanceId());
                if (GlobalConfig.getApplicableActivityList().contains(ActivityName.DATA_EXPORTER)) {
                    trackerRequestDto.setStatus(TrackerStatus.PROCESSED.toString());
                } else {
                    trackerRequestDto.setStatus(TrackerStatus.PROCESSED_WITHOUT_UPLOAD.toString());
                }
                trackerUtil.addTrackerEntry(trackerRequestDto);
            }
        };

        CustomizedThreadPoolExecutor threadPool = new CustomizedThreadPoolExecutor(maxThreadPoolCount, maxThreadExecCount, maxRecordsCountPerThreadPool, "RE-PROCESSOR");

        List<String> statusList = Arrays.asList(reprocessStatusList.split(","));

        List<PacketTracker> trackerList =  packetTrackerRepository.findByStatusIn(statusList);

        System.out.println("Process : RE-PROCESSOR : Found " + trackerList.size() + " Records for Process");
        LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Process : RE-PROCESSOR : Found " + trackerList.size() + " Records for Process");

        for(PacketTracker packetTracker : trackerList) {
            ThreadReprocessorController controller = new ThreadReprocessorController();
            controller.setPacketTracker(packetTracker);
            controller.setSetter(setter);
            controller.setProcessor(new ThreadReprocessor() {
                @Override
                public void processData(PacketTracker packetTracker) throws Exception {
                    ByteArrayInputStream bis = new ByteArrayInputStream(clientCryptoFacade.getClientSecurity().isTPMInstance() ? clientCryptoFacade.decrypt(Base64.getDecoder().decode(packetTracker.getRequest())) : Base64.getDecoder().decode(packetTracker.getRequest()));
                    ObjectInputStream is = new ObjectInputStream(bis);
                    Object requestValue = (Object) is.readObject();
                    is.close();
                    bis.close();

                    if(packetTracker.getStatus().equals(TrackerStatus.CREATED.toString()) || packetTracker.getStatus().equals(TrackerStatus.PROCESSED_WITHOUT_UPLOAD.toString())) {
                        HashMap<String, Object> demoDetails = objectMapper.readValue(requestValue.toString(), new TypeReference<HashMap<String, Object>>() {});

                        Path identityFile = Paths.get(System.getProperty("user.dir"), "identity.json");

                        if (identityFile.toFile().exists()) {
                            PacketUploadDTO uploadDTO = new PacketUploadDTO();

                            JSONParser parser = new JSONParser();
                            JSONObject jsonObject = (JSONObject) parser.parse(IOUtils.toString(new FileInputStream(identityFile.toFile()), StandardCharsets.UTF_8));
                            JSONObject identityJsonObject = (JSONObject) jsonObject.get("identity");
                            for (Object entry : identityJsonObject.keySet()) {
                                String val = (String) ((JSONObject)identityJsonObject.get(entry)).get("value");
                                if (val.contains(",")) {
                                    String[] valList = val.split(",");
                                    String fullVal = null;

                                    for (String val2 : valList) {
                                        if(fullVal == null) {
                                            fullVal= (String) demoDetails.get(val2);
                                        } else {
                                            fullVal += " " + demoDetails.get(val2);
                                        }
                                    }
                                    uploadDTO.setValue(entry.toString(), fullVal);
                                } else {
                                    uploadDTO.setValue(entry.toString(), demoDetails.get(entry));
                                }
                            }

                            Path path = Paths.get(System.getProperty("user.dir"), "home/" + packetUploadPath);
                            uploadDTO.setPacketPath(path.toAbsolutePath().toString());
                            uploadDTO.setRegistrationType(packetTracker.getProcess());
                            uploadDTO.setPacketId(packetTracker.getRegNo());
                            uploadDTO.setRegistrationId(packetTracker.getRegNo().split("-")[0]);
                            uploadDTO.setLangCode(primaryLanguage);

                            List<PacketUploadDTO> uploadList = new ArrayList<>();
                            uploadList.add(uploadDTO);
                            HashMap<String, PacketUploadResponseDTO> response = new HashMap<>();

                            if(GlobalConfig.getApplicableActivityList().contains(ActivityName.DATA_EXPORTER)) {
                                packetUploaderService.syncPacket(uploadList, ConfigUtil.getConfigUtil().getCenterId(), ConfigUtil.getConfigUtil().getMachineId(), response);
                                trackerUtil.addTrackerLocalEntry(packetTracker.getRefId(), packetTracker.getRegNo(), TrackerStatus.SYNCED, null, objectMapper.writeValueAsBytes(uploadList), null, null, GlobalConfig.getSessionId());
                                packetUploaderService.uploadSyncedPacket(uploadList, response);
                            } else {
                                LOGGER.warn("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Packet Uploader Disabled : "+ (new Gson()).toJson(response));
                            }

                            ResultDto resultDto = new ResultDto();
                            resultDto.setRegNo(packetTracker.getRegNo());
                            resultDto.setRefId(packetTracker.getRefId());
                            setter.setResult(resultDto);
                            LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Packet Upload Response : "+ (new Gson()).toJson(response));
                        } else {
                            throw new Exception("Identity Mapping JSON File missing");
                        }
                    } else if(packetTracker.getStatus().equals(TrackerStatus.SYNCED.toString())) {
                        List<PacketUploadDTO> uploadList = (List<PacketUploadDTO>)requestValue;
                        HashMap<String, PacketUploadResponseDTO> response = new HashMap<>();

                        if(GlobalConfig.getApplicableActivityList().contains(ActivityName.DATA_EXPORTER)) {
                            packetUploaderService.uploadSyncedPacket(uploadList, response);
                        } else {
                            LOGGER.warn("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Packet Uploader Disabled : "+ (new Gson()).toJson(response));
                        }

                        ResultDto resultDto = new ResultDto();
                        resultDto.setRegNo(packetTracker.getRegNo());
                        resultDto.setRefId(packetTracker.getRefId());
                        setter.setResult(resultDto);
                        LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Packet Upload Response : "+ (new Gson()).toJson(response));
                    }  else if(packetTracker.getStatus().equals(TrackerStatus.STARTED.toString())) {

                    }
                }
            });
            threadPool.ExecuteTask(controller);
        }
        threadPool.setInputProcessCompleted(true);

        CountDownLatch countDownLatch = new CountDownLatch(1);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (GlobalConfig.isThreadPoolCompleted("RE-PROCESSOR")) {
                    System.out.println("Reprocessor tasks completed. Shutting down...");
                    countDownLatch.countDown();
                    scheduler.shutdown();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }, 10, 10, TimeUnit.SECONDS);

        countDownLatch.await();
        System.out.println("Process : RE-PROCESSOR : Completed");
        LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Process : RE-PROCESSOR : Completed");
    }

    @Override
    public void connectDataReader() throws Exception {
    }

    @Override
    public void disconnectDataReader() {
    }
}
