package ch.qos.logback.classic.net;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Timer;
import java.util.UUID;
import org.apache.commons.io.IOUtils;

public class FileBufferingSocketAppender extends SocketAppender {

    private static final String FILE_ENDING = ".ser";
    private static final String DEFAULT_LOG_FOLDER = "/sdcard/logs/";
    private static final int DEFAULT_BATCH_SIZE = 50;
    private static final long DEFAULT_SEND_INTERVAL = 60000;
    private static final int DEFAULT_QUOTA = 500;

    private String logFolder = DEFAULT_LOG_FOLDER;
    private int batchSize = DEFAULT_BATCH_SIZE;
    private long sendInterval = DEFAULT_SEND_INTERVAL;
    private int fileCountQuota = DEFAULT_QUOTA;

    private Timer timer;

    @Override
    public void start() {
        super.start();
        final LogFileReader logFileReader = new LogFileReader(this);
        timer = new Timer();
        timer.schedule(logFileReader, getSendInterval(), getSendInterval());
    }

    @Override
    protected void append(final ILoggingEvent event) {
        if (!isStarted()) {
            return;
        }

        postProcessEvent(event);
        createLogFolderIfAbsent();

        FileOutputStream fileOutputStream = null;
        ObjectOutputStream outputStream = null;
        try {
            final String fileName = generateFileName();
            fileOutputStream = new FileOutputStream(fileName);
            outputStream = new ObjectOutputStream(fileOutputStream);
            outputStream.writeObject(LoggingEventVO.build(event));
        } catch (final IOException e) {
            addError("Could not write logging event to disk.", e);
        } finally {
            IOUtils.closeQuietly(fileOutputStream);
            IOUtils.closeQuietly(outputStream);
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

    private void createLogFolderIfAbsent() {
        final File folder = new File(getLogFolder());
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    private String generateFileName() {
        return getLogFolder() + UUID.randomUUID() + FILE_ENDING;
    }

    public String getLogFolder() {
        return logFolder;
    }

    public void setLogFolder(String logFolder) {
        this.logFolder = logFolder;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getSendInterval() {
        return sendInterval;
    }

    public void setSendInterval(long sendInterval) {
        this.sendInterval = sendInterval;
    }

    public String getFileEnding() {
        return FILE_ENDING;
    }

    public int getFileCountQuota() {
        return fileCountQuota;
    }

    public void setFileCountQuota(int fileCountQuota) {
        this.fileCountQuota = fileCountQuota;
    }
}
