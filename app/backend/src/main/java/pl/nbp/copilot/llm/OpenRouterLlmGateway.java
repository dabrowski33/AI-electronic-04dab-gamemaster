package pl.nbp.copilot.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openai.client.OpenAIClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionContentPartImage;
import com.openai.models.chat.completions.ChatCompletionContentPartText;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.nbp.copilot.application.LlmUnavailableException;
import pl.nbp.copilot.config.LlmProperties;
import pl.nbp.copilot.model.CaseSession;
import pl.nbp.copilot.model.CaseType;
import pl.nbp.copilot.model.ChatMessage;
import pl.nbp.copilot.model.Confidence;
import pl.nbp.copilot.model.DecisionCategory;
import pl.nbp.copilot.model.DecisionResult;
import pl.nbp.copilot.model.ImageAnalysis;
import pl.nbp.copilot.model.LikelyCause;
import pl.nbp.copilot.model.TriState;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
@Profile("!stub-llm")
public class OpenRouterLlmGateway implements LlmGateway {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterLlmGateway.class);

    private final OpenAIClient client;
    private final LlmProperties props;
    private final PromptCatalog prompts;
    private final ObjectMapper objectMapper;

    public OpenRouterLlmGateway(OpenAIClient client, LlmProperties props,
                                 PromptCatalog prompts, ObjectMapper objectMapper) {
        this.client = client;
        this.props = props;
        this.prompts = prompts;
        this.objectMapper = objectMapper;
    }

    @Override
    public ImageAnalysis analyzeImage(CaseType scenario, byte[] imageBytes) {
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        String dataUrl = "data:image/jpeg;base64," + base64;

        ChatCompletionContentPartImage imageParam = ChatCompletionContentPartImage.builder()
                .imageUrl(ChatCompletionContentPartImage.ImageUrl.builder().url(dataUrl).build())
                .build();
        ChatCompletionContentPart imagePart = ChatCompletionContentPart.ofImageUrl(imageParam);

        ChatCompletionContentPartText textParam = ChatCompletionContentPartText.builder()
                .text(prompts.imageAnalysisPrompt(scenario))
                .build();
        ChatCompletionContentPart textPart = ChatCompletionContentPart.ofText(textParam);

        ChatCompletionUserMessageParam userMsg = ChatCompletionUserMessageParam.builder()
                .contentOfArrayOfContentParts(List.of(textPart, imagePart))
                .build();

        ChatCompletionCreateParams request = ChatCompletionCreateParams.builder()
                .model(props.visionModel())
                .addMessage(userMsg)
                .responseFormat(ResponseFormatJsonObject.builder().build())
                .build();

        String json;
        try {
            json = client.chat().completions().create(request)
                    .choices().get(0).message().content().orElseThrow(
                            () -> new LlmUnavailableException("Empty response from vision model"));
        } catch (LlmUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Vision model call failed: {}", e.getMessage());
            throw new LlmUnavailableException("Vision model call failed: " + e.getMessage());
        }

        log.debug("Vision model raw JSON: {}", json);
        return parseImageAnalysis(json);
    }

    @Override
    public DecisionResult decide(CaseType scenario, CaseSession session, String policyText) {
        ImageAnalysis analysis = session.getImageAnalysis();
        String prompt = prompts.decisionPrompt(scenario, session, analysis, policyText);

        ChatCompletionUserMessageParam userMsg = ChatCompletionUserMessageParam.builder()
                .content(prompt)
                .build();

        ChatCompletionCreateParams request = ChatCompletionCreateParams.builder()
                .model(props.textModel())
                .addMessage(userMsg)
                .responseFormat(ResponseFormatJsonObject.builder().build())
                .build();

        String json;
        try {
            json = client.chat().completions().create(request)
                    .choices().get(0).message().content().orElseThrow(
                            () -> new LlmUnavailableException("Empty response from text model"));
        } catch (LlmUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Text model call failed: {}", e.getMessage());
            throw new LlmUnavailableException("Text model call failed: " + e.getMessage());
        }

        log.debug("Decision model raw JSON: {}", json);
        return parseDecisionResult(json);
    }

    @Override
    public void streamChat(CaseSession session, String userMessage, SseEmitter emitter) {
        ChatCompletionCreateParams request = buildChatRequest(session);

        try (StreamResponse<ChatCompletionChunk> stream = client.chat().completions().createStreaming(request)) {
            stream.stream().forEach(chunk -> {
                String delta = chunk.choices().stream()
                        .findFirst()
                        .flatMap(c -> c.delta().content())
                        .orElse("");
                if (!delta.isEmpty()) {
                    try {
                        // JSON-encode each token so leading/trailing spaces, newlines and unicode
                        // survive the SSE transport intact (raw `data:` framing would otherwise
                        // strip token spacing — see sse-parser.ts).
                        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(delta)));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            });
            emitter.send(SseEmitter.event().name("done").data(""));
            emitter.complete();
        } catch (UncheckedIOException e) {
            // Client disconnected
            emitter.completeWithError(e);
        } catch (Exception e) {
            try {
                emitter.send(SseEmitter.event().name("error").data("LLM_UNAVAILABLE"));
            } catch (IOException ignored) {
            }
            emitter.completeWithError(e);
        }
    }

    /**
     * Builds the full chat completion request from session history.
     * ChatService appends the user message to the session before calling streamChat,
     * so the session already contains the latest user message — no need to add it again.
     */
    private ChatCompletionCreateParams buildChatRequest(CaseSession session) {
        ChatCompletionSystemMessageParam systemMsg = ChatCompletionSystemMessageParam.builder()
                .content(prompts.chatSystemPrompt(session))
                .build();

        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(props.textModel())
                .addMessage(systemMsg);

        for (ChatMessage msg : session.getMessages()) {
            if ("user".equals(msg.role())) {
                builder.addMessage(ChatCompletionUserMessageParam.builder()
                        .content(msg.content())
                        .build());
            } else {
                builder.addMessage(ChatCompletionAssistantMessageParam.builder()
                        .content(msg.content())
                        .build());
            }
        }

        return builder.build();
    }

    private ImageAnalysis parseImageAnalysis(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return new ImageAnalysis(
                    node.path("summary").asText(""),
                    readStringList(node.path("observations")),
                    parseEnum(Confidence.class, node.path("confidence").asText(), Confidence.LOW),
                    parseTriState(node.path("signsOfUse").asText()),
                    parseTriState(node.path("visibleDamage").asText()),
                    parseTriState(node.path("complete").asText()),
                    parseTriState(node.path("resellableAsNew").asText()),
                    node.path("damageType").isMissingNode() || node.path("damageType").isNull()
                            ? null : node.path("damageType").asText(),
                    parseEnum(LikelyCause.class, node.path("likelyCause").asText(), LikelyCause.INCONCLUSIVE)
            );
        } catch (Exception e) {
            throw new LlmUnavailableException("Failed to parse ImageAnalysis: " + e.getMessage());
        }
    }

    private DecisionResult parseDecisionResult(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            String catStr = node.path("category").asText();
            DecisionCategory category = DecisionCategory.fromString(catStr);
            return new DecisionResult(
                    category,
                    node.path("justification").asText(""),
                    node.path("nextSteps").asText(""),
                    readStringList(node.path("missingInfo"))
            );
        } catch (Exception e) {
            log.warn("Failed to parse DecisionResult, falling back to NEEDS_HUMAN_REVIEW: {}", e.getMessage());
            return new DecisionResult(DecisionCategory.NEEDS_HUMAN_REVIEW,
                    "Nie udało się przetworzyć odpowiedzi systemu.", "", List.of());
        }
    }

    private List<String> readStringList(JsonNode node) {
        var list = new ArrayList<String>();
        if (node != null && node.isArray()) {
            node.forEach(el -> list.add(el.asText()));
        }
        return list;
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, E defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    private TriState parseTriState(String value) {
        return parseEnum(TriState.class, value, TriState.UNCERTAIN);
    }
}
