package ch.qos.logback.classic.net;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import ch.qos.logback.classic.util.Closeables;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import static ch.qos.logback.classic.net.testObjectBuilders.LoggingEventFactory.newLoggingEvent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsArrayContaining.hasItemInArray;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

@RunWith(MockitoJUnitRunner.class)
public class LogFileReaderTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Mock
    private FileBufferingSocketAppender appender;

    @Mock
    private IOProvider ioProvider;

    @InjectMocks
    private LogFileReader logFileReader;

    private File logFolder;

    @Before
    public void beforeEachTest() throws IOException {
        final String logFolderPath = folder.getRoot().getAbsolutePath() + "/foo/";
        logFolder = new File(logFolderPath);
        logFolder.mkdirs();

        when(appender.getLogFolder()).thenReturn(logFolderPath);
        when(appender.getFileEnding()).thenReturn(".ser");
        when(appender.getFileCountQuota()).thenReturn(500);
        when(appender.getBatchSize()).thenReturn(50);
        when(appender.wasAppendSuccessful()).thenReturn(Boolean.TRUE);
    }

    @Test
    public void deletesOldestEventsWhichAreOverTheQuota() throws IOException {

        // given
        when(ioProvider.newObjectInput(any(File.class))).thenAnswer(newObjectInput());
        when(appender.getFileCountQuota()).thenReturn(3);
        when(appender.getBatchSize()).thenReturn(0);
        addFile("a.ser", DateTime.now().plusMinutes(1));
        addFile("b.ser", DateTime.now().plusMinutes(2));
        addFile("c.ser", DateTime.now().plusMinutes(3));
        addFile("d.ser", DateTime.now().plusMinutes(4));
        addFile("e.ser", DateTime.now().plusMinutes(5));

        // when
        logFileReader.run();

        // then
        assertThat(logFolder.list(), not(hasItemInArray("a.ser")));
        assertThat(logFolder.list(), not(hasItemInArray("b.ser")));
        assertThat(logFolder.list(), hasItemInArray("c.ser"));
        assertThat(logFolder.list(), hasItemInArray("d.ser"));
        assertThat(logFolder.list(), hasItemInArray("e.ser"));
    }

    @Test
    public void sendsOldestEventsFirst() throws IOException {
        // given
        when(ioProvider.newObjectInput(any(File.class))).thenAnswer(newObjectInput());
        addFile(newLoggingEvent().withMessage("a"), "a.ser", DateTime.now().plusMinutes(1));
        addFile(newLoggingEvent().withMessage("b"), "b.ser", DateTime.now().plusMinutes(2));
        addFile(newLoggingEvent().withMessage("c"), "c.ser", DateTime.now().plusMinutes(3));
        addFile(newLoggingEvent().withMessage("d"), "d.ser", DateTime.now().plusMinutes(4));
        addFile(newLoggingEvent().withMessage("e"), "e.ser", DateTime.now().plusMinutes(5));

        // when
        logFileReader.run();

        // then
        final ArgumentCaptor<ILoggingEvent> captor = ArgumentCaptor.forClass(ILoggingEvent.class);
        verify(appender, times(5)).superAppend(captor.capture());

        // TODO write custom matcher for event properties
        assertThat(captor.getAllValues().get(0).getMessage(), is("a"));
        assertThat(captor.getAllValues().get(1).getMessage(), is("b"));
        assertThat(captor.getAllValues().get(2).getMessage(), is("c"));
        assertThat(captor.getAllValues().get(3).getMessage(), is("d"));
        assertThat(captor.getAllValues().get(4).getMessage(), is("e"));
    }

    @Test
    public void onlySendsTheConfiguredBatchSize() throws IOException {
        // given
        when(ioProvider.newObjectInput(any(File.class))).thenAnswer(newObjectInput());
        when(appender.getBatchSize()).thenReturn(3);
        addFile(newLoggingEvent().withMessage("a"), "a.ser", DateTime.now().plusMinutes(1));
        addFile(newLoggingEvent().withMessage("b"), "b.ser", DateTime.now().plusMinutes(2));
        addFile(newLoggingEvent().withMessage("c"), "c.ser", DateTime.now().plusMinutes(3));
        addFile(newLoggingEvent().withMessage("d"), "d.ser", DateTime.now().plusMinutes(4));
        addFile(newLoggingEvent().withMessage("e"), "e.ser", DateTime.now().plusMinutes(5));

        // when
        logFileReader.run();

        // then
        final ArgumentCaptor<ILoggingEvent> captor = ArgumentCaptor.forClass(ILoggingEvent.class);
        verify(appender, times(3)).superAppend(captor.capture());

        // TODO write custom matcher for event properties
        assertThat(captor.getAllValues().get(0).getMessage(), is("a"));
        assertThat(captor.getAllValues().get(1).getMessage(), is("b"));
        assertThat(captor.getAllValues().get(2).getMessage(), is("c"));
    }

    @Test
    public void doesNotSendEventsWhenAppenderIsDisconnected() throws IOException {
        // given
        when(ioProvider.newObjectInput(any(File.class))).thenAnswer(newObjectInput());
        when(appender.isNotConnected()).thenReturn(Boolean.TRUE);
        addFile("a.ser", DateTime.now().plusMinutes(1));

        // when
        logFileReader.run();

        // then
        verify(appender, times(0)).superAppend(any(ILoggingEvent.class));
    }

    @Test
    public void deletesEventWhichCouldNotBeDeserialized() throws IOException, ClassNotFoundException {
        // given
        final ObjectInput objectInput = mock(ObjectInput.class);
        when(ioProvider.newObjectInput(any(File.class))).thenReturn(objectInput);
        when(objectInput.readObject()).thenThrow(IOException.class);

        addFile("a.ser", DateTime.now().plusMinutes(1));

        // when
        logFileReader.run();

        // then
        assertThat(logFolder.list(), arrayWithSize(0));
    }

    @Test
    public void onlyDeletesEventsWhenNoIOExceptionOccurredDuringTransmission() throws IOException {
        // given
        when(ioProvider.newObjectInput(any(File.class))).thenAnswer(newObjectInput());
        when(appender.wasAppendSuccessful()).thenReturn(Boolean.FALSE);
        addFile("a.ser", DateTime.now().plusMinutes(1));

        // when
        logFileReader.run();

        // then
        assertThat(logFolder.list(), hasItemInArray("a.ser"));
    }

    @Test
    public void logsWarningWhenEventCouldNotBeDeserialized() throws IOException, ClassNotFoundException {
        // given
        final ObjectInput objectInput = mock(ObjectInput.class);
        when(ioProvider.newObjectInput(any(File.class))).thenReturn(objectInput);
        when(objectInput.readObject()).thenThrow(IOException.class);

        addFile("a.ser", DateTime.now().plusMinutes(1));

        // when
        logFileReader.run();

        // then
        verify(appender).addWarn(matches("^Deserialization for logging event at (\\S+)/foo/a.ser failed, deleting file.$"));
    }

    @Test
    public void logsErrorWhenEventFileCouldNotBeFound() throws IOException, ClassNotFoundException {
        // given
        final ObjectInput objectInput = mock(ObjectInput.class);
        when(ioProvider.newObjectInput(any(File.class))).thenReturn(objectInput);
        when(objectInput.readObject()).thenThrow(FileNotFoundException.class);

        addFile("a.ser", DateTime.now().plusMinutes(1));

        // when
        logFileReader.run();

        // then
        verify(appender).addError(eq("Could not find logging event on disk."), any(FileNotFoundException.class));
    }

    @Test
    public void logsErrorWhenILoggingEventCouldNotBeFound() throws IOException, ClassNotFoundException {
        final ObjectInput objectInput = mock(ObjectInput.class);
        when(ioProvider.newObjectInput(any(File.class))).thenReturn(objectInput);
        when(objectInput.readObject()).thenThrow(ClassNotFoundException.class);

        addFile("a.ser", DateTime.now().plusMinutes(1));

        // when
        logFileReader.run();

        // then
        verify(appender).addError(eq("Could not de-serialize logging event from disk."), any(ClassNotFoundException.class));
    }

    @Test
    public void logsErrorsWhenDeserializationFailedDueToIOException() throws IOException, ClassNotFoundException {
        final ObjectInput objectInput = mock(ObjectInput.class);
        when(ioProvider.newObjectInput(any(File.class))).thenReturn(objectInput);
        when(objectInput.readObject()).thenThrow(IOException.class);

        addFile("a.ser", DateTime.now().plusMinutes(1));

        // when
        logFileReader.run();

        // then
        verify(appender).addError(eq("Could not load logging event from disk."), any(IOException.class));
    }

    @Test
    public void closesStreamOnSuccessFulFileRead() throws IOException {

        // given
        final ObjectInput objectInput = mock(ObjectInput.class);
        when(ioProvider.newObjectInput(any(File.class))).thenReturn(objectInput);

        addFile("a.ser", DateTime.now().plusMinutes(1));

        // when
        logFileReader.run();

        // then
        verify(objectInput).close();
    }

    @Test
    public void closesStreamOnUnsuccessfulFulFileRead() throws IOException, ClassNotFoundException {
        final ObjectInput objectInput = mock(ObjectInput.class);
        when(ioProvider.newObjectInput(any(File.class))).thenReturn(objectInput);
        when(objectInput.readObject()).thenThrow(IOException.class);

        addFile("a.ser", DateTime.now().plusMinutes(1));

        // when
        logFileReader.run();

        // then
        verify(objectInput).close();
    }

    @Test
    public void onlyReadsFilesWithTheConfiguredFileEnding() throws IOException {
        // given
        when(ioProvider.newObjectInput(any(File.class))).thenAnswer(newObjectInput());
        addFile("a.ser", DateTime.now().plusMinutes(1));
        addFile("foo.bar", DateTime.now().plusMinutes(2));

        // when
        logFileReader.run();

        // then
        assertThat(logFolder.list(), hasItemInArray("foo.bar"));
    }

    private Answer<?> newObjectInput() {
        return new Answer<Object>() {
            @Override
            public Object answer(final InvocationOnMock invocation) throws Throwable {
                final File file = (File) invocation.getArguments()[0];
                final ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream(file));
                return objectInputStream;
            }
        };
    }

    private void addFile(final String fileName, final DateTime lastModified) throws IOException {
        addFile(newLoggingEvent(), fileName, lastModified);
    }

    private void addFile(final ILoggingEvent loggingEvent, final String fileName, final DateTime lastModified) throws IOException {
        final String filePath = logFolder.getAbsolutePath() + "/" + fileName;

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
