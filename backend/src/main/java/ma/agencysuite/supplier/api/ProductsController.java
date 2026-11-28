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
import ma.agencysuite.supplier.api.SupplierRequests.CreateProductRequest;
import ma.agencysuite.supplier.api.SupplierRequests.UpdateProductRequest;
import ma.agencysuite.supplier.api.SupplierResponses.PageResponse;
import ma.agencysuite.supplier.api.SupplierResponses.ProductItem;
import ma.agencysuite.supplier.api.SupplierResponses.ProductResponse;
import ma.agencysuite.supplier.application.CatalogSort.ProductSort;
import ma.agencysuite.supplier.application.ProductCatalog;
import ma.agencysuite.supplier.domain.ProductType;
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
 * Prestations des fournisseurs (US-601) : nom et description par langue (fr, ar, en). Lecture :
 * tout utilisateur de l'agence ; ecriture : {@code supplier.write}. Un identifiant d'une autre
 * agence donne 404.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "products", description = "Prestations des fournisseurs (chambres, transferts, excursions...)")
class ProductsController {

    static final String PRODUCTS = "/api/v1/products";

    private final ProductCatalog catalog;

    ProductsController(ProductCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/products")
    @Operation(summary = "Rechercher les prestations", description = "q cherche dans le nom, dans toutes ses "
            + "langues, sans tenir compte des accents, de la casse ni des traits d'union. Par defaut, les prestations "
            + "des fournisseurs archives sont masquees (supplierArchived=false).")
    @ApiResponse(responseCode = "200", description = "Page de prestations")
    @ApiResponse(responseCode = "400", description = "Parametre invalide (taille de 1 a 100, tri ou type inconnu)")
    PageResponse<ProductItem> list(
            @Parameter(description = "Texte recherche") @RequestParam(required = false) @Size(max = 200) String q,
            @Parameter(description = "Fournisseur") @RequestParam(required = false) UUID supplierId,
            @Parameter(description = "Type de prestation") @RequestParam(required = false) ProductType type,
            @Parameter(description = "Vrai : seulement les prestations archivees")
            @RequestParam(defaultValue = "false") boolean archived,
            @Parameter(description = "Faux (defaut) : prestations des fournisseurs non archives ; vrai : "
                    + "seulement celles des fournisseurs archives")
            @RequestParam(defaultValue = "false") boolean supplierArchived,
            @Parameter(description = "Numero de page, a partir de 0") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Taille de la page (1 a 100)")
            @RequestParam(defaultValue = "25") @Min(1) @Max(ProductCatalog.MAX_PAGE_SIZE) int size,
            @Parameter(description = "name (defaut) ou -updatedAt")
            @RequestParam(defaultValue = "name") @Pattern(regexp = "name|-updatedAt") String sort) {
        ProductSort order = "-updatedAt".equals(sort) ? ProductSort.UPDATED_AT_DESC : ProductSort.NAME;
        return PageResponse.from(catalog.search(q, supplierId, type, archived, supplierArchived, order, page, size),
                ProductItem::from);
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "Fiche d'une prestation (archivee ou non)", description = "Avec ses tarifs non archives, "
            + "par base puis debut de validite.")
    @ApiResponse(responseCode = "200", description = "Fiche de la prestation")
    @ApiResponse(responseCode = "404", description = "Prestation inconnue dans l'agence")
    ProductResponse get(@PathVariable UUID id) {
        return ProductResponse.from(catalog.get(id));
    }

    @PostMapping("/suppliers/{supplierId}/products")
    @RequiresPermission(PermissionCode.SUPPLIER_WRITE)
    @Operation(summary = "Creer une prestation d'un fournisseur", description = "Nom obligatoire dans au moins une "
            + "langue (fr, ar, en).")
    @ApiResponse(responseCode = "201", description = "Prestation creee ; en-tete Location (/api/v1/products/{id})")
    @ApiResponse(responseCode = "400", description = "Requete invalide (aucune langue, langue inconnue, longueur)")
    @ApiResponse(responseCode = "403", description = "Permission supplier.write absente")
    @ApiResponse(responseCode = "404", description = "Fournisseur inconnu dans l'agence")
    @ApiResponse(responseCode = "409", description = "Fournisseur archive (supplier-archived)")
    ResponseEntity<ProductResponse> create(@PathVariable UUID supplierId,
            @Valid @RequestBody CreateProductRequest request) {
        ProductResponse created = ProductResponse.from(catalog.create(supplierId, request.toData()));
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(PRODUCTS + "/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/products/{id}")
    @RequiresPermission(PermissionCode.SUPPLIER_WRITE)
    @Operation(summary = "Modifier une prestation (remplace les champs modifiables ; le fournisseur ne change pas)")
    @ApiResponse(responseCode = "200", description = "Prestation modifiee")
    @ApiResponse(responseCode = "400", description = "Requete invalide")
    @ApiResponse(responseCode = "403", description = "Permission supplier.write absente")
    @ApiResponse(responseCode = "404", description = "Prestation inconnue dans l'agence")
    @ApiResponse(responseCode = "409", description = "Version perimee")
    ProductResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateProductRequest request) {
        return ProductResponse.from(catalog.update(id, request.version(), request.toData()));
    }

    @PostMapping("/products/{id}/archive")
    @RequiresPermission(PermissionCode.SUPPLIER_WRITE)
    @Operation(summary = "Archiver une prestation (idempotent ; elle reste lisible par son identifiant)")
    @ApiResponse(responseCode = "200", description = "Prestation archivee")
    @ApiResponse(responseCode = "403", description = "Permission supplier.write absente")
    @ApiResponse(responseCode = "404", description = "Prestation inconnue dans l'agence")
    ProductResponse archive(@PathVariable UUID id) {
        return ProductResponse.from(catalog.archive(id));
    }

    @PostMapping("/products/{id}/restore")
    @RequiresPermission(PermissionCode.SUPPLIER_WRITE)
    @Operation(summary = "Restaurer une prestation archivee (idempotent)")
    @ApiResponse(responseCode = "200", description = "Prestation restauree")
    @ApiResponse(responseCode = "403", description = "Permission supplier.write absente")
    @ApiResponse(responseCode = "404", description = "Prestation inconnue dans l'agence")
    ProductResponse restore(@PathVariable UUID id) {
        return ProductResponse.from(catalog.restore(id));
    }
}
