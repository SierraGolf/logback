package ch.qos.logback.classic.net;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import ch.qos.logback.classic.util.Closeables;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.Context;
import com.google.common.io.Files;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.ObjectOutput;
import java.util.Timer;
import java.util.UUID;

/**
 * TODO add documentation for setters
 * Buffers log events to disk, so that they can be send over a socket
 * according to the specified configuration. The default configuration will
 * buffer events to the folder /sdcard/logs and try to send them in chronological order
 * in batches of 50 every minute, unless the file count quota of 500 is reached, in which
 * case the events which are the oldest will be dropped.
 *
 * @author Sebastian Gr&ouml;bler
 */
public class FileBufferingSocketAppender extends AppenderBase<ILoggingEvent> {

  private static final String TMP_FILE_ENDING = "-tmp";

  private final FileBufferingConfiguration configuration;
  private final SocketAppender socketAppender;
  private final LogFileReader logFileReader;
  private final Timer timer;
  private final ObjectIOProvider objectIoProvider;

  public FileBufferingSocketAppender() {
    this(new FileBufferingConfiguration(), new SocketAppender(), new Timer("LogEventReader"), new ObjectIOProvider());
  }

  private FileBufferingSocketAppender(
          final FileBufferingConfiguration configuration,
          final SocketAppender socketAppender,
          final Timer timer,
          final ObjectIOProvider objectIoProvider) {

    this(configuration, socketAppender, new LogFileReader(configuration, socketAppender, objectIoProvider), timer, objectIoProvider);
  }

  FileBufferingSocketAppender(
          final FileBufferingConfiguration configuration,
          final SocketAppender socketAppender,
          final LogFileReader logFileReader,
          final Timer timer,
          final ObjectIOProvider objectIoProvider) {

    this.configuration = configuration;
    this.socketAppender = socketAppender;
    this.logFileReader = logFileReader;
    this.timer = timer;
    this.objectIoProvider = objectIoProvider;
  }

  @Override
  public void start() {

    if (configuration.isInvalid()) {
      configuration.addErrors(this);
    }

    super.start();
    socketAppender.start();

    removeOldTempFiles();

    timer.schedule(logFileReader, configuration.getReadInterval(), configuration.getReadInterval());
  }

  @Override
  protected void append(final ILoggingEvent event) {
    if (!isStarted()) {
      return;
    }

    createLogFolderIfAbsent();
    socketAppender.postProcessEvent(event);

    boolean savedSuccessfully = false;
    final String fileName = generateFilePath();
    final String tempName = fileName + TMP_FILE_ENDING;
    ObjectOutput objectOutput = null;
    try {
      objectOutput = objectIoProvider.newObjectOutput(tempName);
      objectOutput.writeObject(LoggingEventVO.build(event));
      savedSuccessfully = true;
    } catch (final IOException e) {
      addError("Could not write logging event to disk.", e);
    } finally {
      Closeables.close(objectOutput);
    }

    if (savedSuccessfully) {
      renameFileFromTempToFinalName(tempName, fileName);
    }
  }

  @Override
  public void setName(final String name) {
    super.setName(name);
    socketAppender.setName(name + "-BelongingSocketAppender");
  }

  @Override
  public void stop() {
    super.stop();
    socketAppender.stop();
    timer.cancel();
  }

  @Override
  public void setContext(final Context context) {
    super.setContext(context);
    socketAppender.setContext(context);
  }

  public void setIncludeCallerData(boolean includeCallerData) {
    socketAppender.setIncludeCallerData(includeCallerData);
  }

  public void setRemoteHost(final String host) {
    socketAppender.setRemoteHost(host);
  }

  public void setPort(final int port) {
    socketAppender.setPort(port);
  }

  public void setReconnectionDelay(final int delay) {
    socketAppender.setReconnectionDelay(delay);
  }

  public void setLazy(final boolean enable) {
    socketAppender.setLazy(enable);
  }

  public void setLogFolder(final String logFolder) {
    configuration.setLogFolder(format(logFolder));
  }

  public void setBatchSize(final int batchSize) {
    configuration.setBatchSize(batchSize);
  }

  public void setReadInterval(final long readInterval) {
    configuration.setReadInterval(readInterval);
  }

  public void setFileCountQuota(final int fileCountQuota) {
    configuration.setFileCountQuota(fileCountQuota);
  }

  /**
   * Removes old temporary files which might not got removed due to unexpected application shut down.
   */
  private void removeOldTempFiles() {
    final File logFolder = new File(configuration.getLogFolder());
    final File[] oldTempFiles = logFolder.listFiles(new FileFilter() {
      @Override
      public boolean accept(final File file) {
        return file.isFile() && file.getAbsolutePath().endsWith(TMP_FILE_ENDING);
      }
    });

    if (oldTempFiles == null) {
      return;
    }

    for (final File file : oldTempFiles) {
      file.delete();
    }
  }

  /**
   * Makes sure that the log folder actually exists.
   */
  private void createLogFolderIfAbsent() {
    final File folder = new File(configuration.getLogFolder());
    if (!folder.exists()) {
      folder.mkdirs();
    }
  }

  /**
   * Makes sure each serialized event has it's own unique (UUID-based) file name
   * and is located in the specified {@code logFolder} and ends with the specified
   * {@code fileEnding}.
   *
   * @return the generated file path
   */
  private String generateFilePath() {
    return configuration.getLogFolder() + UUID.randomUUID() + configuration.getFileEnding();
  }

  /**
   * Moves the file from it's temporary name to it's final name.
   *
   * @param tempName the temporary name
   * @param fileName the final name
   */
  private void renameFileFromTempToFinalName(final String tempName, final String fileName) {
    try {
      Files.move(new File(tempName), new File(fileName));
    } catch (final IOException e) {
      addError("Could not rename file from " + tempName + " to " + fileName);
    }
  }

  /**
   * Makes sure that the given {@code logFolder} always ends with a file separator.
   *
   * @param logFolder the log folder to check
   * @return a log folder path that always ends with a file separator
   */
  private String format(final String logFolder) {

    if (logFolder.endsWith(File.separator)) {
      return logFolder;
    }

    return logFolder + File.separator;
  }
}
