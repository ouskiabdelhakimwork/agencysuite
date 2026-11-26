package ma.agencysuite.iam;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import ma.agencysuite.iam.application.EmailAlreadyRegisteredException;
import ma.agencysuite.iam.application.IdentityAdminUnavailableException;
import ma.agencysuite.iam.application.IdentityProviderAdmin;
import ma.agencysuite.iam.application.ProvisionedOrganization;
import ma.agencysuite.shared.tenant.AgencyId;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Fournisseur d'identite en memoire, a la place de Keycloak dans les tests d'integration (aucun
 * Keycloak reel). Garde les Organizations, les utilisateurs et la trace des appels ; sait simuler une
 * panne par operation ({@link #failOn}). Partage par tous les tests du contexte : chaque test utilise
 * ses propres e-mails et agences, et appelle {@link #reset()} apres lui.
 */
@Component
@Primary
public class FakeIdentityProviderAdmin implements IdentityProviderAdmin {

    /** Operations du port, pour {@link #failOn}. */
    public enum Operation { ENSURE_ORGANIZATION, CREATE_USER, ADD_MEMBER, SEND_INVITATION, SET_ENABLED, LOGOUT, DELETE_USER }

    /** Utilisateur du faux fournisseur. */
    public record User(String sub, String email, String firstName, String lastName, String locale, boolean enabled) {

        User withEnabled(boolean value) {
            return new User(sub, email, firstName, lastName, locale, value);
        }
    }

    /** Appel recu : operation et argument principal (sub, alias ou identifiant d'Organization). */
    public record Call(Operation operation, String target) {
    }

    private final Map<String, String> organizationsByAlias = new ConcurrentHashMap<>();
    private final Map<String, String> organizationNames = new ConcurrentHashMap<>();
    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> members = new ConcurrentHashMap<>();
    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private final Set<Operation> failing = ConcurrentHashMap.newKeySet();
    private volatile Consumer<String> invitationProbe = sub -> { };
    private volatile String nextSub;

    /** Les appels suivants a cette operation echouent ({@link IdentityAdminUnavailableException}). */
    public void failOn(Operation operation) {
        failing.add(operation);
    }

    /** Fin des pannes simulees et oubli des appels (les donnees restent, isolees par e-mail et agence). */
    public void reset() {
        failing.clear();
        calls.clear();
        nextSub = null;
        invitationProbe = sub -> { };
    }

    /**
     * Le prochain {@code createUser} renvoie ce sub (ex. celui d'un utilisateur deja en base, pour
     * provoquer la violation de la contrainte d'unicite de {@code external_idp_id} a l'ecriture).
     */
    public void returnSubOnNextCreate(String sub) {
        nextSub = sub;
    }

    /** Code execute au moment de l'envoi d'une invitation (ex. verifier ce que la base contient alors). */
    public void onSendInvitation(Consumer<String> probe) {
        invitationProbe = probe;
    }

    public List<Call> calls() {
        return List.copyOf(calls);
    }

    public List<Call> calls(Operation operation) {
        return calls.stream().filter(call -> call.operation() == operation).toList();
    }

    public Optional<User> user(String sub) {
        return Optional.ofNullable(users.get(sub));
    }

    public Optional<User> userByEmail(String email) {
        return users.values().stream().filter(user -> user.email().equals(email)).findFirst();
    }

    public Optional<String> organizationOf(UUID agencyId) {
        return Optional.ofNullable(organizationsByAlias.get(agencyId.toString()));
    }

    public Optional<String> organizationName(String organizationId) {
        return Optional.ofNullable(organizationNames.get(organizationId));
    }

    public Set<String> membersOf(String organizationId) {
        return Set.copyOf(members.getOrDefault(organizationId, Set.of()));
    }

    /** Ajoute un utilisateur deja connu du fournisseur (cas « e-mail deja enregistre » chez Keycloak). */
    public String preexistingUser(String email) {
        String sub = UUID.randomUUID().toString();
        users.put(sub, new User(sub, email, "Deja", "Inscrit", "fr", true));
        return sub;
    }

    @Override
    public ProvisionedOrganization ensureOrganization(AgencyId agencyId, String name) {
        record(Operation.ENSURE_ORGANIZATION, agencyId.value().toString());
        String alias = agencyId.value().toString();
        String existing = organizationsByAlias.get(alias);
        if (existing != null) {
            return new ProvisionedOrganization(existing, false);
        }
        String id = UUID.randomUUID().toString();
        organizationsByAlias.put(alias, id);
        organizationNames.put(id, name);
        return new ProvisionedOrganization(id, true);
    }

    @Override
    public String createUser(String email, String firstName, String lastName, String locale) {
        record(Operation.CREATE_USER, null);
        if (userByEmail(email).isPresent()) {
            throw new EmailAlreadyRegisteredException();
        }
        String forced = nextSub;
        nextSub = null;
        String sub = forced != null ? forced : UUID.randomUUID().toString();
        users.put(sub, new User(sub, email, firstName, lastName, locale, true));
        return sub;
    }

    @Override
    public void addMember(String organizationId, String sub) {
        record(Operation.ADD_MEMBER, sub);
        members.computeIfAbsent(organizationId, id -> ConcurrentHashMap.newKeySet()).add(sub);
    }

    @Override
    public void sendInvitation(String sub) {
        record(Operation.SEND_INVITATION, sub);
        invitationProbe.accept(sub);
    }

    @Override
    public void setEnabled(String sub, boolean enabled) {
        record(Operation.SET_ENABLED, sub);
        users.computeIfPresent(sub, (key, user) -> user.withEnabled(enabled));
    }

    @Override
    public void logout(String sub) {
        record(Operation.LOGOUT, sub);
    }

    @Override
    public void deleteUser(String sub) {
        record(Operation.DELETE_USER, sub);
        users.remove(sub);
        members.values().forEach(set -> set.remove(sub));
    }

    private void record(Operation operation, String target) {
        if (failing.contains(operation)) {
            calls.add(new Call(operation, target));
            throw new IdentityAdminUnavailableException("Panne simulee : " + operation);
        }
        calls.add(new Call(operation, target));
    }
}
