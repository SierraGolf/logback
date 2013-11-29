package ch.qos.logback.classic.net;

import ch.qos.logback.classic.spi.ILoggingEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TimerTask;


// TODO think about directly serializing into the socket without deserializing first
// TODO add dependencies to remove boilerplate (IOUtils, Lists, Optionals)
public class LogSender extends TimerTask {

    private final BufferingSocketAppender socketAppender;
    private final int batchSize;
    private final String logFolderPath;

    public LogSender(
            final BufferingSocketAppender socketAppender,
            final int batchSize,
            final String logFolderPath) {
        this.socketAppender = socketAppender;
        this.batchSize = batchSize;
        this.logFolderPath = logFolderPath;
    }

    @Override
    public void run() {

        if (socketAppender.isNotConnected()) {
            return;
        }

        final List<File> toSend = getFilesToSend();

        for (final File file : toSend) {
            final ILoggingEvent loggingEvent = deserialize(file);

            final boolean couldNotReadLoggingEvent = loggingEvent == null;
            if (couldNotReadLoggingEvent) {
                continue;
            }

            socketAppender.superAppend(loggingEvent);

            if (socketAppender.wasAppendSuccessful()) {
                file.delete();
            }
        }
    }

    private ILoggingEvent deserialize(final File file) {

        FileInputStream fileInputStream = null;
        ObjectInputStream objectInputStream = null;
        try {
            fileInputStream = new FileInputStream(file);
            objectInputStream = new ObjectInputStream(fileInputStream);
            final ILoggingEvent loggingEvent = (ILoggingEvent) objectInputStream.readObject();
            return loggingEvent;
        } catch (final FileNotFoundException e) {
            socketAppender.addError("Could not find logging event on disk.", e);
        } catch (final ClassNotFoundException e) {
            socketAppender.addError("Could not de-serialize logging event from disk.", e);
        } catch (final IOException e) {
            socketAppender.addError("Could not load logging event from disk.", e);
        } finally {
            if (objectInputStream != null) {
                try {
                    objectInputStream.close();
                } catch (final IOException e) {
                    // ignore error on close
                }
            }

            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException e) {
                    // ignore error on close
                }
            }
        }

        return null;
    }

    private List<File> getFilesToSend() {
        final File logFolder = new File(logFolderPath);
        final File[] files = logFolder.listFiles();

        final List<File> ordered = new ArrayList<File>();
        Collections.addAll(ordered, files);
        Collections.sort(ordered, new Comparator<File>() {
            @Override
            public int compare(final File lhs, final File rhs) {

                final long lhsLastModified = lhs.lastModified();
                final long rhsLastModified = rhs.lastModified();

                return lhsLastModified < rhsLastModified ? -1 : (lhsLastModified == rhsLastModified ? 0 : 1);
            }
        });

        final int lastIndex = ordered.size() - 1;
        final int lastIndexOrBatchSize = Math.min(lastIndex, batchSize);
        return ordered.subList(0, lastIndexOrBatchSize);
    }
}
