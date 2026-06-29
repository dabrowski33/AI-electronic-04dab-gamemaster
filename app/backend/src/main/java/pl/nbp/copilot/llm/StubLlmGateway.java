package pl.nbp.copilot.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.nbp.copilot.model.*;

import java.io.IOException;
import java.util.List;

@Component
@Profile({"stub-llm", "test"})
public class StubLlmGateway implements LlmGateway {

    private final ObjectMapper objectMapper;

    public StubLlmGateway(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ImageAnalysis analyzeImage(CaseType scenario, byte[] imageBytes) {
        return new ImageAnalysis(
            "Zdjęcie przedstawia urządzenie elektroniczne w dobrym stanie",
            List.of("Brak widocznych uszkodzeń", "Kompletne akcesoria"),
            Confidence.HIGH,
            TriState.NO,
            TriState.NO,
            TriState.YES,
            TriState.YES,
            null,
            LikelyCause.INCONCLUSIVE
        );
    }

    @Override
    public DecisionResult decide(CaseType scenario, CaseSession session, String policyText) {
        String modelName = session.getModel();
        DecisionCategory category;
        if (modelName != null && modelName.startsWith("NOT_ELIGIBLE:")) {
            category = DecisionCategory.NOT_ELIGIBLE;
        } else if (modelName != null && modelName.startsWith("NEEDS_HUMAN_REVIEW:")) {
            category = DecisionCategory.NEEDS_HUMAN_REVIEW;
        } else if (modelName != null && modelName.startsWith("MORE_INFO_REQUIRED:")) {
            category = DecisionCategory.MORE_INFO_REQUIRED;
        } else {
            category = DecisionCategory.ELIGIBLE;
        }

        return new DecisionResult(
            category,
            "Na podstawie analizy zdjęcia i informacji z formularza, sprawa kwalifikuje się do rozpatrzenia.",
            "Prosimy o dostarczenie sprzętu do najbliższego punktu serwisowego.",
            category == DecisionCategory.MORE_INFO_REQUIRED
                ? List.of("Proszę przesłać lepsze zdjęcie uszkodzenia")
                : List.of()
        );
    }

    @Override
    public void streamChat(CaseSession session, String userMessage, SseEmitter emitter) {
        try {
            String[] tokens = {"Dziękujemy", " za", " pytanie", "."};
            for (String token : tokens) {
                // JSON-encode each token (mirrors OpenRouterLlmGateway) so spacing survives SSE.
                emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(token)));
            }
            emitter.send(SseEmitter.event().name("done").data(""));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }
}
