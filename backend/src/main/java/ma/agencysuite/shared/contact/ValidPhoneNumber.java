package ma.agencysuite.shared.contact;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Numero de telephone facultatif mais, s'il est present, valide pour libphonenumber (region par
 * defaut MA). Un numero invalide donne 400 {@code validation-failed} sur le champ.
 */
@Documented
@Constraint(validatedBy = ValidPhoneNumber.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPhoneNumber {

    String message() default "numero de telephone invalide";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidPhoneNumber, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return PhoneNumbers.isValidOrBlank(value);
        }
    }
}
