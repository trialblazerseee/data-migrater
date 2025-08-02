package io.mosip.packet.core.service.thread;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.packet.core.constant.activity.ActivityName;
import io.mosip.packet.core.logger.DataProcessLogger;
import io.mosip.packet.core.util.FixedListQueue;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static io.mosip.packet.core.constant.GlobalConfig.*;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_ID;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_NAME;

@Getter
@Setter
public class CustomizedThreadPoolExecutor {
    ThreadPoolExecutor threadPoolExecutor;
    private int maxPoolSize;
    private int corePoolSize;
    private Long DELAY_SECONDS = 60000L;
    private int queueSize;
    private AtomicInteger totalTaskCount = new AtomicInteger();
    private AtomicInteger totalCompletedTaskCount = new AtomicInteger();
    private AtomicInteger failedRecordCount = new AtomicInteger();
    private AtomicInteger currentPendingCount = new AtomicInteger();
    private int countOfZeroActiveCount = 0;
    private static final Logger LOGGER = DataProcessLogger.getLogger(CustomizedThreadPoolExecutor.class);

    public long getFailedRecordCount() {
        return failedRecordCount.longValue();
    }

    public void increaseFailedRecordCount() {
        failedRecordCount.incrementAndGet();
        TOTAL_FAILED_RECORDS.incrementAndGet();
    }

    private Timer watch = null;
    private Timer estimateTimer = null;
    private String NAME;
    private FixedListQueue<Long> timeConsumptionPerMin = new FixedListQueue<>(100);
    private FixedListQueue<Integer> countOfProcessPerMin = new FixedListQueue<>(100);
    private boolean isInputProcessCompleted = false;
    private boolean isCompletionCountRequired = false;
    private String trackActivityForCompletion = null;

    public long getTotalCompletedTaskCount() {
        return totalCompletedTaskCount.longValue();
    }

    public long getTotalTaskCount() {
        return totalTaskCount.longValue();
    }

    CountIncrementer failedIncrement = new CountIncrementer() {
        @Override
        public void increment() {
            increaseFailedRecordCount();
        }
    };

    public CustomizedThreadPoolExecutor(Integer corePoolSize, String poolName) {
        this(corePoolSize, corePoolSize, Integer.MAX_VALUE, poolName, true, null);
    }

    public CustomizedThreadPoolExecutor(Integer corePoolSize, Integer queueSize, String poolName) {
        this(corePoolSize, corePoolSize, queueSize, poolName, true, null);
    }

    public CustomizedThreadPoolExecutor(Integer corePoolSize, Integer maxPoolSize, Integer queueSize, String poolName) {
        this(corePoolSize, maxPoolSize, queueSize, poolName, true, null);
    }

    public CustomizedThreadPoolExecutor(Integer corePoolSize, Integer maxPoolSize, Integer queueSize, String poolName, Boolean monitorRequired) {
        this(corePoolSize, maxPoolSize, queueSize, poolName, monitorRequired, null);
    }

