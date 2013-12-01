package ch.qos.logback.classic.net;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Timer;
import java.util.UUID;

public class FileBufferingSocketAppender extends SocketAppender {

    private static final String FILE_ENDING = ".ser";
    private static final String DEFAULT_LOG_FOLDER = "/sdcard/logs/";
    private static final int DEFAULT_BATCH_SIZE = 50;
    private static final long DEFAULT_SEND_INTERVAL = 60000;

    private String logFolder = DEFAULT_LOG_FOLDER;
    private int batchSize = DEFAULT_BATCH_SIZE;
    private long sendInterval = DEFAULT_SEND_INTERVAL;

    private Timer timer;

    @Override
    public void start() {
        super.start();
        final LogSender logSender = new LogSender(this);
        timer = new Timer();
        timer.schedule(logSender, getSendInterval(), getSendInterval());
    }

    @Override
    protected void append(final ILoggingEvent event) {
        createLogFolderIfAbsent();

        FileOutputStream fileOutputStream = null;
        ObjectOutputStream outputStream = null;
        try {
            final String fileName = generateFileName();
            fileOutputStream = new FileOutputStream(fileName);
            outputStream = new ObjectOutputStream(fileOutputStream);
            outputStream.writeObject(LoggingEventVO.build(event));
            outputStream.close();
            fileOutputStream.close();
        } catch (final IOException e) {
            addError("Could not write logging event to disk.", e);
        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (final IOException e) {
                    // ignore error on close
                }
            }

            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    // ignore error on close
                }
            }
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

}
