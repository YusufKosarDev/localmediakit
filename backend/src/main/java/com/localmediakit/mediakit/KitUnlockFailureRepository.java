package com.localmediakit.mediakit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface KitUnlockFailureRepository extends JpaRepository<KitUnlockFailure, Long> {

    long countByAttemptKeyAndFailedAtAfter(String attemptKey, Instant cutoff);

    @Modifying
    @Query("delete from KitUnlockFailure f where f.attemptKey = :key")
    void deleteByAttemptKey(@Param("key") String key);

    /**
     * The sweep. Without it the table keeps a handful of rows for every client
     * that ever guessed wrong and then left, forever — bounded per key by the
     * guard itself, unbounded in the number of keys.
     */
    @Modifying
    @Query("delete from KitUnlockFailure f where f.failedAt <= :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
