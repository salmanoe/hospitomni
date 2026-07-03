/*
 * Property read use cases — scoped to the account bound on the current request.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.property.application;

import id.co.hospitomni.property.domain.model.Property;
import id.co.hospitomni.property.domain.port.out.PropertyRepository;
import id.co.hospitomni.shared.AccountContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
