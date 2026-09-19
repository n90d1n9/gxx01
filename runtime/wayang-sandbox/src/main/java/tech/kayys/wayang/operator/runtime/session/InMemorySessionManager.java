package tech.kayys.wayang.operator.runtime.session;

import tech.kayys.wayang.spi.session.SessionId;
import tech.kayys.wayang.spi.session.SessionInfo;
import tech.kayys.wayang.spi.session.SessionManager;
import tech.kayys.wayang.spi.session.SessionQuery;
import tech.kayys.wayang.spi.session.SessionState;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemorySessionManager implements SessionManager {

    private final ConcurrentMap<SessionId, SessionInfo> sessions = new ConcurrentHashMap<>();

    public void register(SessionInfo info) {
        Objects.requireNonNull(info, "info must not be null");
        sessions.put(info.sessionId(), info);
    }

    @Override
    public Optional<SessionInfo> find(SessionId sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public List<SessionInfo> list(SessionQuery query) {
        if (query == null) {
            return List.copyOf(sessions.values());
        }

        return sessions.values().stream()
                .filter(s -> query.tenantId() == null || Objects.equals(query.tenantId(), s.tenantId()))
                .filter(s -> query.userId() == null || Objects.equals(query.userId(), s.userId()))
                .filter(s -> query.agentId() == null || Objects.equals(query.agentId(), s.agentId()))
                .filter(s -> query.states() == null || query.states().isEmpty() || query.states().contains(s.state()))
                .filter(s -> query.createdAfter() == null || (s.createdAt() != null && s.createdAt().isAfter(query.createdAfter())))
                .filter(s -> query.createdBefore() == null || (s.createdAt() != null && s.createdAt().isBefore(query.createdBefore())))
                .limit(query.limit() > 0 ? query.limit() : 100)
                .toList();
    }

    @Override
    public void suspend(SessionId sessionId) throws Exception {
        SessionInfo current = requireSession(sessionId);
        if (current.state() != SessionState.ACTIVE && current.state() != SessionState.IDLE) {
            throw new IllegalStateException("Cannot suspend session in state: " + current.state());
        }
        SessionInfo updated = updateState(current, SessionState.SUSPENDED);
        sessions.put(sessionId, updated);
    }

    @Override
    public void resume(SessionId sessionId) throws Exception {
        SessionInfo current = requireSession(sessionId);
        if (current.state() != SessionState.SUSPENDED) {
            throw new IllegalStateException("Cannot resume session in state: " + current.state());
        }
        SessionInfo updated = updateState(current, SessionState.ACTIVE);
        sessions.put(sessionId, updated);
    }

    @Override
    public void close(SessionId sessionId) throws Exception {
        SessionInfo current = requireSession(sessionId);
        if (current.state() == SessionState.CLOSED) {
            return;
        }
        SessionInfo updated = updateState(current, SessionState.CLOSED);
        sessions.put(sessionId, updated);
    }

    private SessionInfo requireSession(SessionId sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
        SessionInfo info = sessions.get(sessionId);
        if (info == null) {
            throw new NoSuchElementException("Session not found: " + sessionId);
        }
        return info;
    }

    private SessionInfo updateState(SessionInfo current, SessionState newState) {
        return new SessionInfo(
                current.sessionId(),
                current.tenantId(),
                current.userId(),
                current.agentId(),
                newState,
                current.createdAt(),
                Instant.now(),
                current.expiresAt(),
                current.correlationId(),
                current.executionIds(),
                current.attributes()
        );
    }
}
