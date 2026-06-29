package pl.nbp.copilot.web;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.nbp.copilot.application.CaseService;
import pl.nbp.copilot.application.SessionNotFoundException;
import pl.nbp.copilot.dto.*;
import pl.nbp.copilot.session.SessionStore;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/cases")
public class CaseController {

    private final CaseService caseService;
    private final SessionStore sessionStore;

    public CaseController(CaseService caseService, SessionStore sessionStore) {
        this.caseService = caseService;
        this.sessionStore = sessionStore;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubmitCaseResponse> submitCase(
            @Valid @ModelAttribute SubmitCaseRequest request,
            @RequestPart("image") MultipartFile image) throws IOException {
        var response = caseService.handleSubmit(request, image);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<CaseDetailResponse> getCase(@PathVariable String sessionId) {
        var session = sessionStore.get(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));

        var caseSummary = new CaseSummaryDto(
            session.getType(),
            session.getCategory(),
            session.getModel(),
            session.getPurchaseDate()
        );

        return ResponseEntity.ok(new CaseDetailResponse(caseSummary, session.getMessages()));
    }
}
