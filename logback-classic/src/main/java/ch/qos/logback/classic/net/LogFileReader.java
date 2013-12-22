package ch.qos.logback.classic.net;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.util.Closeables;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.primitives.Longs;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInput;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TimerTask;

public class LogFileReader extends TimerTask {

    private final FileBufferingSocketAppender appender;
    private final IOProvider ioProvider;

    public LogFileReader(final FileBufferingSocketAppender appender, final IOProvider ioProvider) {
        this.appender = appender;
        this.ioProvider = ioProvider;
    }

    @Override
    public void run() {

        final List<File> allFilesOrderedByDate = getAllFilesOrderedByDate();
        final List<File> filesToDelete;
        final List<File> filesToSend;

        final int size = allFilesOrderedByDate.size();
        final boolean quotaIsReached = allFilesOrderedByDate.size() > appender.getFileCountQuota();
        if (quotaIsReached) {
            final int lastToBeRemoved = allFilesOrderedByDate.size() - appender.getFileCountQuota();
            filesToDelete = Lists.newArrayList(allFilesOrderedByDate.subList(0, lastToBeRemoved));
            final int lastIndex = Math.min(lastToBeRemoved + appender.getBatchSize(), size);
            filesToSend = Lists.newArrayList(allFilesOrderedByDate.subList(lastToBeRemoved, lastIndex));
        } else {
            filesToDelete = Collections.emptyList();
            final int lastIndex = Math.min(appender.getBatchSize(), size);
            filesToSend = Lists.newArrayList(allFilesOrderedByDate.subList(0, lastIndex));
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
            final Optional<ILoggingEvent> loggingEvent = deserialize(file);

            final boolean couldNotReadLoggingEvent = !loggingEvent.isPresent();
            if (couldNotReadLoggingEvent) {
                appender.addWarn("Deserialization for logging event at " + file.getAbsolutePath() + " failed, deleting file.");
                file.delete();
                continue;
            }

            if (appender.isNotConnected()) {
                return;
            }

            final boolean sendSuccessful = appender.feedBackingAppend(loggingEvent.get());

            if (sendSuccessful) {
                file.delete();
            }
        }
    }

    private Optional<ILoggingEvent> deserialize(final File file) {

        ObjectInput objectInput = null;
        try {
            objectInput = ioProvider.newObjectInput(file);
            final ILoggingEvent loggingEvent = (ILoggingEvent) objectInput.readObject();
            return Optional.of(loggingEvent);
        } catch (final FileNotFoundException e) {
            appender.addError("Could not find logging event on disk.", e);
        } catch (final ClassNotFoundException e) {
            appender.addError("Could not de-serialize logging event from disk.", e);
        } catch (final IOException e) {
            appender.addError("Could not load logging event from disk.", e);
        } finally {
            Closeables.close(objectInput);
        }

        return Optional.absent();
    }

    private List<File> getAllFilesOrderedByDate() {
        final File logFolder = new File(appender.getLogFolder());
        final File[] files = logFolder.listFiles(new FileFilter() {
            @Override
            public boolean accept(final File file) {
                return file.isFile() && file.getName().endsWith(appender.getFileEnding());
            }
        });

        if (files == null) {
            return Collections.emptyList();
        }

        final List<File> ordered = Lists.newArrayList(files);

        Collections.sort(ordered, new Comparator<File>() {
            @Override
            public int compare(final File lhs, final File rhs) {

                final long lhsLastModified = lhs.lastModified();
                final long rhsLastModified = rhs.lastModified();

                return Longs.compare(lhsLastModified, rhsLastModified);
            }
        });

        return ordered;
    }
}
