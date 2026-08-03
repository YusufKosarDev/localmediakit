package com.localmediakit.recovery;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PasswordResetNotificationRepository
        extends JpaRepository<PasswordResetNotification, Long> {

    /**
     * The dispatch job's only read: work that is due, oldest first.
     *
     * <p>A row with no attempts behind it is due regardless of the clock, the
     * same rule the lead outbox arrived at the hard way — {@code nextAttemptAt}
     * spaces out retries, so comparing it to the current time on a row that has
     * never been tried asks a question with no meaning, and loses to a clock
     * that steps backwards.
     */
    @Query("""
            select n from PasswordResetNotification n
            where n.status = com.localmediakit.notification.NotificationStatus.PENDING
              and (n.attempts = 0 or n.nextAttemptAt <= :now)
            order by n.nextAttemptAt asc, n.id asc""")
    List<PasswordResetNotification> findDue(@Param("now") Instant now, Pageable pageable);

    List<PasswordResetNotification> findByTokenId(Long tokenId);
}
