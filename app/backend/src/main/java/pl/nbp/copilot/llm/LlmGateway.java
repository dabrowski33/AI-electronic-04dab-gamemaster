package pl.nbp.copilot.llm;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.nbp.copilot.model.CaseSession;
import pl.nbp.copilot.model.CaseType;
import pl.nbp.copilot.model.DecisionResult;
import pl.nbp.copilot.model.ImageAnalysis;

public interface LlmGateway {
    ImageAnalysis analyzeImage(CaseType scenario, byte[] imageBytes);
    DecisionResult decide(CaseType scenario, CaseSession session, String policyText);
    void streamChat(CaseSession session, String userMessage, SseEmitter emitter);
}
