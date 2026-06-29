package pl.nbp.copilot.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.nbp.copilot.model.CaseType;
import pl.nbp.copilot.model.EquipmentCategory;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SubmitCaseRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private SubmitCaseRequest validComplaint() {
        var req = new SubmitCaseRequest();
        req.setType(CaseType.REKLAMACJA);
        req.setCategory(EquipmentCategory.LAPTOPY_I_KOMPUTERY);
        req.setModel("MacBook Pro 14");
        req.setPurchaseDate(LocalDate.now().minusDays(30));
        req.setReason("Ekran się nie włącza");
        return req;
    }

    @Test
    void validComplaintHasNoViolations() {
        Set<ConstraintViolation<SubmitCaseRequest>> violations = validator.validate(validComplaint());
        assertThat(violations).isEmpty();
    }

    @Test
    void missingReasonForReklamacjaIsViolation() {
        var req = validComplaint();
        req.setReason(null);
        var violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("reason"));
    }

    @Test
    void blankModelIsViolation() {
        var req = validComplaint();
        req.setModel("   ");
        var violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("model"));
    }

    @Test
    void futurePurchaseDateIsViolation() {
        var req = validComplaint();
        req.setPurchaseDate(LocalDate.now().plusDays(1));
        var violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("purchaseDate"));
    }

    @Test
    void todayPurchaseDatePasses() {
        var req = validComplaint();
        req.setPurchaseDate(LocalDate.now());
        var violations = validator.validate(req);
        assertThat(violations).isEmpty();
    }

    @Test
    void reasonPresentForZwrotPasses() {
        var req = validComplaint();
        req.setType(CaseType.ZWROT);
        req.setReason("Chcę zwrócić");
        var violations = validator.validate(req);
        assertThat(violations).isEmpty();
    }

    @Test
    void noReasonForZwrotPasses() {
        var req = validComplaint();
        req.setType(CaseType.ZWROT);
        req.setReason(null);
        var violations = validator.validate(req);
        assertThat(violations).isEmpty();
    }
}
