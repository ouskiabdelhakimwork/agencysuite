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
import java.util.Locale;
import java.util.Set;

/**
 * Code pays ISO 3166-1 alpha-2 en majuscules (ex. {@code MA}), verifie contre
 * {@link Locale#getISOCountries()}. Une valeur absente est acceptee (champ facultatif).
 */
@Documented
@Constraint(validatedBy = ValidCountryCode.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCountryCode {

    String message() default "code pays ISO 3166-1 alpha-2 inconnu";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidCountryCode, String> {

        private static final Set<String> CODES = Set.of(Locale.getISOCountries());

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || CODES.contains(value);
        }
    }
}
