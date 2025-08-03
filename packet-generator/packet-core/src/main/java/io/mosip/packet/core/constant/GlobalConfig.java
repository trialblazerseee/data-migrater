package io.mosip.packet.core.constant;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.packet.core.config.activity.Activity;
import io.mosip.packet.core.constant.activity.ActivityName;
import io.mosip.packet.core.logger.DataProcessLogger;
import io.mosip.packet.core.service.thread.CustomizedThreadPoolExecutor;
import io.mosip.packet.core.util.FixedListQueue;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_ID;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_NAME;

@Component
public class GlobalConfig {
    private static Logger LOGGER = DataProcessLogger.getLogger(GlobalConfig.class);

    public static Boolean IS_ONLY_FOR_QUALITY_CHECK = false;

    public static Boolean IS_NETWORK_AVAILABLE = true;

    public static ConcurrentLinkedQueue<Long> TIMECONSUPTIONQUEUE;

    public static AtomicInteger TOTAL_RECORDS_FOR_PROCESS = new AtomicInteger();

    public static AtomicInteger TOTAL_FAILED_RECORDS = new AtomicInteger();

 //   public static Long ALREADY_PROCESSED_RECORDS = 0L;

    public static Boolean IS_TPM_AVAILABLE;

    public static Boolean IS_DATABASE_READ_OPERATION = false;

    public static Boolean IS_PACKET_CREATOR_OPERATION = false;

    public static Map<String, Long> COMPLETION_COUNT_MAP = new ConcurrentHashMap<>();

    public static List<CustomizedThreadPoolExecutor> THREAD_POOL_EXECUTOR_LIST = new ArrayList<>();

    private static Activity activity;

    public static Map<String , String> PACKET_TRACKER_ADDITIONAL_FIELDS = new HashMap<>();

    public static String getActivityName() {
        return activity.getActivityName().getActivityName();
    }

    public static List<ActivityName> getApplicableActivityList() {
        return activity.getApplicableActivity();
    }

    public static List<ReferenceClassName> getApplicableReferenceClassList() {
        return activity.getApplicableReferenceClass();
    }

    public static void setActivity(Activity activity) {
        GlobalConfig.activity = activity;

        if(activity.getActivityName().equals(ActivityName.DATA_QUALITY_ANALYZER))
            IS_ONLY_FOR_QUALITY_CHECK = true;
        else
            IS_ONLY_FOR_QUALITY_CHECK = false;
    }

    public static boolean isThreadPoolCompleted() throws InterruptedException {
        return isThreadPoolCompleted(null);
    }

    public static boolean isThreadPoolCompleted(String eventName) throws InterruptedException {
        return isTaskCompleted(eventName);
    }

    public static Long getPendingCountForProcess() {
        Long pendingCount = 0L;
        for(CustomizedThreadPoolExecutor executor : THREAD_POOL_EXECUTOR_LIST) {
            if(executor.getNAME().equals("QUALITY ANALYSIS") || executor.getNAME().equals("PACKET CREATOR")) {
                pendingCount += executor.getCurrentPendingCount();
            }
        }
        return pendingCount;
    }

    public static boolean isTaskCompleted(String eventName) throws InterruptedException {
        boolean isCompleted = true;
        Integer pendingTaskCount = 0;
        Boolean isAllProcessCompleted = true;

        for(CustomizedThreadPoolExecutor executor : THREAD_POOL_EXECUTOR_LIST) {
            pendingTaskCount +=executor.getActiveCount();
            LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Pending Task Count in Pool " + executor.getNAME() + " is " + pendingTaskCount);

            LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Is Input Process Completed for Pool " + executor.getNAME() + " is "  + executor.getInputProcessCompleted());
            if(!executor.getInputProcessCompleted())
                isAllProcessCompleted = false;
        }

        LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Pending Task Count " + pendingTaskCount);
        if(pendingTaskCount > 0 || !isAllProcessCompleted)
            return false;

        LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Is Database Read Operation ? " + IS_DATABASE_READ_OPERATION);
        LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Is Packet Creator Operation ? " + IS_PACKET_CREATOR_OPERATION);
        if(!IS_DATABASE_READ_OPERATION && !IS_PACKET_CREATOR_OPERATION)
            for(CustomizedThreadPoolExecutor executor : THREAD_POOL_EXECUTOR_LIST) {
                if(eventName == null || eventName.equals(executor.getNAME())) {
                    if(executor.getActiveCount() > 0) {
                        LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Pool : " + executor.getNAME() + " No of Active Task " + executor.getActiveCount());
                        isCompleted = false;
                        break;
                    } else {
                        LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Pool : " + executor.getNAME() + " Input Process Completed " + executor.getInputProcessCompleted());
                        LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Pool : " + executor.getNAME() + " Pending Task Count " + (executor.getTotalTaskCount() - executor.getCurrentCompletedTask()));
                        LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Pool : " + executor.getNAME() + " Is Batch Accept Request " + executor.isBatchAcceptRequest());
                        LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Pool : " + executor.getNAME() + " Current Pending Task Count " + executor.getCurrentPendingCount());
                        if(executor.getInputProcessCompleted() && (executor.getTotalTaskCount() - executor.getTotalCompletedTaskCount() - executor.getFailedRecordCount() <= 0) && executor.getCurrentPendingCount() <= 0) {
                            if(executor.getNAME().equals("QUALITY ANALYSIS")) {
                                LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Pool : " + executor.getNAME() + " TOTAL_RECORDS_FOR_PROCESS " + TOTAL_RECORDS_FOR_PROCESS);
                                LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Pool : " + executor.getNAME() + " TOTAL_FAILED_RECORDS " + TOTAL_FAILED_RECORDS);
                                LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Pool : " + executor.getNAME() + " executor.getCurrentCompletedTask() " + executor.getCurrentCompletedTask());
                                LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Pool : " + executor.getNAME() + " executor.getCountOfZeroActiveCount() " + executor.getCountOfZeroActiveCount());
                                if(TOTAL_RECORDS_FOR_PROCESS.get() - TOTAL_FAILED_RECORDS.get() - executor.getCurrentCompletedTask() <= 0 || executor.getCountOfZeroActiveCount() > 10) {
                                    executor.stopWatch();;
                                } else {
                                    isCompleted = false;
                                }
                            } else {
                                executor.stopWatch();
                            }
                        } else {
                            isCompleted = false;
                        }
                    }

                }
            }

        if(IS_DATABASE_READ_OPERATION || IS_PACKET_CREATOR_OPERATION)
            isCompleted = false;

        return isCompleted;
    }
}
