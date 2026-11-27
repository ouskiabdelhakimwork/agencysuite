package ma.agencysuite.crm.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import ma.agencysuite.crm.api.CustomerRequests.ContactRequest;
import ma.agencysuite.crm.api.CustomerRequests.CreateCustomerRequest;
import ma.agencysuite.crm.api.CustomerRequests.UpdateContactRequest;
import ma.agencysuite.crm.api.CustomerRequests.UpdateCustomerRequest;
import ma.agencysuite.crm.api.CustomerResponses.ContactResponse;
import ma.agencysuite.crm.api.CustomerResponses.CustomerPage;
import ma.agencysuite.crm.api.CustomerResponses.CustomerResponse;
import ma.agencysuite.crm.application.CustomerContacts;
import ma.agencysuite.crm.application.CustomerRegistry;
import ma.agencysuite.crm.application.CustomerSort;
import ma.agencysuite.crm.domain.CustomerType;
import ma.agencysuite.shared.security.PermissionCode;
import ma.agencysuite.shared.security.RequiresPermission;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Clients de l'agence courante et contacts des entreprises (US-401). Lecture : tout utilisateur
 * de l'agence ; ecriture : permission {@code crm.write}. L'agence vient du jeton ; un identifiant
 * d'une autre agence donne 404 (jamais 403).
 */
@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "customers", description = "Clients (particuliers et entreprises) et contacts")
class CustomersController {

    private final CustomerRegistry registry;
    private final CustomerContacts contacts;

    CustomersController(CustomerRegistry registry, CustomerContacts contacts) {
        this.registry = registry;
        this.contacts = contacts;
    }

