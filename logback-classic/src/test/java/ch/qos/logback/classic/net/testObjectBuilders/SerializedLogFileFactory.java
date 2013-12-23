package ch.qos.logback.classic.net.testObjectBuilders;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import ch.qos.logback.classic.util.Closeables;
import org.joda.time.DateTime;

import java.io.*;

import static ch.qos.logback.classic.net.testObjectBuilders.LoggingEventFactory.newLoggingEvent;

public class SerializedLogFileFactory {

  public static void addFile(final String filePath, final DateTime lastModified) throws IOException {
    addFile(newLoggingEvent(), filePath, lastModified);
  }

  public static void addFile(final ILoggingEvent loggingEvent, final String filePath, final DateTime lastModified) throws IOException {

    ObjectOutput objectOutput = null;
    try {
      objectOutput = new ObjectOutputStream(new FileOutputStream(filePath));
      objectOutput.writeObject(LoggingEventVO.build(loggingEvent));
    } finally {
      Closeables.close(objectOutput);
    }

    final File file = new File(filePath);
    file.setLastModified(lastModified.getMillis());
  }
}
