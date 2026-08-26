package cn.simpmc.banitem.log;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Writes violations in submission order. Calling submit only queues the entry,
 * so it is safe to call directly from a Folia region or entity event thread.
 */
public final class ViolationLogger implements AutoCloseable {
    private static final long WAIT_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(10);
    private final Object lifecycleLock = new Object();
    private final LinkedBlockingQueue<Command> commands = new LinkedBlockingQueue<>();
    private final ViolationSink sink;
    private final Consumer<Throwable> failureListener;

    private boolean started;
    private boolean accepting;
    private Thread worker;
    private volatile Throwable writeFailure;

    /** Creates a logger that appends a YAML document stream to file. */
    public ViolationLogger(Path file) {
        this(new YamlViolationSink(file), ignored -> { });
    }

    /** Creates a file logger and reports the first persistence failure immediately. */
    public ViolationLogger(Path file, Consumer<Throwable> failureListener) {
        this(new YamlViolationSink(file), failureListener);
    }

    // Package-private for tests and alternate persistence adapters.
    ViolationLogger(ViolationSink sink) {
        this(sink, ignored -> { });
    }

    ViolationLogger(ViolationSink sink, Consumer<Throwable> failureListener) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.failureListener = Objects.requireNonNull(failureListener, "failureListener");
    }

    /** Starts the dedicated virtual writer thread. Calling it more than once has no effect. */
    public void start() {
        synchronized (lifecycleLock) {
            if (started) {
                return;
            }
            started = true;
            accepting = true;
            worker = Thread.ofVirtual().name("SimpMC-BanItem-violation-log").start(this::runWriter);
        }
    }

    /**
     * Queues a violation without file I/O. Returns false after this logger has begun closing.
     * @throws IllegalStateException if start has not been called
     */
    public boolean submit(ViolationRecord record) {
        Objects.requireNonNull(record, "record");
        synchronized (lifecycleLock) {
            if (!started) {
                throw new IllegalStateException("ViolationLogger has not been started");
            }
            if (!accepting) {
                return false;
            }
            commands.add(new RecordCommand(record));
            return true;
        }
    }

    /** Blocks until every record submitted before this call has been written and flushed. */
    public void flush() {
        CountDownLatch barrier;
        synchronized (lifecycleLock) {
            if (!started || !accepting) {
                throw new IllegalStateException("ViolationLogger is not running");
            }
            barrier = new CountDownLatch(1);
            commands.add(new FlushCommand(barrier));
        }
        if (!await(barrier)) {
            fail(new IOException("Timed out waiting for violation records to flush"));
            interruptWriter();
        }
        throwIfWriteFailed();
    }

    /** Stops accepting records, writes all already accepted records, flushes, and closes the file. */
    @Override
    public void close() {
        CountDownLatch stopped;
        synchronized (lifecycleLock) {
            if (!started) {
                return;
            }
            if (!accepting) {
                stopped = null;
            } else {
                accepting = false;
                stopped = new CountDownLatch(1);
                commands.add(new StopCommand(stopped));
            }
        }
        if (stopped != null) {
            if (!await(stopped)) {
                fail(new IOException("Timed out waiting for violation logger to stop"));
                interruptWriter();
            }
        }
        Thread writer = worker;
        if (writer != null && writer != Thread.currentThread()) {
            try {
                writer.join(WAIT_TIMEOUT_MILLIS);
                if (writer.isAlive()) {
                    fail(new IOException("Timed out waiting for violation writer thread to terminate"));
                    writer.interrupt();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                fail(new IOException("Interrupted while closing violation logger", exception));
            }
        }
        throwIfWriteFailed();
    }

    private void runWriter() {
        try {
            runWriterLoop();
        } catch (Throwable failure) {
            fail(failure);
        } finally {
            synchronized (lifecycleLock) {
                accepting = false;
            }
            closeSink();
            drainBarriers();
        }
    }

    private void runWriterLoop() throws InterruptedException {
        while (true) {
            Command command = commands.take();
            if (command instanceof RecordCommand record) {
                write(record.record());
            } else if (command instanceof FlushCommand flush) {
                try {
                    flushSink();
                } finally {
                    flush.done().countDown();
                }
            } else if (command instanceof StopCommand stop) {
                try {
                    flushSink();
                    closeSink();
                } finally {
                    stop.done().countDown();
                }
                return;
            }
        }
    }

    private void write(ViolationRecord record) {
        try {
            sink.append(record);
            // Persist infrequent entries promptly without blocking a Folia region.
            sink.flush();
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private void flushSink() {
        try {
            sink.flush();
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private void closeSink() {
        try {
            sink.close();
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private void fail(Throwable failure) {
        boolean firstFailure = false;
        synchronized (this) {
            if (writeFailure == null) {
                writeFailure = failure;
                firstFailure = true;
            }
        }
        if (firstFailure) {
            try {
                failureListener.accept(failure);
            } catch (Throwable ignored) {
                // A diagnostic callback must never stop barrier release or shutdown.
            }
        }
    }

    private void throwIfWriteFailed() {
        Throwable failure = writeFailure;
        if (failure != null) {
            if (failure instanceof IOException exception) {
                throw new UncheckedIOException("Could not persist one or more violation records", exception);
            }
            throw new IllegalStateException("Could not persist one or more violation records", failure);
        }
    }

    private void drainBarriers() {
        Command command;
        while ((command = commands.poll()) != null) {
            if (command instanceof FlushCommand flush) {
                flush.done().countDown();
            } else if (command instanceof StopCommand stop) {
                stop.done().countDown();
            }
        }
    }

    private void interruptWriter() {
        Thread writer = worker;
        if (writer != null && writer != Thread.currentThread()) {
            writer.interrupt();
        }
    }

    private static boolean await(CountDownLatch latch) {
        try {
            return latch.await(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private sealed interface Command permits RecordCommand, FlushCommand, StopCommand { }
    private record RecordCommand(ViolationRecord record) implements Command { }
    private record FlushCommand(CountDownLatch done) implements Command { }
    private record StopCommand(CountDownLatch done) implements Command { }

    interface ViolationSink extends AutoCloseable {
        void append(ViolationRecord record) throws IOException;
        void flush() throws IOException;
        @Override void close() throws IOException;
    }

    private static final class YamlViolationSink implements ViolationSink {
        private final Path file;
        private BufferedWriter writer;

        private YamlViolationSink(Path file) {
            this.file = Objects.requireNonNull(file, "file");
        }

        @Override
        public void append(ViolationRecord record) throws IOException {
            ensureOpen();
            writer.write("---\n");
            scalar("player-name", record.playerName());
            scalar("player-uuid", record.playerUuid().toString());
            scalar("timestamp", DateTimeFormatter.ISO_INSTANT.format(record.timestamp()));
            scalar("material", record.material());
            writer.write("amount: " + record.amount() + '\n');
            scalar("action", record.action());
            writer.write("location:\n");
            scalarIndented("world", record.world());
            writer.write("  x: " + record.x() + '\n');
            writer.write("  y: " + record.y() + '\n');
            writer.write("  z: " + record.z() + '\n');
        }

        @Override
        public void flush() throws IOException {
            if (writer != null) {
                writer.flush();
            }
        }

        @Override
        public void close() throws IOException {
            if (writer != null) {
                writer.close();
                writer = null;
            }
        }

        private void ensureOpen() throws IOException {
            if (writer == null) {
                Path parent = file.toAbsolutePath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            }
        }

        private void scalar(String name, String value) throws IOException {
            writer.write(name + ": \"" + escape(value) + "\"\n");
        }

        private void scalarIndented(String name, String value) throws IOException {
            writer.write("  " + name + ": \"" + escape(value) + "\"\n");
        }

        private static String escape(String value) {
            StringBuilder escaped = new StringBuilder(value.length());
            for (int i = 0; i < value.length(); i++) {
                char character = value.charAt(i);
                switch (character) {
                    case '\\' -> escaped.append("\\\\");
                    case '"' -> escaped.append("\\\"");
                    case '\n' -> escaped.append("\\n");
                    case '\r' -> escaped.append("\\r");
                    case '\t' -> escaped.append("\\t");
                    default -> {
                        if (Character.isISOControl(character)) {
                            escaped.append(String.format("\\u%04x", (int) character));
                        } else {
                            escaped.append(character);
                        }
                    }
                }
            }
            return escaped.toString();
        }
    }
}
