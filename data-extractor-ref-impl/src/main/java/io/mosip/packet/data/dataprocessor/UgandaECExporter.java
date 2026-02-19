package io.mosip.packet.data.dataprocessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.packet.core.constant.database.DBDriverType;
import io.mosip.packet.core.constant.database.DBTypes;
import io.mosip.packet.core.dto.DataPostProcessorResponseDto;
import io.mosip.packet.core.dto.DataProcessorResponseDto;
import io.mosip.packet.core.exception.ExceptionUtils;
import io.mosip.packet.core.logger.DataProcessLogger;
import io.mosip.packet.core.service.thread.ResultSetter;
import io.mosip.packet.core.spi.datapostprocessor.DataPostProcessor;
import io.mosip.packet.core.util.TrackerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.concurrent.*;

import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_ID;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_NAME;

@Component
public class UgandaECExporter implements DataPostProcessor {
    private static final Logger LOGGER = DataProcessLogger.getLogger(TrackerUtil.class);
    private DataSource dataSource;

    @Autowired
    private Environment env;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${mosip.extractor.uganda.ec.table.mapping:{}}")
    private String tableMapping;

    private List<String> fieldsToStore = new ArrayList<>();

    public final String UG_TABLE_NAME = "UG_DATA_EXPORTER";

    private String preparedQuery;

    private final Object lock = new Object();
    private Semaphore semaphore;

    @Value("${mosip.exporter.uganda.ec.insert.batch.size:5}")
    private int BATCH_SIZE;

    @Value("${mosip.exporter.uganda.ec.parallel.commit.size:5}")
    private int LIMIT_PARALLEL_COMMIT;

    private int total_buffer_parallel_commit;

    private ExecutorService flushExecutor;

    private final ConcurrentLinkedQueue<Map<String, Object>> batchBuffer = new ConcurrentLinkedQueue<>();

    private boolean isInsertLocked = false;
    // ----------------------
    // Main processing entry
    // ----------------------
    @Override
    public DataPostProcessorResponseDto postProcess(DataProcessorResponseDto processObject, ResultSetter setter, Long startTime) throws Exception {
        LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID,
                "Thread - " + processObject.getRefId() + " Time taken to get Connection From Database " +
                        TimeUnit.MILLISECONDS.convert(System.nanoTime() - startTime, TimeUnit.NANOSECONDS));

        Map<String, Object> record = processObject.getResponses();

        boolean isBufferFull = false;
        synchronized (lock) {
            if(batchBuffer.size() > total_buffer_parallel_commit) {
                LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID,
                        "Thread - " + processObject.getRefId() + " Batch Buffer is Greater than Tripple of Batch Size. Halting Record Addition");
                isBufferFull = true;
            }

