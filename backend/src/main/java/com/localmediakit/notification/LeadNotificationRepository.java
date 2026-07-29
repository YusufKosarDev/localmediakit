package com.localmediakit.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface LeadNotificationRepository extends JpaRepository<LeadNotification, Long> {

    /** The dispatch job's only read: work that is due, oldest first. */
    @Query("""
            select n from LeadNotification n
            where n.status = com.localmediakit.notification.NotificationStatus.PENDING
              and n.nextAttemptAt <= :now
            order by n.nextAttemptAt asc, n.id asc""")
    List<LeadNotification> findDue(@Param("now") Instant now, Pageable pageable);

    /**
     * Backs the per-owner hourly cap. Counts everything that was queued —
     * including suppressed rows — so a burst cannot be extended by the very
     * suppressions it caused.
     */
    long countByRecipientEmailAndCreatedAtAfter(String recipientEmail, Instant after);

    List<LeadNotification> findByLeadId(Long leadId);
}
