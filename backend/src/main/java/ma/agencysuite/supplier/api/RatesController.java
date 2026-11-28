package ma.agencysuite.supplier.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.agencysuite.shared.security.PermissionCode;
import ma.agencysuite.shared.security.RequiresPermission;
import ma.agencysuite.supplier.api.SupplierRequests.CreateRateRequest;
import ma.agencysuite.supplier.api.SupplierRequests.UpdateRateRequest;
import ma.agencysuite.supplier.api.SupplierResponses.RateResponse;
import ma.agencysuite.supplier.application.RateBook;
import ma.agencysuite.supplier.domain.RateBasis;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tarifs d'achat d'une prestation, par periode (dates incluses) et par base, sans chevauchement
 * pour une meme base. Lecture : tout utilisateur de l'agence ; ecriture : {@code supplier.write}.
 * Une prestation d'une autre agence, ou un tarif d'une autre prestation, donne 404.
 */
@RestController
@RequestMapping("/api/v1/products/{productId}/rates")
@Tag(name = "rates", description = "Tarifs d'achat des prestations, par periode et par base")
class RatesController {

    private final RateBook book;

    RatesController(RateBook book) {
        this.book = book;
    }

    @GetMapping
    @Operation(summary = "Tarifs d'une prestation", description = "Par base puis debut de validite.")
    @ApiResponse(responseCode = "200", description = "Tarifs")
    @ApiResponse(responseCode = "404", description = "Prestation inconnue dans l'agence")
    List<RateResponse> list(@PathVariable UUID productId,
            @Parameter(description = "Vrai : seulement les tarifs archives")
            @RequestParam(defaultValue = "false") boolean archived,
            @Parameter(description = "Base") @RequestParam(required = false) RateBasis basis,
            @Parameter(description = "Date (AAAA-MM-JJ) : seulement les tarifs valables ce jour-la")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on) {
        return book.list(productId, basis, on, archived).stream().map(RateResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(PermissionCode.SUPPLIER_WRITE)
    @Operation(summary = "Creer un tarif", description = "purchase.amount est une chaine (un nombre JSON est "
            + "refuse), sans plus de decimales que la devise, jamais arrondie. Devise absente : celle du "
            + "fournisseur. 409 rate-overlap si la periode chevauche un tarif non archive de la meme base.")
    @ApiResponse(responseCode = "201", description = "Tarif cree")
    @ApiResponse(responseCode = "400", description = "Requete invalide (montant, devise, validTo avant validFrom)")
    @ApiResponse(responseCode = "403", description = "Permission supplier.write absente")
    @ApiResponse(responseCode = "404", description = "Prestation inconnue dans l'agence")
    @ApiResponse(responseCode = "409", description = "Chevauchement (rate-overlap, tarif en conflit) ou prestation "
            + "archivee (product-archived)")
    RateResponse create(@PathVariable UUID productId, @Valid @RequestBody CreateRateRequest request) {
        return RateResponse.from(book.create(productId, request.toInput()));
    }

    @PutMapping("/{rateId}")
    @RequiresPermission(PermissionCode.SUPPLIER_WRITE)
    @Operation(summary = "Modifier un tarif (remplace les champs)", description = "Devise absente : celle du tarif. "
            + "Memes controles que la creation.")
    @ApiResponse(responseCode = "200", description = "Tarif modifie")
    @ApiResponse(responseCode = "400", description = "Requete invalide")
    @ApiResponse(responseCode = "403", description = "Permission supplier.write absente")
    @ApiResponse(responseCode = "404", description = "Prestation inconnue, ou tarif d'une autre prestation")
    @ApiResponse(responseCode = "409", description = "Version perimee, chevauchement ou prestation archivee")
    RateResponse update(@PathVariable UUID productId, @PathVariable UUID rateId,
            @Valid @RequestBody UpdateRateRequest request) {
        return RateResponse.from(book.update(productId, rateId, request.version(), request.toInput()));
    }

    @PostMapping("/{rateId}/archive")
    @RequiresPermission(PermissionCode.SUPPLIER_WRITE)
    @Operation(summary = "Archiver un tarif (idempotent ; un tarif errone est archive, jamais supprime)")
    @ApiResponse(responseCode = "200", description = "Tarif archive")
    @ApiResponse(responseCode = "403", description = "Permission supplier.write absente")
    @ApiResponse(responseCode = "404", description = "Prestation inconnue, ou tarif d'une autre prestation")
    RateResponse archive(@PathVariable UUID productId, @PathVariable UUID rateId) {
        return RateResponse.from(book.archive(productId, rateId));
    }

    @PostMapping("/{rateId}/restore")
    @RequiresPermission(PermissionCode.SUPPLIER_WRITE)
    @Operation(summary = "Restaurer un tarif archive (idempotent)", description = "409 rate-overlap si sa periode "
            + "chevauche un tarif non archive de la meme base.")
    @ApiResponse(responseCode = "200", description = "Tarif restaure")
    @ApiResponse(responseCode = "403", description = "Permission supplier.write absente")
    @ApiResponse(responseCode = "404", description = "Prestation inconnue, ou tarif d'une autre prestation")
    @ApiResponse(responseCode = "409", description = "Chevauchement avec un tarif actif")
    RateResponse restore(@PathVariable UUID productId, @PathVariable UUID rateId) {
        return RateResponse.from(book.restore(productId, rateId));
    }
}
