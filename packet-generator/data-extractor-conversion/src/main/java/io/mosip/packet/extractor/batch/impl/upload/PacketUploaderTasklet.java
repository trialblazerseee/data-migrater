package io.mosip.packet.extractor.batch.impl.upload;

import com.google.gson.Gson;
import io.mosip.kernel.clientcrypto.service.impl.ClientCryptoFacade;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.packet.core.config.ApplicationConfig;
import io.mosip.packet.core.config.activity.Activity;
import io.mosip.packet.core.constant.GlobalConfig;
import io.mosip.packet.core.constant.activity.ActivityName;
import io.mosip.packet.core.dto.DataPostProcessorResponseDto;
import io.mosip.packet.core.dto.tracker.TrackerRequestDto;
import io.mosip.packet.core.entity.PacketTracker;
import io.mosip.packet.core.exception.ExceptionUtils;
import io.mosip.packet.core.logger.DataProcessLogger;
import io.mosip.packet.core.repository.PacketTrackerRepository;
import io.mosip.packet.core.service.thread.*;
import io.mosip.packet.core.spi.dataexporter.DataExporterApiFactory;
import io.mosip.packet.core.util.TrackerUtil;
import lombok.SneakyThrows;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static io.mosip.packet.core.constant.GlobalConfig.PROCESS;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_ID;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_NAME;

@Component
@DependsOn("activity")
public class PacketUploaderTasklet implements Tasklet {
    private Logger LOGGER = DataProcessLogger.getLogger(PacketUploaderTasklet.class);

    private CustomizedThreadPoolExecutor customizedThreadPoolExecutor = null;

    @Autowired
    private PacketTrackerRepository packetTrackerRepository;

    @Autowired
    private Activity activity;

    @Autowired
    private ClientCryptoFacade clientCryptoFacade;

    @Autowired
    private DataExporterApiFactory dataExporterApiFactory;

    @Autowired
    private ApplicationConfig appConfig;

    @Autowired
    private TrackerUtil trackerUtil;

    private ResultSetter resultSetter = null;

    @Value("${mosip.packet.upload.max-threadpool-count:1}")
    private Integer uploadMaxThreadPoolCount;

    @Value("${mosip.packet.upload.max-records-process-per-threadpool:10000}")
    private Integer uploadMaxRecordsCountPerThreadPool;

    @Value("${mosip.packet.upload.max-thread-execution-count:5}")
    private Integer uploadMaxThreadExecCount;

    private CustomizedThreadPoolExecutor getExecutor() throws Exception {
        if(customizedThreadPoolExecutor == null) {
            Activity exportActivity = activity.getActivity(ActivityName.DATA_EXPORTER.name());
            customizedThreadPoolExecutor = new CustomizedThreadPoolExecutor(uploadMaxThreadPoolCount, uploadMaxThreadExecCount, uploadMaxRecordsCountPerThreadPool, exportActivity.getActivityName().getActivityName(), exportActivity.isMonitorRequired());
        }

        return customizedThreadPoolExecutor;
    }

    private ResultSetter getSetter() {
        if(resultSetter == null) {
            resultSetter = new ResultSetter() {
                @SneakyThrows
                @Override
                public void setResult(Object obj) {
                    ResultDto resultDto = (ResultDto) obj;
  //                  if(!packetCreatorResponse.getRID().contains(resultDto.getRegNo()))
  //                      packetCreatorResponse.getRID().add(resultDto.getRegNo());
                    TrackerRequestDto trackerRequestDto = new TrackerRequestDto();
                    trackerRequestDto.setRegNo(resultDto.getRegNo());
                    trackerRequestDto.setRefId(resultDto.getRefId());
                    trackerRequestDto.setProcess(resultDto.getProcess());
                    trackerRequestDto.setActivity(GlobalConfig.getActivityName());
                    trackerRequestDto.setRunInstanceId(appConfig.getPredefinedRunInstanceId());
                    trackerRequestDto.setSessionId(GlobalConfig.getSessionId());
                    trackerRequestDto.setStatus(resultDto.getStatus().toString());
                    trackerRequestDto.setComments(resultDto.getComments());
                    trackerRequestDto.setAdditionalMaps(resultDto.getAdditionalMaps());
                    trackerUtil.addTrackerEntry(trackerRequestDto);
                }
            };
        }

        return resultSetter;
    }

    @Override
    public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
        String packetId=null;
        try {
                List<String> statusList = new ArrayList<>();
                statusList.add("READY_TO_SYNC");
                List<PacketTracker> trackerList =  packetTrackerRepository.findByStatusIn(statusList);
                CountDownLatch latch = new CountDownLatch(trackerList.size());
                getExecutor().setLatch(latch);

                if(trackerList.size() <= 0) {
                    getExecutor().setInputProcessCompleted(true);
                } else {
                    getExecutor().setInputProcessCompleted(false);
                }

                for(PacketTracker packetTracker : trackerList) {
                    ByteArrayInputStream bis = new ByteArrayInputStream(clientCryptoFacade.getClientSecurity().isTPMInstance() ? clientCryptoFacade.decrypt(Base64.getDecoder().decode(packetTracker.getRequest())) : Base64.getDecoder().decode(packetTracker.getRequest()));
                    ObjectInputStream is = new ObjectInputStream(bis);
                    DataPostProcessorResponseDto responseDto = (DataPostProcessorResponseDto) is.readObject();
                    is.close();
                    bis.close();
                    packetId = responseDto.getRefId();
                    LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Data Export for " + (new Gson()).toJson(responseDto));

                    ThreadUploadController controller = new ThreadUploadController();
                    controller.setResult(responseDto);
                    controller.setSetter(getSetter());
                    controller.setProcessor(new ThreadUploadProcessor() {
                        @Override
                        public void processData(ResultSetter setter, DataPostProcessorResponseDto result) throws Exception {
                            dataExporterApiFactory.export(result, (new Date()).getTime(), setter);
                        }
                    });
                    getExecutor().ExecuteTask(controller);
                }

                latch.await();

            LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Upload Batch Current Pending Count " + getExecutor().getCurrentPendingCount());
        } catch (Exception e) {
            LOGGER.error("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Packet Upload Error for Packet Id : " + packetId + " - " + e.getMessage() + ExceptionUtils.getStackTrace(e));
        }
        return null;
    }
}