    @GetMapping
    @Operation(summary = "Rechercher les clients", description = "q cherche, sans tenir compte de la casse, dans "
            + "le nom, l'e-mail, le telephone (aussi sous sa forme E.164) et l'ICE.")
    @ApiResponse(responseCode = "200", description = "Page de clients")
    @ApiResponse(responseCode = "400", description = "Parametre invalide (taille de 1 a 100, tri inconnu)")
    CustomerPage list(
            @Parameter(description = "Texte recherche") @RequestParam(required = false) @Size(max = 200) String q,
            @Parameter(description = "INDIVIDUAL ou COMPANY") @RequestParam(required = false) CustomerType type,
            @Parameter(description = "Responsable") @RequestParam(required = false) UUID ownerUserId,
            @Parameter(description = "Vrai : seulement les clients archives")
            @RequestParam(defaultValue = "false") boolean archived,
            @Parameter(description = "Numero de page, a partir de 0") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Taille de la page (1 a 100)")
            @RequestParam(defaultValue = "25") @Min(1) @Max(CustomerRegistry.MAX_PAGE_SIZE) int size,
            @Parameter(description = "name (defaut), -createdAt ou -updatedAt")
            @RequestParam(defaultValue = "name") @Pattern(regexp = "name|-createdAt|-updatedAt") String sort) {
        return CustomerPage.from(registry.search(q, type, ownerUserId, archived, sortOf(sort), page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fiche d'un client (archive ou non), avec ses contacts")
    @ApiResponse(responseCode = "200", description = "Fiche du client")
    @ApiResponse(responseCode = "404", description = "Client inconnu dans l'agence")
    CustomerResponse get(@PathVariable UUID id) {
        return CustomerResponse.from(registry.get(id));
    }

    @PostMapping
    @RequiresPermission(PermissionCode.CRM_WRITE)
    @Operation(summary = "Creer un client", description = "409 possible-duplicate si un client non archive a le "
            + "meme e-mail, telephone ou ICE ; renvoyer avec confirmDuplicate: true pour creer quand meme.")
    @ApiResponse(responseCode = "201", description = "Client cree ; en-tete Location")
    @ApiResponse(responseCode = "400", description = "Requete invalide, ou responsable inconnu dans l'agence")
    @ApiResponse(responseCode = "403", description = "Permission crm.write absente")
    @ApiResponse(responseCode = "409", description = "Doublon probable (candidats dans la reponse)")
    ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerResponse created = CustomerResponse.from(registry.create(request.toData(), request.confirmDuplicate()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @RequiresPermission(PermissionCode.CRM_WRITE)
    @Operation(summary = "Modifier un client (remplace les champs modifiables)")
    @ApiResponse(responseCode = "200", description = "Client modifie")
    @ApiResponse(responseCode = "400", description = "Requete invalide, ou responsable inconnu dans l'agence")
    @ApiResponse(responseCode = "404", description = "Client inconnu dans l'agence")
    @ApiResponse(responseCode = "409", description = "Version perimee, doublon probable, ou entreprise avec contacts "
            + "qui deviendrait un particulier")
    CustomerResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateCustomerRequest request) {
        return CustomerResponse.from(
                registry.update(id, request.version(), request.toData(), request.confirmDuplicate()));
    }

    @PostMapping("/{id}/archive")
    @RequiresPermission(PermissionCode.CRM_WRITE)
    @Operation(summary = "Archiver un client (idempotent ; il reste lisible par son identifiant)")
    @ApiResponse(responseCode = "200", description = "Client archive")
    @ApiResponse(responseCode = "404", description = "Client inconnu dans l'agence")
    CustomerResponse archive(@PathVariable UUID id) {
        return CustomerResponse.from(registry.archive(id));
    }

    @PostMapping("/{id}/restore")
    @RequiresPermission(PermissionCode.CRM_WRITE)
    @Operation(summary = "Restaurer un client archive (idempotent)")
    @ApiResponse(responseCode = "200", description = "Client restaure")
    @ApiResponse(responseCode = "404", description = "Client inconnu dans l'agence")
    CustomerResponse restore(@PathVariable UUID id) {
        return CustomerResponse.from(registry.restore(id));
    }

    @PostMapping("/{id}/contacts")
    @RequiresPermission(PermissionCode.CRM_WRITE)
    @Operation(summary = "Ajouter un contact a une entreprise")
    @ApiResponse(responseCode = "201", description = "Contact cree ; en-tete Location")
    @ApiResponse(responseCode = "404", description = "Client inconnu dans l'agence")
    @ApiResponse(responseCode = "409", description = "Le client n'est pas une entreprise")
    ResponseEntity<ContactResponse> addContact(@PathVariable UUID id, @Valid @RequestBody ContactRequest request) {
        ContactResponse created = ContactResponse.from(contacts.add(id, request.toData()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{contactId}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}/contacts/{contactId}")
    @RequiresPermission(PermissionCode.CRM_WRITE)
    @Operation(summary = "Modifier un contact")
    @ApiResponse(responseCode = "200", description = "Contact modifie")
    @ApiResponse(responseCode = "404", description = "Client inconnu, ou contact d'un autre client")
    @ApiResponse(responseCode = "409", description = "Version perimee")
    ContactResponse updateContact(@PathVariable UUID id, @PathVariable UUID contactId,
            @Valid @RequestBody UpdateContactRequest request) {
        return ContactResponse.from(contacts.update(id, contactId, request.version(), request.toData()));
    }

    @DeleteMapping("/{id}/contacts/{contactId}")
    @RequiresPermission(PermissionCode.CRM_WRITE)
    @Operation(summary = "Supprimer un contact (suppression reelle)")
    @ApiResponse(responseCode = "204", description = "Contact supprime")
    @ApiResponse(responseCode = "404", description = "Client inconnu, ou contact d'un autre client")
    ResponseEntity<Void> deleteContact(@PathVariable UUID id, @PathVariable UUID contactId) {
        contacts.delete(id, contactId);
        return ResponseEntity.noContent().build();
    }

    private static CustomerSort sortOf(String sort) {
        return switch (sort) {
            case "-createdAt" -> CustomerSort.CREATED_AT_DESC;
            case "-updatedAt" -> CustomerSort.UPDATED_AT_DESC;
            default -> CustomerSort.NAME;
        };
    }
}
