package ch.qos.logback.classic.util;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class Closeables {

    public static void close(final ObjectOutput closeable) {
        if (closeable == null) {
            return;
        }

        try {
            closeable.close();
        } catch (final IOException e) {
            // ignored
        }
    }

    public static void close(final ObjectInput closeable) {
        if (closeable == null) {
            return;
        }

        try {
            closeable.close();
        } catch (final IOException e) {
            // ignored
        }
    }
}