    public CustomizedThreadPoolExecutor(Integer corePoolSize, Integer maxPoolSize, Integer queueSize, String poolName, Boolean monitorRequired, ActivityName trackActivity) {
        this.NAME = poolName;
        this.corePoolSize = corePoolSize;
        this.queueSize = queueSize;
        this.maxPoolSize = maxPoolSize;

        if(trackActivity != null) {
            this.isCompletionCountRequired = true;
            this.trackActivityForCompletion = trackActivity.getActivityName();
            COMPLETION_COUNT_MAP.put(this.trackActivityForCompletion, Long.valueOf(0L));
        }

        RejectedExecutionHandler blockingHandler = (r, executor) -> {
            try {
                executor.getQueue().put(r);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RejectedExecutionException("Interrupted while waiting for queue", e);
            }
        };

        threadPoolExecutor = new ThreadPoolExecutor(this.corePoolSize, this.maxPoolSize, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(this.queueSize), blockingHandler);
         if(monitorRequired) {
            estimateTimer = new Timer("Estimate Time Calculator");
            estimateTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        if (TIMECONSUPTIONQUEUE != null && TIMECONSUPTIONQUEUE.size() > 0) {
                            FixedListQueue<Long> listQueue = (FixedListQueue<Long>) TIMECONSUPTIONQUEUE.clone();
                            TIMECONSUPTIONQUEUE.clear();

                            Long avgTime = 0l;
                            Long[] consumedTimeList = listQueue.toArray(new Long[listQueue.size()]);

                            Long TotalSum = Arrays.stream(consumedTimeList).mapToLong(Long::longValue).sum();
                            int noOfRecords = consumedTimeList.length;
                            if(noOfRecords > 0)
                                avgTime = TotalSum / noOfRecords;

                            timeConsumptionPerMin.add(avgTime);
                            countOfProcessPerMin.add(noOfRecords);

                        }
                    } catch (Exception e){}

                }
            }, 0, DELAY_SECONDS);
        }

        watch = new Timer("ThreadPool_Wathcer");
        watch.schedule(new TimerTask() {
            @Override
            public void run() {
                printProcessingStatus(monitorRequired);
            }
        }, 0, 120000L);

        THREAD_POOL_EXECUTOR_LIST.add(this);
    }

    private void printProcessingStatus(boolean monitorRequired) {
        int totalDays = 0;
        int totalHours = 0;
        int remainingMinutes =0;
        long avgTime = 0l;
        int avgCount = 0;

        try {
            if(threadPoolExecutor.getActiveCount() <= 0)
                countOfZeroActiveCount++;
            else
                countOfZeroActiveCount=0;

            if(totalTaskCount.longValue() > 0  && monitorRequired) {
                // Calculating Estimated Time of Process Completion
                if(timeConsumptionPerMin != null && timeConsumptionPerMin.size() > 0) {
                    FixedListQueue<Long> listQueue = (FixedListQueue<Long>) timeConsumptionPerMin.clone();
                    FixedListQueue<Integer> countQueue = (FixedListQueue<Integer>)countOfProcessPerMin.clone();

                    Long[] consumedTimeList = listQueue.toArray(new Long[listQueue.size()]);
                    long totalRecords = TOTAL_RECORDS_FOR_PROCESS.longValue();
                    long TotalSum = Arrays.stream(consumedTimeList).mapToLong(Long::longValue).sum();
                    int noOfRecords = consumedTimeList.length;

                    Integer[] consumedCountList = countQueue.toArray(new Integer[countQueue.size()]);
                    int TotalCountSum = Arrays.stream(consumedCountList).mapToInt(Integer::intValue).sum();
                    int noOfCountRecords = consumedCountList.length;
                    avgCount = TotalCountSum/noOfCountRecords;

                    long remainingRecords = totalRecords - (totalCompletedTaskCount.get() + failedRecordCount.get());
                    avgTime = TotalSum / noOfRecords;
                    long totalTimeRequired = (remainingRecords / avgCount);

                    totalHours = (int) (totalTimeRequired / 60);
                    totalDays = (int) totalHours / 24;
                    totalHours = (int) (totalHours % 24);
                    remainingMinutes = (int) (totalTimeRequired % 60);
                }

                System.out.println("Pool Name : " + NAME + " Avg Count per Min.: " + avgCount + " Avg Time per Record : " + TimeUnit.SECONDS.convert(avgTime, TimeUnit.MILLISECONDS) + " S  Estimate Time of Completion : " + totalDays + "D " + totalHours + "H " + remainingMinutes + "M" +"  Total Records for Process : " + TOTAL_RECORDS_FOR_PROCESS + ", Failed in Previous Batch : " + TOTAL_FAILED_RECORDS + ", Total Task : " + totalTaskCount  + ", Active Task : " + threadPoolExecutor.getActiveCount() + ", Completed Task : " + totalCompletedTaskCount + ", Failed Task : " + failedRecordCount + (isCompletionCountRequired ? ", No of "+ trackActivityForCompletion + ", Completed : " +  COMPLETION_COUNT_MAP.get(trackActivityForCompletion) : "."));
                LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Pool Name : " + NAME + " Avg Count per Min.: " + avgCount + " Avg Time per Record : " + TimeUnit.SECONDS.convert(avgTime, TimeUnit.MILLISECONDS) + " S  Estimate Time of Completion : " + totalDays + "D " + totalHours + "H " + remainingMinutes + "M" +"  Total Records for Process : " + TOTAL_RECORDS_FOR_PROCESS + ", Failed in Previous Batch : " + TOTAL_FAILED_RECORDS + ", Total Task : " + (totalTaskCount)  + ", Active Task : " + threadPoolExecutor.getActiveCount() + ", Completed Task : " + totalCompletedTaskCount + ", Failed Task : " + failedRecordCount + (isCompletionCountRequired ? ", No of "+ trackActivityForCompletion + ", Completed : " + COMPLETION_COUNT_MAP.get(trackActivityForCompletion) : "."));
            }
        } catch (Exception e) {}
    }

    public void ExecuteTask(BaseThreadController task) throws InterruptedException {
        task.setPoolName(NAME);
        task.setFailedRecordCount(failedIncrement);

        task.setResponse(new BaseThreadController.SuuccessResponse() {
            @Override
            public void onSuccess() {
                currentPendingCount.decrementAndGet();
                totalCompletedTaskCount.incrementAndGet();

                COMPLETION_COUNT_MAP.merge(NAME, 1L, Long::sum);
            }

            @Override
            public void onFailure() {
                currentPendingCount.decrementAndGet();
            }
        });

        currentPendingCount.incrementAndGet();
        totalTaskCount.incrementAndGet();
        threadPoolExecutor.execute(task);
    }

    public boolean isBatchAcceptRequest() {
        return threadPoolExecutor.getActiveCount() > 0;
    }

    public void setInputProcessCompleted(Boolean isCompleted) {
        this.isInputProcessCompleted = isCompleted;
    }

    public boolean getInputProcessCompleted() {
        return this.isInputProcessCompleted;
    }

    public Timer getWatch() {
        return watch;
    }

    public void stopWatch() {
        printProcessingStatus(true);
        if(getWatch() != null)
            getWatch().cancel();
        if(getEstimateTimer() != null)
            getEstimateTimer().cancel();
    }

    public Timer getEstimateTimer() {
        return estimateTimer;
    }

    public Long getCurrentCompletedTask() {
        return totalCompletedTaskCount.longValue();
    }

    public Long getCurrentPendingCount() {
        return currentPendingCount.longValue();
    }

    public int getActiveCount() {
        return threadPoolExecutor.getActiveCount();
    }
}
