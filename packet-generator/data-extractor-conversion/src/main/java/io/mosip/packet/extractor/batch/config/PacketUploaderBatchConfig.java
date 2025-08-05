package io.mosip.packet.extractor.batch.config;

import io.mosip.packet.extractor.batch.impl.upload.PacketUploaderExecutionListener;
import io.mosip.packet.extractor.batch.impl.upload.PacketUploaderTasklet;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PacketUploaderBatchConfig {
    @Autowired
    private JobBuilderFactory jobBuilderFactory;

    @Autowired
    private StepBuilderFactory stepBuilderFactory;

    @Autowired
    private PacketUploaderTasklet packetUploaderTasklet;

    @Bean
    public Step packetUploaderStep() {
        return stepBuilderFactory.get("packetUploaderStep").tasklet(packetUploaderTasklet).build();
    }

    @Bean
    @Qualifier("packetUploaderJob")
    public Job packetUploaderJob(PacketUploaderExecutionListener listener) {
        return jobBuilderFactory.get("packetUploaderJob")
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .flow(packetUploaderStep())
                .end()
                .build();
    }
}
