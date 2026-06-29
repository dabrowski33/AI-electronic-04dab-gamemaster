package pl.nbp.copilot.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CaseSession {

    private final String sessionId;
    private final CaseType type;
    private final EquipmentCategory category;
    private final String model;
    private final LocalDate purchaseDate;
    private final String reason;
    private ImageAnalysis imageAnalysis;
    private DecisionResult decision;
    private final List<ChatMessage> messages;
    private final Instant createdAt;

    public CaseSession(
            String sessionId,
            CaseType type,
            EquipmentCategory category,
            String model,
            LocalDate purchaseDate,
            String reason) {
        this.sessionId = sessionId;
        this.type = type;
        this.category = category;
        this.model = model;
        this.purchaseDate = purchaseDate;
        this.reason = reason;
        this.messages = new ArrayList<>();
        this.createdAt = Instant.now();
    }

    public String getSessionId() { return sessionId; }
    public CaseType getType() { return type; }
    public EquipmentCategory getCategory() { return category; }
    public String getModel() { return model; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public String getReason() { return reason; }
    public ImageAnalysis getImageAnalysis() { return imageAnalysis; }
    public void setImageAnalysis(ImageAnalysis imageAnalysis) { this.imageAnalysis = imageAnalysis; }
    public DecisionResult getDecision() { return decision; }
    public void setDecision(DecisionResult decision) { this.decision = decision; }
    public List<ChatMessage> getMessages() { return messages; }
    public Instant getCreatedAt() { return createdAt; }
}
