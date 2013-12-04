package ch.qos.logback.classic.net;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

public class IOProvider {

    public ObjectOutput newObjectOutput(final String fileName) throws IOException {
        return new ObjectOutputStream(new FileOutputStream(fileName));
    }

    public ObjectInput newObjectInput(File file) throws IOException {
        return new ObjectInputStream(new FileInputStream(file));
    }
}
