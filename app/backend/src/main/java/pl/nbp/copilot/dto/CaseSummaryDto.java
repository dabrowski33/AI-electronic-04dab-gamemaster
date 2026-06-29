package pl.nbp.copilot.dto;

import pl.nbp.copilot.model.CaseType;
import pl.nbp.copilot.model.EquipmentCategory;

import java.time.LocalDate;

public record CaseSummaryDto(
    CaseType type,
    EquipmentCategory category,
    String model,
    LocalDate purchaseDate
) {}
