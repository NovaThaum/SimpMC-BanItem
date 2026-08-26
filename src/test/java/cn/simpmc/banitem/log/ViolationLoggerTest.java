package cn.simpmc.banitem.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ViolationLoggerTest {
    @Test
    void closePersistsEveryAcceptedRecordInSubmissionOrder() {
        CollectingSink sink = new CollectingSink();
        ViolationLogger logger = new ViolationLogger(sink);
        logger.start();

        for (int amount = 1; amount <= 100; amount++) {
            logger.submit(record(amount));
        }
        logger.close();

        assertEquals(100, sink.records.size());
        assertEquals(1, sink.records.getFirst().amount());
        assertEquals(100, sink.records.getLast().amount());
        assertEquals(101, sink.flushes);
        assertFalse(logger.submit(record(101)));
    }

    @Test
    void flushWaitsForPriorRecordAndNotStartedLoggerRejectsSubmission() {
        CollectingSink sink = new CollectingSink();
        ViolationLogger logger = new ViolationLogger(sink);
        assertThrows(IllegalStateException.class, () -> logger.submit(record(1)));

        logger.start();
        logger.submit(record(1));
        logger.flush();
        assertEquals(List.of(record(1)), sink.records);
        assertEquals(2, sink.flushes);
        logger.close();
    }

    @Test
    void yamlOutputEscapesUserControlledText() throws Exception {
        Path file = Files.createTempDirectory("violation-log-test").resolve("violations.yml");
        ViolationLogger logger = new ViolationLogger(file);
        logger.start();
        logger.submit(new ViolationRecord("A\"B\nC", UUID.randomUUID(), Instant.EPOCH,
                "minecraft:bedrock", 1, "DROP\tBLOCK", "world\\name", 0, 0, 0));
        logger.close();

        String yaml = Files.readString(file);
        assertTrue(yaml.contains("player-name: \"A\\\"B\\nC\""));
        assertTrue(yaml.contains("action: \"DROP\\tBLOCK\""));
        assertTrue(yaml.contains("world: \"world\\\\name\""));
    }

    @Test
    void appendIoFailureDoesNotPreventLaterAcceptedRecordsOrClose() {
        FailingSink sink = new FailingSink(FailurePoint.APPEND_IO);
        ViolationLogger logger = new ViolationLogger(sink);
        logger.start();
        logger.submit(record(1));
        logger.submit(record(2));

        assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> assertThrows(UncheckedIOException.class, logger::close));
        assertEquals(List.of(record(2)), sink.records);
    }

    @Test
    void appendRuntimeFailureIsReportedAndWriterContinues() {
        FailingSink sink = new FailingSink(FailurePoint.APPEND_RUNTIME);
        AtomicReference<Throwable> observedFailure = new AtomicReference<>();
        ViolationLogger logger = new ViolationLogger(sink, observedFailure::set);
        logger.start();
        logger.submit(record(1));
        logger.submit(record(2));

        assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> assertThrows(IllegalStateException.class, logger::close));
        assertEquals(List.of(record(2)), sink.records);
        assertTrue(observedFailure.get() instanceof IllegalStateException);
    }

    @Test
    void runtimeFailureDuringFlushReleasesBarrierAndIsReported() {
        ViolationLogger logger = new ViolationLogger(new FailingSink(FailurePoint.FLUSH_RUNTIME));
        logger.start();
        logger.submit(record(1));

        assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> assertThrows(IllegalStateException.class, logger::flush));
        assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> assertThrows(IllegalStateException.class, logger::close));
    }

    @Test
    void ioFailureDuringFlushReleasesBarrierAndIsReported() {
        ViolationLogger logger = new ViolationLogger(new FailingSink(FailurePoint.FLUSH_IO));
        logger.start();
        logger.submit(record(1));

        assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> assertThrows(UncheckedIOException.class, logger::flush));
        assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> assertThrows(UncheckedIOException.class, logger::close));
    }

    @Test
    void closeFailureIsReportedWithoutLeavingCloseBlocked() {
        ViolationLogger logger = new ViolationLogger(new FailingSink(FailurePoint.CLOSE_RUNTIME));
        logger.start();
        logger.submit(record(1));

        assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> assertThrows(IllegalStateException.class, logger::close));
    }

    @Test
    void ioFailureDuringCloseIsReportedWithoutLeavingCloseBlocked() {
        ViolationLogger logger = new ViolationLogger(new FailingSink(FailurePoint.CLOSE_IO));
        logger.start();
        logger.submit(record(1));

        assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> assertThrows(UncheckedIOException.class, logger::close));
    }

    private static ViolationRecord record(int amount) {
        return new ViolationRecord("Player", UUID.fromString("00000000-0000-0000-0000-000000000001"),
                Instant.parse("2026-08-24T00:00:00Z"), "minecraft:bedrock", amount,
                "CONFISCATE", "world", 1.5, 64, -2.25);
    }

    private static final class CollectingSink implements ViolationLogger.ViolationSink {
        private final List<ViolationRecord> records = new ArrayList<>();
        private int flushes;

        @Override public void append(ViolationRecord record) { records.add(record); }
        @Override public void flush() { flushes++; }
        @Override public void close() { }
    }

    private enum FailurePoint {
        APPEND_IO, APPEND_RUNTIME, FLUSH_IO, FLUSH_RUNTIME, CLOSE_IO, CLOSE_RUNTIME
    }

    private static final class FailingSink implements ViolationLogger.ViolationSink {
        private final FailurePoint failurePoint;
        private final List<ViolationRecord> records = new ArrayList<>();
        private boolean failed;

        private FailingSink(FailurePoint failurePoint) {
            this.failurePoint = failurePoint;
        }

        @Override
        public void append(ViolationRecord record) throws IOException {
            if ((failurePoint == FailurePoint.APPEND_IO || failurePoint == FailurePoint.APPEND_RUNTIME)
                    && !failed) {
                failed = true;
                if (failurePoint == FailurePoint.APPEND_IO) {
                    throw new IOException("injected append failure");
                }
                throw new IllegalStateException("injected append failure");
            }
            records.add(record);
        }

        @Override
        public void flush() throws IOException {
            if (failurePoint == FailurePoint.FLUSH_IO) {
                throw new IOException("injected flush failure");
            }
            if (failurePoint == FailurePoint.FLUSH_RUNTIME) {
                throw new IllegalStateException("injected flush failure");
            }
        }

        @Override
        public void close() throws IOException {
            if (failurePoint == FailurePoint.CLOSE_IO) {
                throw new IOException("injected close failure");
            }
            if (failurePoint == FailurePoint.CLOSE_RUNTIME) {
                throw new IllegalStateException("injected close failure");
            }
        }
    }
}
