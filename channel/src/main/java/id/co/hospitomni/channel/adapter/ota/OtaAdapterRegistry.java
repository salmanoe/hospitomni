/*
 * Name-keyed registry over every OtaAdapterPort bean — the relay resolves
 * the adapter for a channel's ota_name here.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.channel.adapter.ota;

import id.co.hospitomni.channel.domain.port.out.OtaAdapterPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class OtaAdapterRegistry {

    private final Map<String, OtaAdapterPort> adapters;

    public OtaAdapterRegistry(List<OtaAdapterPort> adapterBeans) {
        this.adapters = adapterBeans.stream()
                .collect(Collectors.toUnmodifiableMap(OtaAdapterPort::otaName, Function.identity()));
    }

    public boolean knows(String otaName) {
        return adapters.containsKey(otaName);
    }

    public OtaAdapterPort byName(String otaName) {
        OtaAdapterPort adapter = adapters.get(otaName);
        if (adapter == null) {
            throw new IllegalStateException("No OTA adapter registered for: " + otaName);
        }
        return adapter;
    }
}
