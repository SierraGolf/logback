package ch.qos.logback.matchers;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import static org.junit.matchers.JUnitMatchers.containsString;

/**
 * Matchers for working with {@link ch.qos.logback.classic.spi.ILoggingEvent}s.
 * 
 * @author sgroebler
 * @since 10.02.2013
 */
public final class LoggingEventMatchers {

	@Factory
	public static <T> Matcher<ILoggingEvent> containsMessage(final String message) {
		return new LoggingEventMessageMatcher(containsString(message));
	}

	@Factory
	public static <T> Matcher<ILoggingEvent> containsMessage(final Matcher<String> matcher) {
		return new LoggingEventMessageMatcher(matcher);
	}
}
