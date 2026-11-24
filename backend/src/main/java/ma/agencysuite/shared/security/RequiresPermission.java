package ma.agencysuite.shared.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Reserve un controleur (ou une methode) aux utilisateurs de l'agence qui ont la permission
 * indiquee par l'un de leurs roles.
 *
 * <p>Verifie cote serveur a chaque requete, apres {@code @RequiresModule} (un module non souscrit
 * repond {@code module-not-enabled} avant toute question de permission). Sans utilisateur courant :
 * 403 {@code access-denied} ; permission absente : 403 {@code permission-denied}. L'annotation de
 * methode l'emporte sur celle de la classe.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequiresPermission {

    PermissionCode value();
}
