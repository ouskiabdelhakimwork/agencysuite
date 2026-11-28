package ma.agencysuite.supplier.api;

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
import ma.agencysuite.shared.security.PermissionCode;
import ma.agencysuite.shared.security.RequiresPermission;
import ma.agencysuite.supplier.api.SupplierRequests.CreateSupplierRequest;
import ma.agencysuite.supplier.api.SupplierRequests.UpdateSupplierRequest;
import ma.agencysuite.supplier.api.SupplierResponses.PageResponse;
import ma.agencysuite.supplier.api.SupplierResponses.SupplierItem;
import ma.agencysuite.supplier.api.SupplierResponses.SupplierResponse;
import ma.agencysuite.supplier.application.CatalogSort.SupplierSort;
import ma.agencysuite.supplier.application.SupplierRegistry;
import ma.agencysuite.supplier.domain.SupplierType;
import org.springframework.http.ResponseEntity;
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
 * Fournisseurs de l'agence courante (US-601). Lecture : tout utilisateur de l'agence ; ecriture :
 * permission {@code supplier.write}. Socle : pas de {@code @RequiresModule}. L'agence vient du
 * jeton ; un identifiant d'une autre agence donne 404 (jamais 403).
 */
@RestController
@RequestMapping("/api/v1/suppliers")
@Tag(name = "suppliers", description = "Fournisseurs (hotels, riads, transporteurs, DMC, lieux...)")
class SuppliersController {

    private final SupplierRegistry registry;

    SuppliersController(SupplierRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    @Operation(summary = "Rechercher les fournisseurs", description = "q cherche dans le nom (sans tenir compte "
            + "des accents ni de la casse), l'e-mail, le telephone (aussi sous sa forme E.164), l'ICE et la ville. "
            + "productCount : nombre de prestations non archivees.")
    @ApiResponse(responseCode = "200", description = "Page de fournisseurs")
    @ApiResponse(responseCode = "400", description = "Parametre invalide (taille de 1 a 100, tri ou type inconnu)")
    PageResponse<SupplierItem> list(
            @Parameter(description = "Texte recherche") @RequestParam(required = false) @Size(max = 200) String q,
            @Parameter(description = "Type de fournisseur") @RequestParam(required = false) SupplierType type,
            @Parameter(description = "Ville (egalite, sans tenir compte de la casse)")
            @RequestParam(required = false) @Size(max = 100) String city,
            @Parameter(description = "Vrai : seulement les fournisseurs archives")
            @RequestParam(defaultValue = "false") boolean archived,
            @Parameter(description = "Numero de page, a partir de 0") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Taille de la page (1 a 100)")
            @RequestParam(defaultValue = "25") @Min(1) @Max(SupplierRegistry.MAX_PAGE_SIZE) int size,
            @Parameter(description = "name (defaut), -createdAt ou -updatedAt")
            @RequestParam(defaultValue = "name") @Pattern(regexp = "name|-createdAt|-updatedAt") String sort) {
        return PageResponse.from(registry.search(q, type, city, archived, sortOf(sort), page, size),
                SupplierItem::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fiche d'un fournisseur (archive ou non)")
    @ApiResponse(responseCode = "200", description = "Fiche du fournisseur")
    @ApiResponse(responseCode = "404", description = "Fournisseur inconnu dans l'agence")
    SupplierResponse get(@PathVariable UUID id) {
        return SupplierResponse.from(registry.get(id));
    }

    @PostMapping
    @RequiresPermission(PermissionCode.SUPPLIER_WRITE)
    @Operation(summary = "Creer un fournisseur", description = "409 possible-duplicate si un fournisseur non "
            + "archive a le meme ICE, e-mail ou telephone ; renvoyer avec confirmDuplicate: true pour creer quand "
            + "meme. Pays MA et devise MAD par defaut.")
    @ApiResponse(responseCode = "201", description = "Fournisseur cree ; en-tete Location")
    @ApiResponse(responseCode = "400", description = "Requete invalide")
    @ApiResponse(responseCode = "403", description = "Permission supplier.write absente")
    @ApiResponse(responseCode = "409", description = "Doublon probable (candidats dans la reponse)")
    ResponseEntity<SupplierResponse> create(@Valid @RequestBody CreateSupplierRequest request) {
        SupplierResponse created = SupplierResponse.from(registry.create(request.toData(), request.confirmDuplicate()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @RequiresPermission(PermissionCode.SUPPLIER_WRITE)
    @Operation(summary = "Modifier un fournisseur (remplace les champs modifiables)", description = "Changer la "
            + "devise par defaut ne modifie pas les tarifs existants.")
    @ApiResponse(responseCode = "200", description = "Fournisseur modifie")
    @ApiResponse(responseCode = "400", description = "Requete invalide")
    @ApiResponse(responseCode = "403", description = "Permission supplier.write absente")
    @ApiResponse(responseCode = "404", description = "Fournisseur inconnu dans l'agence")
    @ApiResponse(responseCode = "409", description = "Version perimee ou doublon probable")
    SupplierResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateSupplierRequest request) {
        return SupplierResponse.from(
                registry.update(id, request.version(), request.toData(), request.confirmDuplicate()));
    }

    @PostMapping("/{id}/archive")
    @RequiresPermission(PermissionCode.SUPPLIER_WRITE)
    @Operation(summary = "Archiver un fournisseur (idempotent)", description = "Il reste lisible par son "
            + "identifiant ; ses prestations ne sont pas archivees mais disparaissent de la liste par defaut "
            + "(filtre supplierArchived).")
    @ApiResponse(responseCode = "200", description = "Fournisseur archive")
    @ApiResponse(responseCode = "403", description = "Permission supplier.write absente")
    @ApiResponse(responseCode = "404", description = "Fournisseur inconnu dans l'agence")
    SupplierResponse archive(@PathVariable UUID id) {
        return SupplierResponse.from(registry.archive(id));
    }

    @PostMapping("/{id}/restore")
    @RequiresPermission(PermissionCode.SUPPLIER_WRITE)
    @Operation(summary = "Restaurer un fournisseur archive (idempotent)")
    @ApiResponse(responseCode = "200", description = "Fournisseur restaure")
    @ApiResponse(responseCode = "403", description = "Permission supplier.write absente")
    @ApiResponse(responseCode = "404", description = "Fournisseur inconnu dans l'agence")
    SupplierResponse restore(@PathVariable UUID id) {
        return SupplierResponse.from(registry.restore(id));
    }

    private static SupplierSort sortOf(String sort) {
        return switch (sort) {
            case "-createdAt" -> SupplierSort.CREATED_AT_DESC;
            case "-updatedAt" -> SupplierSort.UPDATED_AT_DESC;
            default -> SupplierSort.NAME;
        };
    }
}
