package ch.qos.logback.classic.net;

import ch.qos.logback.core.spi.ContextAware;
import com.google.common.base.Strings;

import java.util.concurrent.TimeUnit;

/**
 * TODO unit test default values, validation and error reporting
 * TODO add documentation
 * Configuration for {@link FileBufferingSocketAppender}.
 *
 * @author Sebastian Gr&ouml;bler
 */
public class FileBufferingConfiguration {

  private static final String DEFAULT_FILE_ENDING = ".ser";
  private static final String DEFAULT_LOG_FOLDER = "/sdcard/logs/";
  private static final int DEFAULT_BATCH_SIZE = 50;
  private static final long DEFAULT_SEND_INTERVAL = TimeUnit.MINUTES.toMillis(1);
  private static final int DEFAULT_FILE_COUNT_QUOTA = 500;

  private String logFolder = DEFAULT_LOG_FOLDER;
  private String fileEnding = DEFAULT_FILE_ENDING;
  private int batchSize = DEFAULT_BATCH_SIZE;
  private long readInterval = DEFAULT_SEND_INTERVAL;
  private int fileCountQuota = DEFAULT_FILE_COUNT_QUOTA;

  public String getLogFolder() {
    return logFolder;
  }

  public void setLogFolder(final String logFolder) {
    this.logFolder = logFolder;
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

  public boolean isInvalid() {
    return isLogFolderInvalid() ||
            isFileEndingInvalid() ||
            isBatchSizeInvalid() ||
            isReadIntervalInvalid() ||
            isFileCountQuotaInvalid();
  }

  public void addErrors(final ContextAware contextAware) {

    if (isLogFolderInvalid()) {
      contextAware.addError("logFolder must not be null nor empty");
    }

    if (isFileEndingInvalid()) {
      contextAware.addError("fileEnding must not be null nor empty");
    }

    if (isBatchSizeInvalid()) {
      contextAware.addError("batchSize must be greater than zero");
    }

    if (isReadIntervalInvalid()) {
      contextAware.addError("readInterval must be greater than zero");
    }

    if (isFileCountQuotaInvalid()) {
      contextAware.addError("fileCountQuota must be greater than zero");
    }
  }

  public boolean isLogFolderInvalid() {
    return Strings.isNullOrEmpty(logFolder);
  }

  public boolean isFileEndingInvalid() {
    return Strings.isNullOrEmpty(fileEnding);
  }

  public boolean isBatchSizeInvalid() {
    return batchSize < 1;
  }

  public boolean isReadIntervalInvalid() {
    return readInterval < 1;
  }

  public boolean isFileCountQuotaInvalid() {
    return fileCountQuota < 1;
  }
}
