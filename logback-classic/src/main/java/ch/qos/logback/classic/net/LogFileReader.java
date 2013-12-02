package ch.qos.logback.classic.net;

import ch.qos.logback.classic.spi.ILoggingEvent;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TimerTask;

public class LogFileReader extends TimerTask {

    private final FileBufferingSocketAppender appender;

    public LogFileReader(final FileBufferingSocketAppender appender) {
        this.appender = appender;
    }

    @Override
    public void run() {

        if (appender.isNotConnected()) {
            return;
        }

        final List<File> allFilesOrderedByDate = getAllFilesOrderedByDate();
        final List<File> filesToDelete;
        final List<File> filesToSend;

        final int size = allFilesOrderedByDate.size();
        final boolean quotaIsReached = allFilesOrderedByDate.size() > appender.getFileCountQuota();
        if (quotaIsReached) {
            final int lastToBeRemoved = allFilesOrderedByDate.size() - appender.getFileCountQuota();
            filesToDelete = new ArrayList<File>(allFilesOrderedByDate.subList(0, lastToBeRemoved));
            final int lastIndex = Math.min(lastToBeRemoved + appender.getBatchSize(), size);
            filesToSend = new ArrayList<File>(allFilesOrderedByDate.subList(lastToBeRemoved, lastIndex));
        } else {
            filesToDelete = Collections.emptyList();
            final int lastIndex = Math.min(appender.getBatchSize(), size);
            filesToSend = new ArrayList<File>(allFilesOrderedByDate.subList(0, lastIndex));
        }

        delete(filesToDelete);
        send(filesToSend);
    }

    private void delete(final List<File> files) {
        for (final File file : files) {
            file.delete();
        }
    }

    private void send(final List<File> files) {
        for (final File file : files) {
            final ILoggingEvent loggingEvent = deserialize(file);

            final boolean couldNotReadLoggingEvent = loggingEvent == null;
            if (couldNotReadLoggingEvent) {
                // TODO why does this happen
                // 1. parallel reading/writing (maybe then the event would be picked up by the next run
                // 2. broken file because app crashed during write
                appender.addWarn("Deserialization for logging event at " + file.getAbsolutePath() + " failed, deleting file.");
                file.delete();
                continue;
            }

            appender.superAppend(loggingEvent);

            if (appender.wasAppendSuccessful()) {
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
            appender.addError("Could not find logging event on disk.", e);
        } catch (final ClassNotFoundException e) {
            appender.addError("Could not de-serialize logging event from disk.", e);
        } catch (final IOException e) {
            appender.addError("Could not load logging event from disk.", e);
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

    private List<File> getAllFilesOrderedByDate() {
        final File logFolder = new File(appender.getLogFolder());
        final File[] files = logFolder.listFiles(new FileFilter() {
            @Override
            public boolean accept(final File file) {
                return file.isFile() && file.getName().endsWith(appender.getFileEnding());
            }
        });

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

        return ordered;
    }
}
