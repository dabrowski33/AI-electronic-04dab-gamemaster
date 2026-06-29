package pl.nbp.copilot.web;

import jakarta.validation.Valid;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.nbp.copilot.application.ChatService;
import pl.nbp.copilot.application.SessionNotFoundException;
import pl.nbp.copilot.dto.ChatMessageRequest;
import pl.nbp.copilot.session.SessionStore;

@RestController
@RequestMapping("/api/v1/cases")
public class ChatController {

    private final ChatService chatService;
    private final SessionStore sessionStore;
    private final AsyncTaskExecutor sseTaskExecutor;

    public ChatController(ChatService chatService, SessionStore sessionStore,
                          AsyncTaskExecutor sseTaskExecutor) {
        this.chatService = chatService;
        this.sessionStore = sessionStore;
        this.sseTaskExecutor = sseTaskExecutor;
    }

    @PostMapping(value = "/{sessionId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@PathVariable String sessionId,
                           @RequestBody @Valid ChatMessageRequest request) {
        if (!sessionStore.exists(sessionId)) {
            throw new SessionNotFoundException(sessionId);
        }
        var emitter = new SseEmitter(60_000L);
        sseTaskExecutor.execute(() -> chatService.streamChat(sessionId, request.getMessage(), emitter));
        return emitter;
    }
}
