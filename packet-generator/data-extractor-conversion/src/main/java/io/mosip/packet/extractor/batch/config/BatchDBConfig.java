package io.mosip.packet.extractor.batch.config;

import org.springframework.batch.core.configuration.annotation.DefaultBatchConfigurer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;

@Configuration
public class BatchDBConfig extends DefaultBatchConfigurer {

    private DataSource trackDataSource;
    public BatchDBConfig(
            @Qualifier("trackerDataSource") DataSource trackerDataSource) {
        super(trackerDataSource);
        this.trackDataSource = trackerDataSource;
    }

    @Override
    @PostConstruct
    public void initialize() {
        ResourceDatabasePopulator populator =
                new ResourceDatabasePopulator();

        populator.addScript(
                new ClassPathResource(
                        "org/springframework/batch/core/schema-postgresql.sql"));

        populator.execute(trackDataSource);
        super.initialize();
    }
}
