package pl.nbp.copilot.application;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.nbp.copilot.llm.LlmGateway;
import pl.nbp.copilot.model.ChatMessage;
import pl.nbp.copilot.session.SessionStore;

import java.io.IOException;
import java.time.Instant;

@Service
public class ChatService {

    private final LlmGateway llmGateway;
    private final SessionStore sessionStore;

    public ChatService(LlmGateway llmGateway, SessionStore sessionStore) {
        this.llmGateway = llmGateway;
        this.sessionStore = sessionStore;
    }

    public void streamChat(String sessionId, String userMessage, SseEmitter emitter) {
        var session = sessionStore.get(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));

        sessionStore.appendMessage(sessionId, new ChatMessage("user", userMessage, Instant.now()));

        try {
            llmGateway.streamChat(session, userMessage, emitter);
        } catch (Exception e) {
            try {
                emitter.send(SseEmitter.event().name("error").data("LLM_UNAVAILABLE"));
                emitter.completeWithError(e);
            } catch (IOException ex) {
                // ignore - connection already closed
            }
        }
    }
}
