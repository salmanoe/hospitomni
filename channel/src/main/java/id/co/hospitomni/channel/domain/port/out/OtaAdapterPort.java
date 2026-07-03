/*
 * THE adapter seam (direct analogue of HospitOps's ChannelConnectorPort):
 * one implementation per OTA, registered by name. Called by the outbox
 * relay only — failures throw so the relay retries with backoff.
 *
 * @author Salman
 * @version 1.0
 * @since 2026-07-03
 */
package id.co.hospitomni.channel.domain.port.out;

import id.co.hospitomni.channel.domain.model.AriPush;

public interface OtaAdapterPort {

    /** Registry key, e.g. "mock", "traveloka", "agoda". */
    String otaName();

    /**
     * Deliver one coalesced ARI push.
     *
     * @throws OtaPushException on any provider/transport failure, so the
     *                          relay backs off and retries.
     */
    void pushAri(AriPush push);
}
