package pl.nbp.copilot.session;

import org.springframework.stereotype.Component;
import pl.nbp.copilot.model.CaseSession;
import pl.nbp.copilot.model.ChatMessage;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemorySessionStore implements SessionStore {

    private final ConcurrentHashMap<String, CaseSession> store = new ConcurrentHashMap<>();

    @Override
    public void create(CaseSession session) {
        store.put(session.getSessionId(), session);
    }

    @Override
    public Optional<CaseSession> get(String sessionId) {
        return Optional.ofNullable(store.get(sessionId));
    }

    @Override
    public void appendMessage(String sessionId, ChatMessage message) {
        var session = store.get(sessionId);
        if (session != null) {
            synchronized (session.getMessages()) {
                session.getMessages().add(message);
            }
        }
    }

    @Override
    public boolean exists(String sessionId) {
        return store.containsKey(sessionId);
    }
}
