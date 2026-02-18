package io.mosip.packet.extractor.batch.config;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.packet.core.constant.GlobalConfig;
import io.mosip.packet.core.constant.activity.ActivityName;
import io.mosip.packet.core.event.AppReadyEvent;
import io.mosip.packet.core.logger.DataProcessLogger;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.UUID;

import static io.mosip.packet.core.constant.GlobalConfig.DATA_EXPORTER_JOB_DELAY;
import static io.mosip.packet.core.constant.GlobalConfig.DATA_EXPORTER_JOB_INITIAL_DELAY;

@Configuration
@EnableBatchProcessing
@EnableScheduling
@DependsOn("packetUploaderBatchConfig")
public class BatchSchedulerConfig {
    private Logger LOGGER = DataProcessLogger.getLogger(BatchSchedulerConfig.class);

    private final String BATCH_DEFAULT_DELAY = "1000";

    private final String BATCH_DEFAULT_INITIAL_DELAY = "10000";

    private boolean appReady = false;

    @Value("${data.migrator.data.exporter.enable:true}")
    private boolean enablePacketUpload;

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("packetUploaderJob")
    private Job packetUploaderJob;

    @EventListener(AppReadyEvent.class)
    public void onApplicationReady() {
        LOGGER.info("Application is fully initialized. Scheduler will now start.");
        appReady = true;
    }

    @Scheduled(
            initialDelayString = "${" + DATA_EXPORTER_JOB_INITIAL_DELAY + ":" + BATCH_DEFAULT_INITIAL_DELAY + "}",
            fixedDelayString = "${" + DATA_EXPORTER_JOB_DELAY + ":" + BATCH_DEFAULT_DELAY + "}")
    public void schedulePacketUploader() {
        if(!appReady || !GlobalConfig.getApplicableActivityList().contains(ActivityName.DATA_EXPORTER) || !enablePacketUpload)
            return;

        try {
            JobParameters jobParameters = new JobParametersBuilder().addLong("time", System.currentTimeMillis())
                    .addString("uuid", UUID.randomUUID().toString())
                    .toJobParameters();
            jobLauncher.run(packetUploaderJob, jobParameters);
        } catch (Exception e) {
            LOGGER.error("unable to launch job for credential store batch: {}", e.getMessage(), e);
        }
    }

}
