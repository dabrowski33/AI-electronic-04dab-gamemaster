package pl.nbp.copilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import pl.nbp.copilot.model.CaseType;
import pl.nbp.copilot.model.EquipmentCategory;

import java.time.LocalDate;

@RequiredIfComplaint
public class SubmitCaseRequest {

    @NotNull
    private CaseType type;

    @NotNull
    private EquipmentCategory category;

    @NotBlank
    @Size(max = 120)
    private String model;

    @NotNull
    @PastOrPresent
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate purchaseDate;

    @Size(max = 2000)
    private String reason;

    // Getters and setters
    public CaseType getType() { return type; }
    public void setType(CaseType type) { this.type = type; }
    public EquipmentCategory getCategory() { return category; }
    public void setCategory(EquipmentCategory category) { this.category = category; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
