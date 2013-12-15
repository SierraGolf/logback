package ch.qos.logback.classic.net;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import ch.qos.logback.classic.util.Closeables;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutput;
import java.util.Timer;
import java.util.UUID;

public class FileBufferingSocketAppender extends SocketAppender {

    private static final String DEFAULT_FILE_ENDING = ".ser";
    private static final String DEFAULT_LOG_FOLDER = "/sdcard/logs/";
    private static final int DEFAULT_BATCH_SIZE = 50;
    private static final long DEFAULT_SEND_INTERVAL = 60000;
    private static final int DEFAULT_FILE_COUNT_QUOTA = 500;

    private String logFolder = DEFAULT_LOG_FOLDER;
    private String fileEnding = DEFAULT_FILE_ENDING;
    private int batchSize = DEFAULT_BATCH_SIZE;
    private long readInterval = DEFAULT_SEND_INTERVAL;
    private int fileCountQuota = DEFAULT_FILE_COUNT_QUOTA;

    private final Timer timer;
    private final IOProvider ioProvider;

    public FileBufferingSocketAppender() {
        this(new Timer("LogEventReader"), new IOProvider());
    }

    FileBufferingSocketAppender(final Timer timer, final IOProvider ioProvider) {
        this.timer = timer;
        this.ioProvider = ioProvider;
    }

    @Override
    public void start() {
        super.start();
        final LogFileReader logFileReader = new LogFileReader(this, ioProvider);
        timer.schedule(logFileReader, getReadInterval(), getReadInterval());
    }

    @Override
    protected void append(final ILoggingEvent event) {
        if (!isStarted()) {
            return;
        }

        createLogFolderIfAbsent();
        postProcessEvent(event);

        boolean savedSuccessfully = false;
        final String fileName = generateFileName();
        final String tempName = fileName + "-tmp";
        ObjectOutput objectOutput = null;
        try {
            objectOutput = ioProvider.newObjectOutput(tempName);
            objectOutput.writeObject(LoggingEventVO.build(event));
            savedSuccessfully = true;
        } catch (final IOException e) {
            addError("Could not write logging event to disk.", e);
        } finally {
            Closeables.close(objectOutput);
        }

        if (savedSuccessfully) {
            renameFileFromTempToFinalName(fileName, tempName);
        }
    }

    public void superAppend(final ILoggingEvent event) {
        super.append(event);
    }

    @Override
    public void stop() {
        super.stop();
        timer.cancel();
    }

    public String getLogFolder() {
        return logFolder;
    }

    public void setLogFolder(final String logFolder) {
        this.logFolder = format(logFolder);
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(final int batchSize) {
        this.batchSize = batchSize;
    }

    public long getReadInterval() {
        return readInterval;
    }

    public void setReadInterval(final long readInterval) {
        this.readInterval = readInterval;
    }

    public String getFileEnding() {
        return fileEnding;
    }

    public int getFileCountQuota() {
        return fileCountQuota;
    }

    public void setFileCountQuota(final int fileCountQuota) {
        this.fileCountQuota = fileCountQuota;
    }

    private String format(final String logFolder) {

        if (logFolder.endsWith(File.separator)) {
            return logFolder;
        }

        return logFolder + File.separator;
    }

    private void createLogFolderIfAbsent() {
        final File folder = new File(getLogFolder());
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    private String generateFileName() {
        return getLogFolder() + UUID.randomUUID() + DEFAULT_FILE_ENDING;
    }

    private void renameFileFromTempToFinalName(String fileName, String tempName) {
        try {
            Files.move(new File(tempName), new File(fileName));
        } catch (final IOException e) {
            addError("Could not rename file from " + tempName + " to " + fileName);
        }
    }
}
