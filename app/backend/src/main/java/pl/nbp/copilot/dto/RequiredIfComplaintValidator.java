package pl.nbp.copilot.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import pl.nbp.copilot.model.CaseType;

public class RequiredIfComplaintValidator
        implements ConstraintValidator<RequiredIfComplaint, SubmitCaseRequest> {

    @Override
    public boolean isValid(SubmitCaseRequest req, ConstraintValidatorContext ctx) {
        if (req == null) return true;
        if (req.getType() == CaseType.REKLAMACJA
                && (req.getReason() == null || req.getReason().isBlank())) {
            ctx.disableDefaultConstraintViolation();
            ctx.buildConstraintViolationWithTemplate("Opis przyczyny jest wymagany dla reklamacji")
               .addPropertyNode("reason")
               .addConstraintViolation();
            return false;
        }
        return true;
    }
}
