package ma.agencysuite.shared.module;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Reserve un controleur (ou une methode) aux agences ayant souscrit le module indique.
 *
 * <p>Verifie cote serveur a chaque requete, dans le perimetre de l'agence courante :
 * si le module n'est pas active, la requete est refusee (403, problem+json de type
 * {@code module-not-enabled}), meme en appel direct de l'API. L'annotation de methode
 * l'emporte sur celle de la classe.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequiresModule {

    ModuleCode value();
}
