package com.localmediakit.domain;

import java.util.List;

/**
 * Resolves TXT records for a host. Abstracted behind an interface so the
 * verification logic can be tested with controlled records instead of hitting
 * real DNS.
 */
public interface DnsResolver {

    /**
     * @return the TXT record strings at {@code host} (unquoted), or an empty
     *         list when the host has no TXT records / does not exist.
     * @throws DnsLookupException on an infrastructure-level failure (timeout,
     *         resolver error) — never for a simple "no such record".
     */
    List<String> lookupTxt(String host);
}
