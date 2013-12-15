package ch.qos.logback.matchers;

import ch.qos.logback.core.status.Status;
import org.hamcrest.Matcher;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.internal.matchers.IsCollectionContaining.hasItem;
import static org.junit.matchers.JUnitMatchers.containsString;

/**
 * Matchers fpr working with {@link Status}.
 *
 * @author Sebastian Gröbler
 * @since 15.12.2013
 */
public class StatusMatchers {

    public static <T> Matcher<Iterable<Status>> hasNoItemWhichContainsMessage(final String message) {
        return not(hasItem(new StatusMessageMatcher(containsString(message))));
    }
}
