package pl.nbp.copilot.dto;

import pl.nbp.copilot.model.ChatMessage;

import java.util.List;

public record CaseDetailResponse(CaseSummaryDto caseSummary, List<ChatMessage> transcript) {}
