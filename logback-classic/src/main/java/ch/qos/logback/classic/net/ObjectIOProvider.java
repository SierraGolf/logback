package ch.qos.logback.classic.net;

import java.io.*;

/**
 * Provides inputs and outputs for objects to and from files.
 *
 * @author Sebastian Gr&ouml;bler
 */
public class ObjectIOProvider {

  /**
   * Creates a new {@link ObjectOutput} for the given {@code fileName}.
   *
   * @param fileName the name of the file for which the output should be created
   * @return a new instance of {@link ObjectOutput}
   * @throws IOException when an exception occurred during the creation of the stream
   */
  public ObjectOutput newObjectOutput(final String fileName) throws IOException {
    return new ObjectOutputStream(new FileOutputStream(fileName));
  }

  /**
   * Creates a new {@link ObjectInput} for the given {@code file}.
   *
   * @param file the file for which the input should be cerated
   * @return a new instance of {@link ObjectInput}
   * @throws IOException when an exception occurred during the creation of the stream
   */
  public ObjectInput newObjectInput(final File file) throws IOException {
    return new ObjectInputStream(new FileInputStream(file));
  }
}
