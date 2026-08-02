package com.localmediakit.recovery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Backs the per-account hourly cap. */
    long countByUserIdAndCreatedAtAfter(Long userId, Instant after);

    /**
     * Burns every other outstanding token for this user.
     *
     * <p>Resetting a password is a statement that the account may be
     * compromised. Leaving earlier links alive would mean whoever prompted the
     * reset still holds one.
     */
    @Modifying
    @Query("""
            update PasswordResetToken t set t.usedAt = :now
            where t.userId = :userId and t.usedAt is null and t.id <> :exceptId""")
    int invalidateOthers(@Param("userId") Long userId,
                         @Param("exceptId") Long exceptId,
                         @Param("now") Instant now);
}
