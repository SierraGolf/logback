package ch.qos.logback.classic.net;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.status.StatusManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Timer;
import java.util.TimerTask;
import org.fest.util.Files;
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
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class FileBufferingSocketAppenderTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Mock
    private IOProvider ioProvider;

    @Mock
    private Timer timer;

    @InjectMocks
    private FileBufferingSocketAppender appender;

    private final Context context = mock(Context.class);
    private final StatusManager statusManager = mock(StatusManager.class);
    private ObjectOutput objectOutput;

    @Before
    public void beforeEachTest() {
        appender.setPort(6000);
        appender.setRemoteHost("localhost");
        appender.setContext(context);
        appender.setLazy(true);
        when(context.getStatusManager()).thenReturn(statusManager);
    }

    // TODO does not make sense with before anymore
    @Test
    public void hasDefaultValueForLogFolder() {
        assertThat(appender.getLogFolder(), is(notNullValue()));
    }

    @Test
    public void hasDefaultValueForSendInterval() {
        assertThat(appender.getSendInterval(), is(not(0L)));
    }

    @Test
    public void hasDefaultValueForFileEnding() {
        assertThat(appender.getFileEnding(), is(notNullValue()));
    }

    @Test
    public void hasDefaultValueForBatchSize() {
        assertThat(appender.getBatchSize(), is(not(0)));
    }

    @Test
    public void hasDefaultValueForFileCountQuota() {
        assertThat(appender.getFileCountQuota(), is(not(0)));
    }

    @Test
    public void logsErrorOnIOException() throws IOException {

        // given
        final ILoggingEvent event = mock(ILoggingEvent.class);
        when(ioProvider.newObjectOutput(anyString())).thenThrow(IOException.class);

        // when
        appender.start();
        appender.append(event);

        // then
        final ArgumentCaptor<Status> captor = ArgumentCaptor.forClass(Status.class);
        verify(statusManager).add(captor.capture());

        assertThat(captor.getValue().getMessage(), is("Could not write logging event to disk."));
        assertThat(captor.getValue().getLevel(), is(Status.ERROR));
        assertThat(captor.getValue().getThrowable(), instanceOf(IOException.class));
    }

    @Test
    public void startsTimerWithConfiguredIntervalOnStart() {
        appender.start();

        verify(timer).schedule(any(TimerTask.class), eq(appender.getSendInterval()), eq(appender.getSendInterval()));
    }

    @Test
    public void stopsTimerOnStop() {
        appender.stop();

        verify(timer).cancel();
    }

    @Test
    public void doesNotAppendIfNotStarted() {
        appender.append(mock(ILoggingEvent.class));
        verifyZeroInteractions(ioProvider);
    }

    @Test
    public void serializesEventToConfiguredFolder() throws IOException {
        // given
        final String logFolder = folder.getRoot().getAbsolutePath() + "/foo/";
        appender.setLogFolder(logFolder);

        when(ioProvider.newObjectOutput(anyString())).then(createObjectOutput());

        // when
        appender.start();
        appender.append(newLoggingEvent());

        assertThat(new File(logFolder).list(), hasItemInArray(endsWith(appender.getFileEnding())));
    }

    @Test
    public void createsMissingDirsWithEveryLoggingEvent() throws IOException {
        // given
        appender.setLogFolder(folder.getRoot().getAbsolutePath() + "/foo");

        when(ioProvider.newObjectOutput(anyString())).then(createObjectOutput());

        // when
        appender.start();
        appender.append(newLoggingEvent());

        Files.delete(new File(appender.getLogFolder()));

        appender.append(newLoggingEvent());

        verifyZeroInteractions(statusManager);
    }

    @Test
    public void closesStreamsOnSuccessfulFileWrite() throws IOException {
        // given
        appender.setLogFolder(folder.getRoot().getAbsolutePath() + "/foo/");

        when(ioProvider.newObjectOutput(anyString())).then(createObjectOutput());

        // when
        appender.start();
        appender.append(newLoggingEvent());

        verify(objectOutput).close();
    }

    @Test
    public void closesStreamsOnUnsuccessfulFileWrite() throws IOException {
        // given
        appender.setLogFolder(folder.getRoot().getAbsolutePath() + "/foo/");

        when(ioProvider.newObjectOutput(anyString())).then(createExceptionThrowingObjectOutput());

        // when
        appender.start();
        appender.append(newLoggingEvent());

        verify(objectOutput).close();
    }

    @Test
    public void allowsFolderToHaveTrailingSlash() throws IOException {
        // given
        final String logFolder = folder.getRoot().getAbsolutePath() + "/foo/";
        appender.setLogFolder(logFolder);

        when(ioProvider.newObjectOutput(anyString())).then(createObjectOutput());

        // when
        appender.start();
        appender.append(newLoggingEvent());

        assertThat(new File(logFolder).list(), hasItemInArray(endsWith(appender.getFileEnding())));
    }

    @Test
    public void allowsFolderToHaveNoTrailingSlash() throws IOException {
        // given
        final String logFolder = folder.getRoot().getAbsolutePath() + "/foo";
        appender.setLogFolder(logFolder);

        when(ioProvider.newObjectOutput(anyString())).then(createObjectOutput());

        // when
        appender.start();
        appender.append(newLoggingEvent());

        assertThat(new File(logFolder).list(), hasItemInArray(endsWith(appender.getFileEnding())));
    }

    @Test
    public void loadsCallerDataWhenConfigured() throws IOException {

        // given
        appender.setIncludeCallerData(true);
        objectOutput = mock(ObjectOutput.class);
        when(ioProvider.newObjectOutput(anyString())).thenReturn(objectOutput);

        // when
        appender.start();
        appender.append(newLoggingEvent());

        // then
        final ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(objectOutput).writeObject(captor.capture());

        final LoggingEventVO loggingEventVO = (LoggingEventVO) captor.getValue();

        assertThat(loggingEventVO.hasCallerData(), is(true));
    }

    @Test
    public void doesNotCallerDataWhenConfigured() throws IOException {

        // given
        appender.setIncludeCallerData(false);
        objectOutput = mock(ObjectOutput.class);
        when(ioProvider.newObjectOutput(anyString())).thenReturn(objectOutput);

        // when
        appender.start();
        appender.append(newLoggingEvent());

        // then
        final ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(objectOutput).writeObject(captor.capture());

        final LoggingEventVO loggingEventVO = (LoggingEventVO) captor.getValue();

        assertThat(loggingEventVO.hasCallerData(), is(false));
    }

    private Answer<?> createExceptionThrowingObjectOutput() {
        return new Answer<Object>() {
            @Override
            public Object answer(final InvocationOnMock invocation) throws Throwable {
                final String fileName = (String) invocation.getArguments()[0];
                objectOutput = spy(new ObjectOutputStream(new FileOutputStream(fileName)));
                doThrow(IOException.class).when(objectOutput).writeObject(any(Object.class));
                return objectOutput;
            }
        };
    }

    private Answer<Object> createObjectOutput() {
        return new Answer<Object>() {
            @Override
            public Object answer(final InvocationOnMock invocation) throws Throwable {
                final String fileName = (String) invocation.getArguments()[0];
                objectOutput = spy(new ObjectOutputStream(new FileOutputStream(fileName)));
                return objectOutput;
            }
        };
    }
}
