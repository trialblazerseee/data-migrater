package io.mosip.packet.core.config.dbconfig;

import com.zaxxer.hikari.HikariDataSource;
import io.mosip.packet.core.config.ApplicationConfig;
import io.mosip.packet.core.constant.database.DBDriverType;
import io.mosip.packet.core.constant.database.DBTypes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.util.Objects;

@Configuration
@DependsOn("applicationConfig")
public class DBConfig {

    @Autowired
    private Environment env;

    @Autowired
    private ApplicationConfig appConfig;

    @Bean("trackerDataSource")
    public DataSource trackerDataSource() {
        if(!appConfig.isTrackerEnabled())
            return null;

        DBTypes dbType = Enum.valueOf(DBTypes.class, Objects.requireNonNull(env.getProperty("spring.datasource.tracker.dbtype")));
        String driverFormat = env.getProperty("spring.datasource.tracker.driver.format");
        DBDriverType dbDriverType = DBDriverType.valueOf(driverFormat == null ? DBDriverType.DEFAULT.toString() : driverFormat);

        HikariDataSource ds = new HikariDataSource();

        String url = String.format(
                dbType.getDriverUrl(dbDriverType),
                env.getProperty("spring.datasource.tracker.host"),
                env.getProperty("spring.datasource.tracker.port"),
                env.getProperty("spring.datasource.tracker.database"));

        ds.setJdbcUrl(url);
        ds.setUsername(env.getProperty("spring.datasource.tracker.username"));
        ds.setPassword(env.getProperty("spring.datasource.tracker.password"));
        ds.setDriverClassName(dbType.getDriver());

        return ds;
    }
}
