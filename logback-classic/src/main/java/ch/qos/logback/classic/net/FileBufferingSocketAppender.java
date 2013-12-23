package ch.qos.logback.classic.net;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import ch.qos.logback.classic.util.Closeables;
import com.google.common.io.Files;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.ObjectOutput;
import java.util.Timer;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Buffers log events to disk, so that they can be send over a socket
 * according to the specified settings. The default setting will
 * buffer events to the folder /sdcard/logs and try to send them in chronological order
 * in batches of 50 every minute, unless the file count quota of 500 is reached, in which
 * case the events which are the oldest will be dropped.
 *
 * @author Sebastian Gr&ouml;bler
 */
public class FileBufferingSocketAppender extends SocketAppender {

  private static final String DEFAULT_FILE_ENDING = ".ser";
  private static final String TMP_FILE_ENDING = "-tmp";
  private static final String DEFAULT_LOG_FOLDER = "/sdcard/logs/";
  private static final int DEFAULT_BATCH_SIZE = 50;
  private static final long DEFAULT_SEND_INTERVAL = TimeUnit.MINUTES.toMillis(1);
  private static final int DEFAULT_FILE_COUNT_QUOTA = 500;

  private String logFolder = DEFAULT_LOG_FOLDER;
  private String fileEnding = DEFAULT_FILE_ENDING;
  private int batchSize = DEFAULT_BATCH_SIZE;
  private long readInterval = DEFAULT_SEND_INTERVAL;
  private int fileCountQuota = DEFAULT_FILE_COUNT_QUOTA;

  private final Timer timer;
  private final ObjectIOProvider objectIoProvider;

  public FileBufferingSocketAppender() {
    this(new Timer("LogEventReader"), new ObjectIOProvider());
  }

  FileBufferingSocketAppender(final Timer timer, final ObjectIOProvider objectIoProvider) {
    this.timer = timer;
    this.objectIoProvider = objectIoProvider;
  }

  @Override
  public void start() {
    super.start();
    removeOldTempFiles();
    final LogFileReader logFileReader = new LogFileReader(this, objectIoProvider);
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

  /**
   * Removes old temporary files which might not got removed due to unexpected application shut down.
   */
  private void removeOldTempFiles() {
    final File logFolder = new File(this.logFolder);
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

  /**
   * Makes sure that the log folder actually exists.
   */
  private void createLogFolderIfAbsent() {
    final File folder = new File(getLogFolder());
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
    return getLogFolder() + UUID.randomUUID() + getFileEnding();
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
}
