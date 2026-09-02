package com.localmediakit.user;

import com.localmediakit.notification.NotificationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EmailChangeRequestRepository extends JpaRepository<EmailChangeRequest, Long> {

    Optional<EmailChangeRequest> findByTokenHash(String tokenHash);

    /** The dispatcher's queue: due work, oldest first. */
    @Query("""
            select r from EmailChangeRequest r
            where r.status = :status and r.nextAttemptAt <= :now
            order by r.nextAttemptAt asc
            """)
    List<EmailChangeRequest> findDue(@Param("status") NotificationStatus status,
                                     @Param("now") Instant now,
                                     Pageable pageable);

    /**
     * An account's other live requests. Asking for a second change supersedes
     * the first: leaving both redeemable would mean two links in two inboxes,
     * either of which could move the account, and the owner would have no way
     * to say which one they meant.
     */
    @Query("""
            select r from EmailChangeRequest r
            where r.userId = :userId and r.usedAt is null and r.id <> :exceptId
            """)
    List<EmailChangeRequest> findOtherLive(@Param("userId") Long userId,
                                           @Param("exceptId") Long exceptId);
}
