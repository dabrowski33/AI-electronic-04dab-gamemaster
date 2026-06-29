package pl.nbp.copilot.session;

import pl.nbp.copilot.model.CaseSession;
import pl.nbp.copilot.model.ChatMessage;

import java.util.Optional;

public interface SessionStore {
    void create(CaseSession session);
    Optional<CaseSession> get(String sessionId);
    void appendMessage(String sessionId, ChatMessage message);
    boolean exists(String sessionId);
}
