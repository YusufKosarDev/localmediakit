package com.localmediakit.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface LeadNotificationRepository extends JpaRepository<LeadNotification, Long> {

    /**
     * The dispatch job's only read: work that is due, oldest first.
     *
     * <p>A row that has never been attempted is due regardless of the clock.
     * That is not a convenience -- nextAttemptAt exists to space out RETRIES, so
     * a row with no attempts behind it has no backoff to respect, and comparing
     * its timestamp to the current time is asking a question with no meaning.
     *
     * <p>It also removed a real intermittent failure. Enqueuing sets
     * nextAttemptAt to Instant.now() and the batch compares against
     * Instant.now() milliseconds later, so the window is effectively zero: if
     * the system clock steps backwards even slightly -- which it does on a host
     * running a hypervisor that resynchronises time -- a brand's message sat in
     * the outbox until the next tick for no reason anyone could see. It failed
     * about one run in three on such a machine, and never on the servers.
     */
    @Query("""
            select n from LeadNotification n
            where n.status = com.localmediakit.notification.NotificationStatus.PENDING
              and (n.attempts = 0 or n.nextAttemptAt <= :now)
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