            batchBuffer.add(record);
            LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID,
                    "Thread - " + processObject.getRefId() + " Batch Buffer size " + batchBuffer.size());

        }

        if(isBufferFull && !isInsertLocked) {
            isInsertLocked = true;
            runFlushAsync(startTime);
            isInsertLocked = false;
        }

        LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID,
                "Thread - " + processObject.getRefId() + " Time taken to Exist the Lock Method " +
                        TimeUnit.MILLISECONDS.convert(System.nanoTime() - startTime, TimeUnit.NANOSECONDS));

        DataPostProcessorResponseDto responseDto = new DataPostProcessorResponseDto();
        responseDto.setProcess(processObject.getProcess());
        responseDto.setRefId(processObject.getRefId());
        responseDto.setTrackerRefId(processObject.getTrackerRefId());
        return responseDto;
    }

    // ----------------------
    // Schedule flush task
    // ----------------------
    private void runFlushAsync(Long startTime) {
        if (batchBuffer.size() < BATCH_SIZE) {
            return;
        }
        List<Map<String, Object>> toFlush;
        synchronized (lock) {
            toFlush = new ArrayList<>(batchBuffer);
            batchBuffer.clear();
        }

        if (toFlush.isEmpty()) {
            LOGGER.debug("Nothing to flush");
            return;
        }

        LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID,
                " Flushing {} records. Elapsed {} ms",
                toFlush.size(),
                TimeUnit.MILLISECONDS.convert(System.nanoTime() - startTime, TimeUnit.NANOSECONDS));

        int idx = 0;
        while (idx < toFlush.size()) {
            int end = Math.min(idx + BATCH_SIZE, toFlush.size());
            List<Map<String, Object>> sub = toFlush.subList(idx, end);

            Runnable task = () -> {
                boolean permitAcquired = false;
                try {
                    long acquireTime = System.nanoTime();
                    semaphore.acquire();
                    permitAcquired = true;
                    LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID,
                            " Available Permits for semaphore is : " +
                                    semaphore.availablePermits() + " at " + TimeUnit.MILLISECONDS.convert(System.nanoTime() - acquireTime, TimeUnit.NANOSECONDS));
                    // perform a single batch flush (extracts exactly BATCH_SIZE records)
                    flushBatch(sub, startTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    LOGGER.error("Flush task interrupted: {}", ie.getMessage());
                } catch (Exception e) {
                    LOGGER.error("Error while flushing batch: {}", ExceptionUtils.getStackTrace(e));
                } finally {
                    if (permitAcquired) {
                        semaphore.release();
                    }
                }
            };

            try {
                flushExecutor.submit(task);
            } catch (RejectedExecutionException rex) {
                LOGGER.error("Flush task rejected; running synchronously: {}", rex.getMessage());
                // fallback synchronously
                task.run();
            }

            idx = end;
        }
    }

    // ----------------------
    // Perform DB insert for the extracted batch
    // ----------------------
    private void flushBatch(List<Map<String, Object>> toFlush, Long startTime) throws Exception {
        LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID,
                " Enter flushBatch at " + TimeUnit.MILLISECONDS.convert(System.nanoTime() - startTime, TimeUnit.NANOSECONDS));

        if (preparedQuery == null || preparedQuery.isEmpty()) {
            LOGGER.error("Prepared query is empty; cannot execute flush.");
            throw new Exception("Prepared query is empty; cannot execute flush.");
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(preparedQuery)) {

            conn.setAutoCommit(false);

            // Optionally chunk large batches; currently toFlush size == BATCH_SIZE usually
            for (Map<String, Object> rec : toFlush) {
                for (int i = 0; i < fieldsToStore.size(); i++) {
                    ps.setObject(i + 1, rec.get(fieldsToStore.get(i)));
                }
                ps.addBatch();
            }

            long commitTime = System.nanoTime();
            ps.executeBatch();
            conn.commit();
            LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID,
                    " Time Taken to Execute Batch & Commit into Database: " +
                            TimeUnit.MILLISECONDS.convert(System.nanoTime() - commitTime, TimeUnit.NANOSECONDS));
        } catch (Exception e) {
            // return records to buffer for retry
            synchronized (lock) {
                // addAll preserves no specific order; consider DLQ or retry-count if repeated failures happen
                batchBuffer.addAll(toFlush);
            }
            LOGGER.error("Flush failed " + ExceptionUtils.getStackTrace(e));
            throw e;
        }
    }

    // ----------------------
    // Lifecycle
    // ----------------------
    @PostConstruct
    public void init() throws Exception {
        try {
            initializeDataSource();
            prepareFields();
            prepareQuery();
            validateTableExists();

            if (BATCH_SIZE <= 0) {
                BATCH_SIZE = 5;
            }


            if (LIMIT_PARALLEL_COMMIT <= 0) {
                LIMIT_PARALLEL_COMMIT = 1;
            }

            total_buffer_parallel_commit = Math.min(((LIMIT_PARALLEL_COMMIT*2)* BATCH_SIZE), 1000);

            semaphore = new Semaphore(LIMIT_PARALLEL_COMMIT);

            // fixed thread pool sized to parallel commit limit. Named threads help debugging.
            flushExecutor = Executors.newFixedThreadPool(LIMIT_PARALLEL_COMMIT, r -> {
                Thread t = new Thread(r, "uganda-ec-flush-" + UUID.randomUUID());
                t.setDaemon(true);
                return t;
            });
        } catch (Exception e) {
            LOGGER.error("INIT_FAILED", APPLICATION_NAME, APPLICATION_ID, "Failed to initialize UgandaECExporter: " + ExceptionUtils.getStackTrace(e));
            throw e;
        }
    }

    /**
     * Shutdown procedure:
     * 1) Stop accepting new flush tasks (shutdown executor)
     * 2) Wait a short time for in-flight tasks to finish
     * 3) Acquire ALL permits (blocks until no flushes running)
     * 4) Synchronously flush any remaining records
     * 5) Release permits and force shutdown if necessary
     */
    @PreDestroy
    public void onShutdown() {
        LOGGER.info("Shutdown initiated for UgandaECExporter");
        // stop accepting new flush tasks
        if (flushExecutor != null) {
            flushExecutor.shutdown(); // don't accept new tasks
        }

        try {
            // wait briefly for current tasks to finish
            if (flushExecutor != null) {
                if (!flushExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    LOGGER.info("Flush executor did not terminate within 5s; will proceed to final flush.");
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while waiting for flushExecutor termination");
        }

        // Acquire all permits -> this blocks until all running flushes release their permits.
        try {
            semaphore.acquire(LIMIT_PARALLEL_COMMIT);
            try {
                // final flush loop: flush until queue empty
                while (true) {
                    List<Map<String, Object>> remaining;
                    synchronized (lock) {
                        if (batchBuffer.isEmpty()) break;
                        remaining = new ArrayList<>(batchBuffer);
                        batchBuffer.clear();
                    }

                    if (!remaining.isEmpty()) {
                        // Execute a synchronous flush for the remaining records in chunks of BATCH_SIZE
                        // We temporarily set them back into the queue in chunks and call flushBatch synchronously
                        // to reuse the existing logic.
                        // Simpler: directly insert remaining using the same PreparedStatement logic here.
                        // We'll reuse existing flushBatch by re-adding in controlled manner.
                        // Put back a chunk and call flushBatch synchronously
                        int idx = 0;
                        while (idx < remaining.size()) {
                            int end = Math.min(idx + BATCH_SIZE, remaining.size());
                            List<Map<String, Object>> sub = remaining.subList(idx, end);
                            flushBatch(sub, System.nanoTime());
                            idx = end;
                        }
                    } else {
                        break;
                    }
                }
            } finally {
                // release permits (not strictly necessary in shutdown, but clean)
                semaphore.release(LIMIT_PARALLEL_COMMIT);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while acquiring all semaphore permits during shutdown");
        } catch (Exception e) {
            LOGGER.error("Error during shutdown final flush: {}", ExceptionUtils.getStackTrace(e));
        } finally {
            if (flushExecutor != null) {
                try {
                    if (!flushExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                        flushExecutor.shutdownNow();
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    flushExecutor.shutdownNow();
                }
            }
            LOGGER.info("UgandaECExporter shutdown complete");
        }
    }

    // -------------------------------------------------------------------------
    // 1. CREATE DATASOURCE INSIDE SAME CLASS
    // -------------------------------------------------------------------------
    private void initializeDataSource() {
        HikariDataSource ds = new HikariDataSource();
        DBTypes dbType = Enum.valueOf(DBTypes.class, env.getProperty("spring.datasource.uganda.ec.dbtype"));
        String driverFormat = env.getProperty("spring.datasource.uganda.ec.driver.format");
        DBDriverType dbDriverType = DBDriverType.valueOf(driverFormat == null ? DBDriverType.DEFAULT.toString() : driverFormat);
        String connectionHost = String.format(dbType.getDriverUrl(dbDriverType), env.getProperty("spring.datasource.uganda.ec.host"), env.getProperty("spring.datasource.uganda.ec.port"), env.getProperty("spring.datasource.uganda.ec.database"));

        ds.setJdbcUrl(connectionHost);
        ds.setUsername(env.getProperty("spring.datasource.uganda.ec.username"));
        ds.setPassword(env.getProperty("spring.datasource.uganda.ec.password"));
        ds.setDriverClassName(dbType.getDriver());

        ds.setMaximumPoolSize(20);
        ds.setMinimumIdle(5);
        ds.setPoolName("UgandaECExporterPool");

        this.dataSource = ds;
    }

    // -------------------------------------------------------------------------
    // 2. Parse fields from JSON mapping
    // -------------------------------------------------------------------------
    private void prepareFields() throws Exception {
        JsonNode node = objectMapper.readTree(tableMapping);
        fieldsToStore.clear();
        Iterator<String> it = node.fieldNames();
        while (it.hasNext()) {
            fieldsToStore.add(it.next());
        }
    }

    // -------------------------------------------------------------------------
    // 3. Build INSERT query
    // -------------------------------------------------------------------------
    private void prepareQuery() {
        if (fieldsToStore.isEmpty()) {
            preparedQuery = null;
            return;
        }
        StringJoiner cols = new StringJoiner(",");
        StringJoiner vals = new StringJoiner(",");
        fieldsToStore.forEach(f -> {
            cols.add(f);
            vals.add("?");
        });
        preparedQuery = "INSERT INTO " + UG_TABLE_NAME + " (" + cols + ") VALUES (" + vals + ")";
    }

    // -------------------------------------------------------------------------
    // 4. Validate table exists
    // -------------------------------------------------------------------------
    private void validateTableExists() throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM " + UG_TABLE_NAME + " LIMIT 1")) {
            ps.executeQuery();
        }
    }
}
