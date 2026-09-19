package tech.kayys.wayang.operator.runtime.diagnostics;

import tech.kayys.wayang.spi.diagnostics.*;
import tech.kayys.wayang.spi.session.SessionInfo;
import tech.kayys.wayang.spi.session.SessionManager;
import tech.kayys.wayang.spi.session.SessionQuery;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SessionDiagnosticProvider implements DiagnosticProvider {

    public static final String ID = "session-diagnostic-provider";

    private final SessionManager sessionManager;

    public SessionDiagnosticProvider(SessionManager sessionManager) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager must not be null");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public DiagnosticComponentType componentType() {
        return DiagnosticComponentType.SESSION;
    }

    @Override
    public DiagnosticResult diagnose(DiagnosticContext context) throws Exception {
        List<SessionInfo> sessions = sessionManager.list(SessionQuery.all());

        return new DiagnosticResult(
                DiagnosticComponent.of(DiagnosticComponentType.SESSION, ID),
                DiagnosticStatus.HEALTHY,
                Instant.now(),
                "Session subsystem status: HEALTHY (" + sessions.size() + " sessions active)",
                List.of(),
                Map.of("totalSessions", sessions.size())
        );
    }
}
