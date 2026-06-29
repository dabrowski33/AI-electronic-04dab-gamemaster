package pl.nbp.copilot.llm;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.nbp.copilot.application.LlmUnavailableException;
import pl.nbp.copilot.model.CaseSession;
import pl.nbp.copilot.model.CaseType;
import pl.nbp.copilot.model.Confidence;
import pl.nbp.copilot.model.DecisionCategory;
import pl.nbp.copilot.model.DecisionResult;
import pl.nbp.copilot.model.EquipmentCategory;
import pl.nbp.copilot.model.ImageAnalysis;
import pl.nbp.copilot.model.LikelyCause;
import pl.nbp.copilot.model.TriState;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WireMock integration test for OpenRouterLlmGateway.
 *
 * Uses "integration-test" profile to avoid activating StubLlmGateway
 * (which binds to "test" and "stub-llm" profiles). The real gateway
 * is wired with OpenAIClient pointing at the WireMock server.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integration-test")
class OpenRouterLlmGatewayIntegrationTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("app.llm.base-url", wm::baseUrl);
        registry.add("app.llm.api-key", () -> "test-key");
        registry.add("app.llm.vision-model", () -> "test-vision");
        registry.add("app.llm.text-model", () -> "test-text");
        registry.add("app.llm.app-url", () -> "http://localhost");
        registry.add("app.llm.app-title", () -> "Test Copilot");
    }

    @Autowired
    private OpenRouterLlmGateway gateway;

    private CaseSession zwrotSession;

    @BeforeEach
    void setUp() {
        zwrotSession = new CaseSession(
                "test-session-id",
                CaseType.ZWROT,
                EquipmentCategory.LAPTOPY_I_KOMPUTERY,
                "MacBook Pro",
                LocalDate.of(2024, 1, 15),
                null
        );
        zwrotSession.setImageAnalysis(new ImageAnalysis(
                "Test summary",
                List.of("No damage"),
                Confidence.HIGH,
                TriState.NO,
                TriState.NO,
                TriState.YES,
                TriState.YES,
                null,
                LikelyCause.INCONCLUSIVE
        ));
    }

    private void stubImageAnalysisSuccess() {
        wm.stubFor(post(urlPathEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                              "id": "chatcmpl-1",
                              "object": "chat.completion",
                              "choices": [{
                                "index": 0,
                                "message": {
                                  "role": "assistant",
                                  "content": "{\\"summary\\":\\"Telefon w dobrym stanie\\",\\"observations\\":[\\"Brak uszkodzeń\\"],\\"confidence\\":\\"HIGH\\",\\"signsOfUse\\":\\"NO\\",\\"visibleDamage\\":\\"NO\\",\\"complete\\":\\"YES\\",\\"resellableAsNew\\":\\"YES\\"}"
                                },
                                "finish_reason": "stop"
                              }]
                            }
                            """)));
    }

    private void stubDecisionSuccess(String category) {
        wm.stubFor(post(urlPathEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                              "id": "chatcmpl-2",
                              "object": "chat.completion",
                              "choices": [{
                                "index": 0,
                                "message": {
                                  "role": "assistant",
                                  "content": "{\\"category\\":\\"%s\\",\\"justification\\":\\"Spełnia warunki\\",\\"nextSteps\\":\\"Proszę dostarczyć sprzęt\\",\\"missingInfo\\":[]}"
                                },
                                "finish_reason": "stop"
                              }]
                            }
                            """.formatted(category))));
    }

    @Test
    void analyzeImage_zwrotScenario_parsesImageAnalysisCorrectly() {
        stubImageAnalysisSuccess();

        byte[] imageBytes = new byte[]{0x01, 0x02, 0x03};
        ImageAnalysis result = gateway.analyzeImage(CaseType.ZWROT, imageBytes);

        assertThat(result).isNotNull();
        assertThat(result.signsOfUse()).isEqualTo(TriState.NO);
        assertThat(result.visibleDamage()).isEqualTo(TriState.NO);
        assertThat(result.complete()).isEqualTo(TriState.YES);
        assertThat(result.confidence()).isEqualTo(Confidence.HIGH);
        assertThat(result.summary()).isEqualTo("Telefon w dobrym stanie");
    }

    @Test
    void analyzeImage_usesVisionModel() {
        stubImageAnalysisSuccess();

        gateway.analyzeImage(CaseType.ZWROT, new byte[]{0x01});

        wm.verify(postRequestedFor(urlPathEqualTo("/chat/completions"))
                .withRequestBody(containing("\"model\":\"test-vision\"")));
    }

    @Test
    void decide_usesTextModel() {
        stubDecisionSuccess("ELIGIBLE");

        gateway.decide(CaseType.ZWROT, zwrotSession, "Polityka zwrotów: 14 dni");

        wm.verify(postRequestedFor(urlPathEqualTo("/chat/completions"))
                .withRequestBody(containing("\"model\":\"test-text\"")));
    }

    @Test
    void decide_returnsEligibleWhenModelSaysEligible() {
        stubDecisionSuccess("ELIGIBLE");

        DecisionResult result = gateway.decide(CaseType.ZWROT, zwrotSession, "polityka");

        assertThat(result.category()).isEqualTo(DecisionCategory.ELIGIBLE);
        assertThat(result.justification()).isEqualTo("Spełnia warunki");
    }

    @Test
    void decide_unknownCategory_coercedToNeedsHumanReview() {
        wm.stubFor(post(urlPathEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                              "id": "chatcmpl-3",
                              "object": "chat.completion",
                              "choices": [{
                                "index": 0,
                                "message": {
                                  "role": "assistant",
                                  "content": "{\\"category\\":\\"UNKNOWN_VALUE\\",\\"justification\\":\\"test\\",\\"nextSteps\\":\\"test\\",\\"missingInfo\\":[]}"
                                },
                                "finish_reason": "stop"
                              }]
                            }
                            """)));

        DecisionResult result = gateway.decide(CaseType.ZWROT, zwrotSession, "test policy");

        assertThat(result.category()).isEqualTo(DecisionCategory.NEEDS_HUMAN_REVIEW);
    }

    @Test
    void decide_policyTextInjectedInRequest() {
        stubDecisionSuccess("ELIGIBLE");

        String policyText = "Zwrot możliwy w ciągu 14 dni od zakupu";
        gateway.decide(CaseType.ZWROT, zwrotSession, policyText);

        wm.verify(postRequestedFor(urlPathEqualTo("/chat/completions"))
                .withRequestBody(containing("14 dni")));
    }

    @Test
    void noResponsesEndpointCalled() {
        stubImageAnalysisSuccess();
        stubDecisionSuccess("ELIGIBLE");

        gateway.analyzeImage(CaseType.ZWROT, new byte[]{0x01});

        wm.verify(0, postRequestedFor(urlPathMatching(".*/responses.*")));
    }

    @Test
    void analyzeImage_on503_throwsLlmUnavailableException() {
        wm.stubFor(post(urlPathEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withBody("Service Unavailable")));

        assertThatThrownBy(() -> gateway.analyzeImage(CaseType.ZWROT, new byte[]{0x01}))
                .isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    void decide_on503_throwsLlmUnavailableException() {
        wm.stubFor(post(urlPathEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withBody("Service Unavailable")));

        assertThatThrownBy(() -> gateway.decide(CaseType.ZWROT, zwrotSession, "policy"))
                .isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    void streamChat_sendsRequestToTextModelEndpoint() throws Exception {
        String sseBody = "data: {\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"delta\":{\"content\":\"Hej\"},\"index\":0,\"finish_reason\":null}]}\n\n" +
                "data: {\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"delta\":{\"content\":\" tam\"},\"index\":0,\"finish_reason\":null}]}\n\n" +
                "data: [DONE]\n\n";

        wm.stubFor(post(urlPathMatching(".*/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(sseBody)));

        List<String> errors = new CopyOnWriteArrayList<>();
        SseEmitter emitter = new SseEmitter(5000L);
        emitter.onError(t -> errors.add(t.getMessage()));

        Thread thread = new Thread(() -> {
            try {
                gateway.streamChat(zwrotSession, "Pytanie testowe", emitter);
            } catch (Exception e) {
                errors.add(e.getMessage());
            }
        });
        thread.start();
        thread.join(10_000);

        wm.verify(postRequestedFor(urlPathMatching(".*/chat/completions"))
                .withRequestBody(containing("\"model\":\"test-text\"")));
    }

    @Test
    void streamChat_includesSessionHistoryInRequest() throws Exception {
        String sseBody = "data: [DONE]\n\n";

        wm.stubFor(post(urlPathMatching(".*/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(sseBody)));

        // Add a prior assistant message to the session
        zwrotSession.getMessages().add(new pl.nbp.copilot.model.ChatMessage(
                "assistant", "Dzień dobry, jak mogę pomóc?", java.time.Instant.now()));
        zwrotSession.getMessages().add(new pl.nbp.copilot.model.ChatMessage(
                "user", "Chcę zapytać o zwrot", java.time.Instant.now()));

        SseEmitter emitter = new SseEmitter(5000L);
        emitter.onError(t -> {});

        Thread thread = new Thread(() -> gateway.streamChat(zwrotSession, "Pytanie", emitter));
        thread.start();
        thread.join(10_000);

        // Verify system prompt includes session context
        wm.verify(postRequestedFor(urlPathMatching(".*/chat/completions"))
                .withRequestBody(containing("system")));
    }
}
