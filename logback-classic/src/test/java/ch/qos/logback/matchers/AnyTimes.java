package ch.qos.logback.matchers;

import java.util.List;
import org.mockito.internal.invocation.Invocation;
import org.mockito.internal.invocation.InvocationMarker;
import org.mockito.internal.invocation.InvocationMatcher;
import org.mockito.internal.invocation.InvocationsFinder;
import org.mockito.internal.verification.api.VerificationData;
import org.mockito.verification.VerificationMode;

public class AnyTimes implements VerificationMode {

    private final InvocationMarker invocationMarker = new InvocationMarker();

    public static VerificationMode anyTimes() {
        return new AnyTimes();
    }

    private AnyTimes() {
    }

    @Override
    public void verify(VerificationData data) {
        final List<Invocation> invocations = data.getAllInvocations();
        final InvocationMatcher wanted = data.getWanted();
        final InvocationsFinder finder = new InvocationsFinder();
        final List<Invocation> found = finder.findInvocations(invocations, wanted);

        invocationMarker.markVerified(found, wanted);
    }
}
