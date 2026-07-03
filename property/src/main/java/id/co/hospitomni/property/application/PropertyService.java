/*
 * Property content use cases — create/update/read, scoped to the account
 * bound on the current request. HospitOmni assigns and owns the UUIDs;
 * upserts are idempotent.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.property.application;

import id.co.hospitomni.property.application.command.UpsertPropertyCommand;
import id.co.hospitomni.property.domain.model.Property;
import id.co.hospitomni.property.domain.port.out.PropertyRepository;
import id.co.hospitomni.shared.AccountContext;
import id.co.hospitomni.shared.PropertyId;
import id.co.hospitomni.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.Currency;
import java.util.List;

@Service
public class PropertyService {

    private final PropertyRepository propertyRepository;

    public PropertyService(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    @Transactional(readOnly = true)
    public List<Property> listProperties() {
        return propertyRepository.findAllByAccount(AccountContext.current());
    }

    @Transactional(readOnly = true)
    public Property getProperty(PropertyId id) {
        return propertyRepository.findByIdAndAccount(id, AccountContext.current())
                .orElseThrow(() -> new ResourceNotFoundException("Property", id.value()));
    }

    @Transactional
    public Property createProperty(UpsertPropertyCommand command) {
        Property property = new Property(
                PropertyId.generate(),
                AccountContext.current(),
                command.title(),
                Currency.getInstance(command.currency()),
                ZoneId.of(command.timezone()));
        return propertyRepository.save(property);
    }

    @Transactional
    public Property updateProperty(PropertyId id, UpsertPropertyCommand command) {
        Property existing = getProperty(id);
        Property updated = new Property(
                existing.id(),
                existing.accountId(),
                command.title(),
                Currency.getInstance(command.currency()),
                ZoneId.of(command.timezone()));
        return propertyRepository.save(updated);
    }
}
