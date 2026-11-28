package ma.agencysuite.shared.i18n;

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
 * Regles d'un {@link LocalizedText} saisi : cles {@code fr}, {@code ar}, {@code en} uniquement ;
 * au moins une langue si {@link #required()} (un champ absent compte comme vide) ; chaque valeur
 * d'au plus {@link #max()} caracteres apres nettoyage. Sinon 400 {@code validation-failed}.
 */
@Documented
@Constraint(validatedBy = ValidLocalizedText.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidLocalizedText {

    /** Vrai : au moins une langue est obligatoire. */
    boolean required() default false;

    /** Longueur maximale de chaque langue. */
    int max();

    String message() default "texte traduisible invalide (langues fr, ar, en ; longueur)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidLocalizedText, LocalizedText> {

        private boolean required;
        private int max;

        @Override
        public void initialize(ValidLocalizedText annotation) {
            this.required = annotation.required();
            this.max = annotation.max();
        }

        @Override
        public boolean isValid(LocalizedText value, ConstraintValidatorContext context) {
            if (value == null) {
                return !required;
            }
            return value.isValid(required, max);
        }
    }
}
