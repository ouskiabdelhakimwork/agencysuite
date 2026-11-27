package ma.agencysuite.crm.application;

import java.util.UUID;
import ma.agencysuite.crm.domain.Contact;
import ma.agencysuite.crm.domain.ContactData;
import ma.agencysuite.crm.domain.Customer;
import ma.agencysuite.crm.infrastructure.ContactRepository;
import ma.agencysuite.crm.infrastructure.CrmConstraints;
import ma.agencysuite.crm.infrastructure.CustomerRepository;
import ma.agencysuite.shared.tenant.TenantContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contacts des clients entreprises de l'agence courante. Un contact n'est accessible que par son
 * client : un contact d'un autre client (ou d'une autre agence) donne 404. Au plus un contact
 * principal par client : en designer un nouveau retire le statut a l'ancien, dans la meme
 * transaction. N'ouvre aucun perimetre (agence requise).
 */
@Service
public class CustomerContacts {

    private final CustomerRepository customers;
    private final ContactRepository contacts;

    CustomerContacts(CustomerRepository customers, ContactRepository contacts) {
        this.customers = customers;
        this.contacts = contacts;
    }

    @Transactional
    public Contact add(UUID customerId, ContactData data) {
        Customer customer = company(customerId);
        if (data.primary()) {
            demoteCurrentPrimary(customer.getId(), null);
        }
        Contact contact = new Contact(customer.getId(), data);
        writeChecked(contact.getId(), () -> contacts.saveAndFlush(contact));
        return contact;
    }

    /** {@code version} doit etre celle du contact lu (sinon 409 stale-version). */
    @Transactional
    public Contact update(UUID customerId, UUID contactId, long version, ContactData data) {
        Customer customer = company(customerId);
        Contact contact = find(customer.getId(), contactId);
        CustomerRegistry.checkVersion(contact, contactId, contact.getVersion(), version);
        if (data.primary() && !contact.isPrimary()) {
            demoteCurrentPrimary(customer.getId(), contactId);
        }
        contact.update(data);
        writeChecked(contactId, contacts::flush);
        return contact;
    }

    /** Suppression reelle : un contact n'est pas un client facture. */
    @Transactional
    public void delete(UUID customerId, UUID contactId) {
        Customer customer = existing(customerId);
        contacts.delete(find(customer.getId(), contactId));
    }

    private Customer existing(UUID customerId) {
        TenantContext.requireAgency();
        return customers.findById(customerId).orElseThrow(() -> new CustomerNotFoundException(customerId));
    }

    private Customer company(UUID customerId) {
        Customer customer = existing(customerId);
        if (!customer.isCompany()) {
            throw new ContactsCompanyOnlyException();
        }
        return customer;
    }

    private Contact find(UUID customerId, UUID contactId) {
        return contacts.findByIdAndCustomerId(contactId, customerId)
                .orElseThrow(() -> new ContactNotFoundException(contactId));
    }

    /**
     * Un autre utilisateur a designe un contact principal en meme temps (l'index unique partiel
     * uk_contact_one_primary le refuse) : traduit en conflit de version, 409 stale-version, pour que
     * l'utilisateur recharge la fiche. Toute autre violation reste une erreur imprevue.
     */
    private static void writeChecked(UUID contactId, Runnable write) {
        try {
            write.run();
        } catch (DataIntegrityViolationException e) {
            if (CrmConstraints.isPrimaryContactTaken(e)) {
                throw new ObjectOptimisticLockingFailureException(Contact.class, contactId, e);
            }
            throw e;
        }
    }

    /**
     * Retire le statut a l'actuel principal et l'ecrit tout de suite : l'index unique partiel
     * (un seul principal par client) est verifie ligne par ligne, avant le nouveau principal.
     */
    private void demoteCurrentPrimary(UUID customerId, UUID except) {
        contacts.findByCustomerIdAndPrimaryTrue(customerId)
                .filter(current -> !current.getId().equals(except))
                .ifPresent(current -> {
                    current.demote();
                    contacts.flush();
                });
    }
}
