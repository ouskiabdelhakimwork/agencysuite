package ma.agencysuite.supplier.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import ma.agencysuite.shared.money.Money;
import ma.agencysuite.shared.money.MoneyJson;
import ma.agencysuite.shared.tenant.TenantContext;
import ma.agencysuite.supplier.domain.Product;
import ma.agencysuite.supplier.domain.Rate;
import ma.agencysuite.supplier.domain.RateBasis;
import ma.agencysuite.supplier.domain.RateData;
import ma.agencysuite.supplier.domain.Supplier;
import ma.agencysuite.supplier.infrastructure.ProductRepository;
import ma.agencysuite.supplier.infrastructure.RateRepository;
import ma.agencysuite.supplier.infrastructure.RateSpecifications;
import ma.agencysuite.supplier.infrastructure.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tarifs d'achat des prestations : liste, creation, modification, archivage et restauration.
 *
 * <p>Non-chevauchement : pour une meme prestation et une meme base, deux tarifs non archives n'ont
 * jamais de jour commun (dates incluses). Le controle est fait dans l'application, apres avoir pose
 * un verrou pessimiste ({@code SELECT ... FOR UPDATE}) sur la ligne de la prestation : deux
 * ecritures simultanees de tarifs d'une meme prestation sont serialisees, la seconde voit le tarif
 * valide par la premiere. Pas de contrainte d'exclusion en base (btree_gist non confirme,
 * TODO(etape-12) dans V9).
 *
 * <p>Devise : celle envoyee, sinon celle du fournisseur a la creation (et celle du tarif a la
 * modification). Montant exact, strictement positif, jamais arrondi. Pas d'audit des tarifs a ce lot.
 * N'ouvre aucun perimetre (voir {@link SupplierRegistry}).
 */
@Service
public class RateBook {

    /** Aucun tarif ne porte cet identifiant : « pas de tarif a exclure » du controle de chevauchement. */
    private static final UUID NO_RATE = new UUID(0L, 0L);

    /** Champs saisis d'un tarif ; devise du montant facultative. */
    public record RateInput(RateBasis basis, LocalDate validFrom, LocalDate validTo, MoneyJson purchase,
            String label) {
    }

    private final ProductRepository products;
    private final SupplierRepository suppliers;
    private final RateRepository rates;

    RateBook(ProductRepository products, SupplierRepository suppliers, RateRepository rates) {
        this.products = products;
        this.suppliers = suppliers;
        this.rates = rates;
    }

    /**
     * Tarifs de la prestation, par base puis debut de validite.
     *
     * @param archived vrai : seulement les tarifs archives
     * @param on si present : seulement les tarifs valables ce jour-la
     */
    @Transactional(readOnly = true)
    public List<Rate> list(UUID productId, RateBasis basis, LocalDate on, boolean archived) {
        Product product = findProduct(productId);
        return rates.findAll(RateSpecifications.matching(product.getId(), basis, on, archived)).stream()
                .sorted(ProductCatalog.RATE_ORDER).toList();
    }

    /** Cree le tarif : 404 prestation inconnue, 409 product-archived ou rate-overlap. */
    @Transactional
    public Rate create(UUID productId, RateInput input) {
        Product product = lockProduct(productId);
        if (product.isArchived()) {
            throw new SupplierException.ProductArchived();
        }
        Supplier supplier = suppliers.findById(product.getSupplierId()).orElseThrow();
        RateData data = toData(input, supplier.getDefaultCurrency());
        rejectOverlap(product.getId(), data.basis(), data.validFrom(), data.validTo(), NO_RATE);
        return rates.saveAndFlush(Rate.create(product.getId(), data));
    }

    /**
     * Remplace les champs du tarif. Devise absente : celle du tarif (jamais celle du fournisseur,
     * qui a pu changer). Memes controles que la creation ; un tarif archive n'est controle qu'a sa
     * restauration.
     */
    @Transactional
    public Rate update(UUID productId, UUID rateId, long version, RateInput input) {
        Product product = lockProduct(productId);
        Rate rate = findRate(product, rateId);
        SupplierRegistry.checkVersion(rate, rate.getId(), rate.getVersion(), version);
        if (product.isArchived()) {
            throw new SupplierException.ProductArchived();
        }
        RateData data = toData(input, rate.getPurchase().currency().getCurrencyCode());
        if (!rate.isArchived()) {
            rejectOverlap(product.getId(), data.basis(), data.validFrom(), data.validTo(), rate.getId());
        }
        rate.update(data);
        rates.flush();
        return rate;
    }

    /** Archive le tarif (idempotent, sans audit). */
    @Transactional
    public Rate archive(UUID productId, UUID rateId) {
        Rate rate = findRate(findProduct(productId), rateId);
        if (rate.archive(Instant.now())) {
            rates.flush();
        }
        return rate;
    }

    /** Restaure le tarif (idempotent) : 409 rate-overlap si sa periode chevauche un tarif actif. */
    @Transactional
    public Rate restore(UUID productId, UUID rateId) {
        Product product = lockProduct(productId);
        Rate rate = findRate(product, rateId);
        if (rate.isArchived()) {
            rejectOverlap(product.getId(), rate.getBasis(), rate.getValidFrom(), rate.getValidTo(), rate.getId());
            rate.restore();
            rates.flush();
        }
        return rate;
    }

    private static RateData toData(RateInput input, String defaultCurrency) {
        Money purchase = Objects.requireNonNull(input.purchase(), "purchase").toPositiveMoney(defaultCurrency);
        return new RateData(input.basis(), input.validFrom(), input.validTo(), purchase, input.label());
    }

    private void rejectOverlap(UUID productId, RateBasis basis, LocalDate from, LocalDate to, UUID self) {
        List<Rate> overlapping = rates.findOverlapping(productId, basis, from, to, self);
        if (!overlapping.isEmpty()) {
            Rate conflict = overlapping.getFirst();
            throw new RateOverlapException(conflict.getId(), conflict.getValidFrom(), conflict.getValidTo());
        }
    }

    private Product findProduct(UUID productId) {
        TenantContext.requireAgency();
        return products.findById(productId).orElseThrow(() -> new SupplierException.NotFound("Prestation", productId));
    }

    /** Prestation verrouillee jusqu'a la fin de la transaction (voir la documentation de la classe). */
    private Product lockProduct(UUID productId) {
        TenantContext.requireAgency();
        return products.findByIdForUpdate(productId)
                .orElseThrow(() -> new SupplierException.NotFound("Prestation", productId));
    }

    private Rate findRate(Product product, UUID rateId) {
        return rates.findByIdAndProductId(rateId, product.getId())
                .orElseThrow(() -> new SupplierException.NotFound("Tarif", rateId));
    }
}
