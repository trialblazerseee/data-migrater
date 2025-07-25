package io.mosip.packet.extractor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.dataaccess.hibernate.config.HibernateDaoConfig;
import io.mosip.kernel.dataaccess.hibernate.repository.impl.HibernateRepositoryImpl;
import io.mosip.packet.core.config.ApplicationConfig;
import io.mosip.packet.core.config.activity.Activity;
import io.mosip.packet.core.constant.GlobalConfig;
import io.mosip.packet.core.constant.activity.ActivityName;
import io.mosip.packet.core.dto.RequestWrapper;
import io.mosip.packet.core.dto.dbimport.DBImportRequest;
import io.mosip.packet.core.dto.dbimport.PacketCreatorResponse;
import io.mosip.packet.core.logger.DataProcessLogger;
import io.mosip.packet.core.spi.datareprocessor.DataReProcessorApiFactory;
import io.mosip.packet.core.util.regclient.ConfigUtil;
import io.mosip.packet.extractor.service.DataExtractionService;
import io.mosip.packet.manager.util.mock.sbi.devicehelper.MockDeviceUtil;
import org.apache.commons.lang.RandomStringUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.io.FileInputStream;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import static io.mosip.packet.core.constant.GlobalConfig.*;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_ID;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_NAME;

@SpringBootApplication(scanBasePackages = {"io.mosip.packet.*", "${mosip.auth.adapter.impl.basepackage}", "io.mosip.kernel.clientcrypto.*", "io.mosip.kernel.dataaccess", "io.mosip.kernel.keymanagerservice.*", "io.mosip.kernel.biometrics.*", "io.mosip.kernel.cbeffutil.*"}, exclude = {SecurityAutoConfiguration.class, HibernateDaoConfig.class, HibernateJpaAutoConfiguration.class})
@EntityScan(basePackages = {"io.mosip.packet.core.entity", "io.mosip.kernel.idgenerator.rid.entity", "io.mosip.kernel.keymanagerservice.entity"})
@EnableJpaRepositories(basePackages = {"io.mosip.packet.core.repository", "io.mosip.kernel.idgenerator.rid.repository", "io.mosip.kernel.keymanagerservice.repository"}, repositoryBaseClass = HibernateRepositoryImpl.class)
public class DataProcessApplication {

    public static void main(String[] args) {
        Logger LOGGER = DataProcessLogger.getLogger(DataProcessApplication.class);
        ConfigurableApplicationContext context = SpringApplication.run(DataProcessApplication.class, args);
        try {
            ApplicationConfig appConfig = context.getBean(ApplicationConfig.class);

            context.getBean(MockDeviceUtil.class).resetDevices();
            context.getBean(MockDeviceUtil.class).initDeviceHelpers();
            context.getBean(ConfigUtil.class).loadConfigDetails();
            GlobalConfig.setActivity(context.getBean(Activity.class).setActivity(null));

            if (GlobalConfig.getApplicableActivityList().contains(ActivityName.DATA_REPROCESSOR))
                context.getBean(DataReProcessorApiFactory.class).reProcess();

            if (appConfig.isReferInernalJsonRequestFile()) {
                String option = "";

                if (!appConfig.isRunningAsBatch()) {
                    System.out.println("Current Flow Enabled for  " + getActivityName() + " . Do you want to Continue (Y-Yes, N-No)");
                    Scanner scanner = new Scanner(System.in);
                    option = scanner.next();
                } else {
                    System.out.println("Current Flow Enabled for  " + getActivityName());
                    option = "Y";
                }
                LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Current Flow Enabled for  " + getActivityName());


                if (option.equalsIgnoreCase("Y")) {
                    FileInputStream io = new FileInputStream("./ApiRequest.json");
                    String requestJson = new String(io.readAllBytes(), StandardCharsets.UTF_8);
                    ObjectMapper mapper = new ObjectMapper();
                    RequestWrapper<DBImportRequest> request = mapper.readValue(requestJson, new TypeReference<RequestWrapper<DBImportRequest>>() {
                    });
                    LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Request  : " + (new Gson()).toJson(request));
                    PacketCreatorResponse response = context.getBean(DataExtractionService.class).createPacketFromDataBase(request.getRequest());
                    LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Response  : " + (new Gson()).toJson(response));
                }

                System.exit(0);
            } else {
                System.out.println("Current Flow Enabled for  " + getActivityName());
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
    }
}
