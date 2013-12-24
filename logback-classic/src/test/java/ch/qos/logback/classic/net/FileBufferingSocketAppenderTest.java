package ch.qos.logback.classic.net;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEventVO;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.status.StatusManager;
import com.google.common.base.Strings;
import org.fest.util.Files;
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

import java.io.*;
import java.util.Timer;
import java.util.TimerTask;

import static ch.qos.logback.classic.net.testObjectBuilders.LoggingEventFactory.newLoggingEvent;
import static ch.qos.logback.classic.net.testObjectBuilders.SerializedLogFileFactory.addFile;
import static ch.qos.logback.matchers.AnyTimes.anyTimes;
import static ch.qos.logback.matchers.StatusMatchers.hasNoItemWhichContainsMessage;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class FileBufferingSocketAppenderTest {

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  @Mock
  private SocketAppender socketAppender;

  @Mock
  private LogFileReader logFileReader;

  @Mock
  private FileBufferingConfiguration configuration;

  @Mock
  private ObjectIOProvider objectIoProvider;

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

  // TODO move these tests to the FileBufferingConfiguration
//  @Test
//  public void hasDefaultValueForLogFolder() {
//    assertThat(appender.getLogFolder(), is(notNullValue()));
//  }
//
//  @Test
//  public void hasDefaultValueForSendInterval() {
//    assertThat(appender.getReadInterval(), is(not(0L)));
//  }
//
//  @Test
//  public void hasDefaultValueForFileEnding() {
//    assertThat(appender.getFileEnding(), is(notNullValue()));
//  }
//
//  @Test
//  public void hasDefaultValueForBatchSize() {
//    assertThat(appender.getBatchSize(), is(not(0)));
//  }
//
//  @Test
//  public void hasDefaultValueForFileCountQuota() {
//    assertThat(appender.getFileCountQuota(), is(not(0)));
//  }

  @Test
  public void logsErrorOnIOException() throws IOException {

    // given
    final ILoggingEvent event = mock(ILoggingEvent.class);
    when(objectIoProvider.newObjectOutput(anyString())).thenThrow(IOException.class);

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

    verify(timer).schedule(any(TimerTask.class), eq(appender.getReadInterval()), eq(appender.getReadInterval()));
  }

  @Test
  public void stopsTimerOnStop() {
    appender.stop();

    verify(timer).cancel();
  }

  @Test
  public void doesNotAppendIfNotStarted() {
    appender.append(mock(ILoggingEvent.class));
    verifyZeroInteractions(objectIoProvider);
  }

  @Test
  public void serializesEventToConfiguredFolder() throws IOException {
    // given
    final String logFolder = folder.getRoot().getAbsolutePath() + "/foo/";
    appender.setLogFolder(logFolder);

    when(objectIoProvider.newObjectOutput(anyString())).then(createObjectOutput());

    // when
    appender.start();
    appender.append(newLoggingEvent());

    assertThat(new File(logFolder).list(), hasItemInArray(endsWith(appender.getFileEnding())));
  }

  @Test
  public void createsMissingDirsWithEveryLoggingEvent() throws IOException {
    // given
    appender.setLogFolder(folder.getRoot().getAbsolutePath() + "/foo");

    when(objectIoProvider.newObjectOutput(anyString())).then(createObjectOutput());

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

    when(objectIoProvider.newObjectOutput(anyString())).then(createObjectOutput());

    // when
    appender.start();
    appender.append(newLoggingEvent());

    verify(objectOutput).close();
  }

  @Test
  public void closesStreamsOnUnsuccessfulFileWrite() throws IOException {
    // given
    appender.setLogFolder(folder.getRoot().getAbsolutePath() + "/foo/");

    when(objectIoProvider.newObjectOutput(anyString())).then(createExceptionThrowingObjectOutput());

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

    when(objectIoProvider.newObjectOutput(anyString())).then(createObjectOutput());

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

    when(objectIoProvider.newObjectOutput(anyString())).then(createObjectOutput());

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
    when(objectIoProvider.newObjectOutput(anyString())).thenReturn(objectOutput);

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
  public void doesNotLoadCallerDataWhenConfigured() throws IOException {

    // given
    appender.setIncludeCallerData(false);
    objectOutput = mock(ObjectOutput.class);
    when(objectIoProvider.newObjectOutput(anyString())).thenReturn(objectOutput);

    // when
    appender.start();
    appender.append(newLoggingEvent());

    // then
    final ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(objectOutput).writeObject(captor.capture());

    final LoggingEventVO loggingEventVO = (LoggingEventVO) captor.getValue();

    assertThat(loggingEventVO.hasCallerData(), is(false));
  }

  @Test
  public void givesFilesSpecialTemporaryFileEndingWhileWritingToAvoidSimultaneousReadAndWrite() throws IOException {

    // given
    final FileBufferingSocketAppender appender = new FileBufferingSocketAppender();
    final Context context = mock(Context.class);
    final StatusManager statusManager = mock(StatusManager.class);
    when(context.getStatusManager()).thenReturn(statusManager);

    final String logFolder = folder.getRoot().getAbsolutePath() + "/foo/";
    appender.setLogFolder(logFolder);
    appender.setPort(6000);
    appender.setRemoteHost("localhost");
    appender.setContext(context);
    appender.setLazy(true);
    appender.setReadInterval(10);

    // when
    appender.start();
    appender.append(newLoggingEvent().withMessage(Strings.repeat("some random string", 1000000)));
    appender.stop();

    // then
    final ArgumentCaptor<Status> captor = ArgumentCaptor.forClass(Status.class);
    verify(statusManager, anyTimes()).add(captor.capture());

    assertThat(captor.getAllValues(), hasNoItemWhichContainsMessage("Could not load logging event from disk."));
  }

  @Test
  public void removesOldTempFilesOnStart() throws IOException {
    // given
    final String logFolderPath = folder.getRoot().getAbsolutePath() + "/foo/";
    final File logFolder = new File(logFolderPath);
    logFolder.mkdirs();
    appender.setLogFolder(logFolderPath);

    addFile(logFolder.getAbsolutePath() + "/foo.ser-tmp", DateTime.now());
    addFile(logFolder.getAbsolutePath() + "/bar.ser-tmp", DateTime.now());

    // when
    appender.start();

    // then
    assertThat(logFolder.list(), not(hasItemInArray("foo.ser-tmp")));
    assertThat(logFolder.list(), not(hasItemInArray("bar.ser-tmp")));
  }

  @Test
  public void removesOnlyOldTempFilesButNotDirectoriesOnStart() throws IOException {
    // given
    final String logFolderPath = folder.getRoot().getAbsolutePath() + "/foo/";
    final File logFolder = new File(logFolderPath);
    logFolder.mkdirs();
    appender.setLogFolder(logFolderPath);

    final File tmpFolder = new File(logFolderPath + "/some-tmp");
    tmpFolder.mkdirs();

    // when
    appender.start();

    // then
    assertThat(logFolder.list(), hasItemInArray("some-tmp"));
  }

  @Test
  public void removalOfOldTempFilesOnStartCanHandleNonExistingTempFolder() {
    // given
    appender.setLogFolder(folder.getRoot().getAbsolutePath() + "/foo/");

    // when
    appender.start();
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
