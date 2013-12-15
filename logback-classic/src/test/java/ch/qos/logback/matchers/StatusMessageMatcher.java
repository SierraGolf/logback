package ch.qos.logback.matchers;

import ch.qos.logback.core.status.Status;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.internal.matchers.TypeSafeMatcher;

public class StatusMessageMatcher extends TypeSafeMatcher<Status> {

    private Matcher<String> matcher;

    public StatusMessageMatcher(final Matcher<String> matcher) {
        this.matcher = matcher;
    }

    @Override
    public boolean matchesSafely(final Status status) {
        return matcher.matches(status.getMessage());
    }

    @Override
    public void describeTo(final Description description) {
        matcher.describeTo(description);
    }
}
