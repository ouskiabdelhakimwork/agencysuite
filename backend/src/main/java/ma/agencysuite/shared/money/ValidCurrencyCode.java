package ma.agencysuite.shared.money;

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
 * Code devise ISO 4217 en majuscules (ex. {@code MAD}, {@code EUR}), connu de
 * {@link java.util.Currency} et ayant un nombre de decimales defini (regle de
 * {@link Money#currencyOf(String)}). Une valeur absente est acceptee (champ facultatif) ; sinon
 * 400 {@code validation-failed}.
 */
@Documented
@Constraint(validatedBy = ValidCurrencyCode.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCurrencyCode {

    String message() default "code devise ISO 4217 inconnu";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidCurrencyCode, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || Money.isKnownCurrency(value);
        }
    }
}
