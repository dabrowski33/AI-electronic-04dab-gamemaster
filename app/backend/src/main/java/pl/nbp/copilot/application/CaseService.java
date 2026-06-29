package pl.nbp.copilot.application;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import pl.nbp.copilot.dto.CaseSummaryDto;
import pl.nbp.copilot.dto.DecisionDto;
import pl.nbp.copilot.dto.SubmitCaseRequest;
import pl.nbp.copilot.dto.SubmitCaseResponse;
import pl.nbp.copilot.image.ImageCompressor;
import pl.nbp.copilot.llm.LlmGateway;
import pl.nbp.copilot.model.*;
import pl.nbp.copilot.policy.PolicyProvider;
import pl.nbp.copilot.session.SessionStore;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class CaseService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "image/jpeg", "image/png", "image/webp"
    );
    private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024; // 10MB
    private static final int MAX_LLM_ATTEMPTS = 3;

    private final LlmGateway llmGateway;
    private final ImageCompressor imageCompressor;
    private final PolicyProvider policyProvider;
    private final MessageComposer messageComposer;
    private final SessionStore sessionStore;

    public CaseService(LlmGateway llmGateway, ImageCompressor imageCompressor,
                       PolicyProvider policyProvider, MessageComposer messageComposer,
                       SessionStore sessionStore) {
        this.llmGateway = llmGateway;
        this.imageCompressor = imageCompressor;
        this.policyProvider = policyProvider;
        this.messageComposer = messageComposer;
        this.sessionStore = sessionStore;
    }

    public SubmitCaseResponse handleSubmit(SubmitCaseRequest request, MultipartFile image) throws IOException {
        // Validate image content type
        String contentType = image.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new UnsupportedImageTypeException(contentType);
        }

        // Validate image size
        if (image.getSize() > MAX_IMAGE_SIZE) {
            throw new PayloadTooLargeException("Image exceeds 10 MB limit");
        }

        // Compress image
        byte[] compressedBytes = imageCompressor.compress(image.getBytes(), contentType);

        // Create session
        String sessionId = UUID.randomUUID().toString();
        var session = new CaseSession(
            sessionId,
            request.getType(),
            request.getCategory(),
            request.getModel(),
            request.getPurchaseDate(),
            request.getReason()
        );

        // Analyze image (with retry)
        ImageAnalysis imageAnalysis = withRetry(() -> llmGateway.analyzeImage(request.getType(), compressedBytes));
        session.setImageAnalysis(imageAnalysis);

        // Get policy text
        String policyText = policyProvider.getPolicy(request.getType());

        // Get decision (with retry)
        DecisionResult decisionResult = withRetry(() -> llmGateway.decide(request.getType(), session, policyText));
        session.setDecision(decisionResult);

        // Compose first message
        String firstMessage = messageComposer.compose(decisionResult);

        // Append first message to session
        session.getMessages().add(new ChatMessage("assistant", firstMessage, Instant.now()));

        // Persist session
        sessionStore.create(session);

        // Build response
        var decisionDto = new DecisionDto(
            decisionResult.category(),
            decisionResult.justification(),
            decisionResult.nextSteps(),
            decisionResult.missingInfo()
        );
        var caseSummaryDto = new CaseSummaryDto(
            request.getType(),
            request.getCategory(),
            request.getModel(),
            request.getPurchaseDate()
        );

        return new SubmitCaseResponse(sessionId, decisionDto, firstMessage, caseSummaryDto);
    }

    private <T> T withRetry(Supplier<T> call) {
        LlmUnavailableException lastException = null;
        for (int i = 0; i < MAX_LLM_ATTEMPTS; i++) {
            try {
                return call.get();
            } catch (LlmUnavailableException e) {
                lastException = e;
            }
        }
        throw lastException;
    }
}
