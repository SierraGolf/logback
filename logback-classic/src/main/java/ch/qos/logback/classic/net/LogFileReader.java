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

/**
 * A {@link TimerTask} which works closely together with the {@link FileBufferingSocketAppender} and the
 * {@link SocketAppender}. It serves as a timed task which will take over the reading of serialized log events
 * and either send them or delete them according to the provided {@link FileBufferingConfiguration}.
 *
 * @author Sebastian Gr&ouml;bler
 */
public class LogFileReader extends TimerTask {

  private final FileBufferingConfiguration configuration;
  private final SocketAppender appender;
  private final ObjectIOProvider objectIoProvider;

  public LogFileReader(
          final FileBufferingConfiguration configuration,
          final SocketAppender socketAppender,
          final ObjectIOProvider objectIoProvider) {

    this.configuration = configuration;
    this.appender = socketAppender;
    this.objectIoProvider = objectIoProvider;
  }

  @Override
  public void run() {

    final List<File> allFilesOrderedByDate = getAllFilesOrderedByDate();
    final List<File> filesToDelete;
    final List<File> filesToSend;

    final int size = allFilesOrderedByDate.size();
    final boolean quotaIsReached = allFilesOrderedByDate.size() > configuration.getFileCountQuota();
    if (quotaIsReached) {
      final int lastToBeRemoved = allFilesOrderedByDate.size() - configuration.getFileCountQuota();
      filesToDelete = Lists.newArrayList(allFilesOrderedByDate.subList(0, lastToBeRemoved));
      final int lastIndex = Math.min(lastToBeRemoved + configuration.getBatchSize(), size);
      filesToSend = Lists.newArrayList(allFilesOrderedByDate.subList(lastToBeRemoved, lastIndex));
    } else {
      filesToDelete = Collections.emptyList();
      final int lastIndex = Math.min(configuration.getBatchSize(), size);
      filesToSend = Lists.newArrayList(allFilesOrderedByDate.subList(0, lastIndex));
    }

    delete(filesToDelete);
    send(filesToSend);
  }

  /**
   * Deletes the given {@code files}.
   *
   * @param files the files to delete
   */
  private void delete(final List<File> files) {
    for (final File file : files) {
      file.delete();
    }
  }

  /**
   * Tries to send the given {@code files} over the socket. Each successfully send operation
   * will automatically delete the serialized version of the event. In the unlikely event
   * that a file could not be read, the file is assumed to be broken and will also be deleted.
   *
   * @param files the files to be send
   */
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

  /**
   * De-serializes the given file in to a instance of {@link ILoggingEvent}.
   * The result is an optional, meaning that in case the de-serialization failed
   * and "absent" is returned.
   *
   * @param file the file to de-serialize
   * @return an {@link Optional} of a {@link File}
   */
  private Optional<ILoggingEvent> deserialize(final File file) {

    ObjectInput objectInput = null;
    try {
      objectInput = objectIoProvider.newObjectInput(file);
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

  /**
   * Retrieves all files which end with the specified file ending
   * located in the specified folder in chronological order.
   *
   * @return a list of files which matches the criteria, might be empty
   */
  private List<File> getAllFilesOrderedByDate() {
    final File logFolder = new File(configuration.getLogFolder());
    final File[] files = logFolder.listFiles(new FileFilter() {
      @Override
      public boolean accept(final File file) {
        return file.isFile() && file.getName().endsWith(configuration.getFileEnding());
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
