package pl.nbp.copilot.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = RequiredIfComplaintValidator.class)
@Documented
public @interface RequiredIfComplaint {
    String message() default "Opis przyczyny jest wymagany dla reklamacji";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
