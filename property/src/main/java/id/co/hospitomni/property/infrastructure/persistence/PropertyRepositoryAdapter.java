/*
 * PropertyRepository port implementation over Spring Data JPA.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.property.infrastructure.persistence;

import id.co.hospitomni.property.domain.model.Property;
import id.co.hospitomni.property.domain.port.out.PropertyRepository;
import id.co.hospitomni.property.infrastructure.persistence.entity.PropertyJpaEntity;
import id.co.hospitomni.shared.AccountId;
import id.co.hospitomni.shared.PropertyId;
import org.springframework.stereotype.Repository;

import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

@Repository
public class PropertyRepositoryAdapter implements PropertyRepository {

    private final PropertyJpaRepository jpa;

    public PropertyRepositoryAdapter(PropertyJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Property save(Property property) {
        PropertyJpaEntity entity = jpa.findById(property.id().value())
                .map(existing -> {
                    existing.updateContent(property.title(),
                            property.currency().getCurrencyCode(), property.timezone().getId());
                    return existing;
                })
                .orElseGet(() -> new PropertyJpaEntity(
                        property.id().value(), property.accountId().value(), property.title(),
                        property.currency().getCurrencyCode(), property.timezone().getId()));
        return toDomain(jpa.save(entity));
    }

    @Override
    public Optional<Property> findByIdAndAccount(PropertyId id, AccountId accountId) {
        return jpa.findByIdAndAccountId(id.value(), accountId.value())
                .map(PropertyRepositoryAdapter::toDomain);
    }

    @Override
    public List<Property> findAllByAccount(AccountId accountId) {
        return jpa.findAllByAccountIdOrderByTitle(accountId.value()).stream()
                .map(PropertyRepositoryAdapter::toDomain)
                .toList();
    }

    private static Property toDomain(PropertyJpaEntity entity) {
        return new Property(
                PropertyId.of(entity.id()),
                AccountId.of(entity.accountId()),
                entity.title(),
                Currency.getInstance(entity.currency().trim()),
                ZoneId.of(entity.timezone()));
    }
}
