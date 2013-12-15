package ch.qos.logback.matchers;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.internal.matchers.TypeSafeMatcher;

/**
 * A matcher which expects the {@link ch.qos.logback.classic.spi.ILoggingEvent} to contain the specified {@code message}.
 * 
 * @author Sebastian Gröbler
 * @since 10.02.2013
 */
public class LoggingEventMessageMatcher extends TypeSafeMatcher<ILoggingEvent> {

	private Matcher<String> matcher;

	public LoggingEventMessageMatcher(final Matcher<String> matcher) {
		this.matcher = matcher;
	}

	@Override
	public boolean matchesSafely(final ILoggingEvent event) {
		return matcher.matches(event.toString());
	}

	@Override
	public void describeTo(final Description description) {
        matcher.describeTo(description);
	}
}
