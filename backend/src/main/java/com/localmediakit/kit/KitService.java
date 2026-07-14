package com.localmediakit.kit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class KitService {

    private final KitRepository repository;
    private final RevalidationClient revalidationClient;

    public KitService(KitRepository repository, RevalidationClient revalidationClient) {
        this.repository = repository;
        this.revalidationClient = revalidationClient;
    }

    @Transactional(readOnly = true)
    public Optional<Kit> findBySlug(String slug) {
        return repository.findById(slug);
    }

    /**
     * Persists the new content, then triggers on-demand revalidation of the public page.
     * The revalidation call happens after the save is committed (not inside a transaction),
     * so we never hold a DB transaction open across a network call.
     *
     * @return the revalidation HTTP status from the frontend (-1 if unreachable).
     */
    public int publish(String slug, String content) {
        Kit kit = repository.findById(slug)
                .map(existing -> {
                    existing.updateContent(content);
                    return existing;
                })
                .orElseGet(() -> new Kit(slug, content));
        repository.save(kit);
        return revalidationClient.revalidate(slug);
    }
}
