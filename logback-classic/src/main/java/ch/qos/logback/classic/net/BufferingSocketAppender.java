package ch.qos.logback.classic.net;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import ch.qos.logback.core.AppenderBase;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Timer;
import java.util.UUID;

// TODO make constants configurable
public class BufferingSocketAppender extends AppenderBase<ILoggingEvent> {

    private static final String FILE_ENDING = ".ser";
    private static final String LOG_FOLDER = "/sdcard/logs/";
    private static final int PORT = 6000;
    private static final String HOST = "10.0.2.2";
    private static final int BATCH_SIZE = 50;
    private static final long SEND_INTERVAL = 60000;
    private static final boolean INCLUDE_CALLER_DATA = true;
    private static final int RECONNECTION_DELAY = 1000;

    private SocketAppender socketAppender;
    private LogSender logSender;
    private Timer timer;

    @Override
    public void start() {
        super.start();
        // TODO maybe replace this through logbacks internal composition strategy
        socketAppender = new SocketAppender(HOST, PORT);
        socketAppender.setContext(getContext());
        socketAppender.setIncludeCallerData(INCLUDE_CALLER_DATA);
        socketAppender.setReconnectionDelay(RECONNECTION_DELAY);
        socketAppender.setName("SOCKET");
        socketAppender.start();
        
        logSender = new LogSender(this, socketAppender, BATCH_SIZE, LOG_FOLDER);
        timer = new Timer();
        timer.schedule(logSender, SEND_INTERVAL, SEND_INTERVAL);
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

    @Override
    public void stop() {
        super.stop();
        socketAppender.stop();
    }

    private void createLogFolderIfAbsent() {
        final File folder = new File(LOG_FOLDER);
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    private String generateFileName() {
        return LOG_FOLDER + UUID.randomUUID() + FILE_ENDING;
    }
}
