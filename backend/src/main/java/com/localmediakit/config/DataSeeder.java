package com.localmediakit.config;

import com.localmediakit.kit.Kit;
import com.localmediakit.kit.KitRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds a single demo kit so the public page has something to render on a fresh
 * database. Idempotent: only inserts when the slug is missing.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final KitRepository repository;

    public DataSeeder(KitRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.findById("demo").isEmpty()) {
            repository.save(new Kit("demo", "Merhaba - bu ilk yayinlanan icerik (seed)."));
        }
    }
}
