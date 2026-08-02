package com.localmediakit.shared;

import com.localmediakit.analytics.ShareLinkNotFoundException;
import com.localmediakit.analytics.TooManyShareLinksException;
import com.localmediakit.auth.EmailAlreadyUsedException;
import com.localmediakit.billing.AlreadyProException;
import com.localmediakit.billing.BillingNotConfiguredException;
import com.localmediakit.billing.DemoUpgradeDisabledException;
import com.localmediakit.billing.InvalidWebhookSignatureException;
import com.localmediakit.collab.CollaborationNotFoundException;
import com.localmediakit.lead.LeadNotFoundException;
import com.localmediakit.media.MediaItemNotFoundException;
import com.localmediakit.recovery.InvalidResetTokenException;
import com.localmediakit.media.TooManyMediaItemsException;
import com.localmediakit.ratecard.RateCardItemNotFoundException;
import com.localmediakit.domain.DomainAlreadyExistsException;
import com.localmediakit.domain.DomainNotFoundException;
import com.localmediakit.domain.InvalidDomainException;
import com.localmediakit.auth.InvalidCredentialsException;
import com.localmediakit.mediakit.InvalidAppearanceException;
import com.localmediakit.mediakit.InvalidKitPasswordException;
import com.localmediakit.mediakit.MediaKitNotFoundException;
import com.localmediakit.mediakit.ReservedSlugException;
import com.localmediakit.mediakit.TooManyUnlockAttemptsException;
import com.localmediakit.mediakit.VersionNotFoundException;
import com.localmediakit.mediakit.VersionNotVisibleException;
import com.localmediakit.stats.InvalidDemographicsException;
import com.localmediakit.stats.sync.ExternalAccountNotFoundException;
import com.localmediakit.stats.sync.SyncCooldownException;
import com.localmediakit.stats.sync.SyncNotConfiguredException;
import com.localmediakit.stats.sync.SyncSourceNotFoundException;
import com.localmediakit.stats.sync.SyncUpstreamException;
import com.localmediakit.user.PlanLimitExceededException;
import com.localmediakit.user.ProtectedAccountException;
import com.localmediakit.shared.UnsupportedLocaleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * Unparseable bodies (bad JSON, unknown enum values like an unsupported
     * platform) get a direct 400. Without this handler the container's error
     * dispatch would kick in and Spring Security would mask the status as 401.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return body(HttpStatus.BAD_REQUEST, "MALFORMED_BODY", "Malformed request body", null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Validation failed", fieldErrors);
    }

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ResponseEntity<Map<String, Object>> handleEmailTaken(EmailAlreadyUsedException ex) {
        return body(HttpStatus.CONFLICT, codeFor(ex), ex.getMessage(), null);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(InvalidCredentialsException ex) {
        return body(HttpStatus.UNAUTHORIZED, codeFor(ex), ex.getMessage(), null);
    }

    /** Appearance value outside the curated set. */
    @ExceptionHandler(UnsupportedLocaleException.class)
    public ResponseEntity<Map<String, Object>> handleLocale(UnsupportedLocaleException ex) {
        return body(HttpStatus.BAD_REQUEST, codeFor(ex), ex.getMessage(), null);
    }

    @ExceptionHandler(InvalidAppearanceException.class)
    public ResponseEntity<Map<String, Object>> handleAppearance(InvalidAppearanceException ex) {
        return body(HttpStatus.BAD_REQUEST, codeFor(ex), ex.getMessage(), null);
    }

    @ExceptionHandler(ReservedSlugException.class)
    public ResponseEntity<Map<String, Object>> handleReservedSlug(ReservedSlugException ex) {
        return body(HttpStatus.BAD_REQUEST, codeFor(ex), ex.getMessage(), null);
    }

    @ExceptionHandler(PlanLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handlePlanLimit(PlanLimitExceededException ex) {
        return body(HttpStatus.FORBIDDEN, codeFor(ex), ex.getMessage(), null);
    }

    /** Destructive settings op aimed at the shared demo account. */
    @ExceptionHandler(ProtectedAccountException.class)
    public ResponseEntity<Map<String, Object>> handleProtectedAccount(ProtectedAccountException ex) {
        return body(HttpStatus.FORBIDDEN, codeFor(ex), ex.getMessage(), null);
    }

    @ExceptionHandler(MediaKitNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleMediaKitNotFound(MediaKitNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, codeFor(ex), ex.getMessage(), null);
    }

    @ExceptionHandler(InvalidDemographicsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidDemographics(InvalidDemographicsException ex) {
        return body(HttpStatus.BAD_REQUEST, codeFor(ex), ex.getMessage(), null);
    }

    @ExceptionHandler(InvalidWebhookSignatureException.class)
    public ResponseEntity<Map<String, Object>> handleBadWebhookSignature(InvalidWebhookSignatureException ex) {
        return body(HttpStatus.BAD_REQUEST, codeFor(ex), ex.getMessage(), null);
    }

    @ExceptionHandler(BillingNotConfiguredException.class)
    public ResponseEntity<Map<String, Object>> handleBillingNotConfigured(BillingNotConfiguredException ex) {
        return body(HttpStatus.SERVICE_UNAVAILABLE, codeFor(ex), ex.getMessage(), null);
    }

    @ExceptionHandler(DemoUpgradeDisabledException.class)
    public ResponseEntity<Map<String, Object>> handleDemoUpgradeDisabled(DemoUpgradeDisabledException ex) {
        return body(HttpStatus.FORBIDDEN, codeFor(ex), ex.getMessage(), null);
    }

    @ExceptionHandler(AlreadyProException.class)
    public ResponseEntity<Map<String, Object>> handleAlreadyPro(AlreadyProException ex) {
        return body(HttpStatus.CONFLICT, codeFor(ex), ex.getMessage(), null);
    }

    @ExceptionHandler(CollaborationNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleCollabNotFound(CollaborationNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, codeFor(ex), ex.getMessage(), null);
    }

    @ExceptionHandler(RateCardItemNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleRateCardItemNotFound(RateCardItemNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, codeFor(ex), ex.getMessage(), null);
    }

    @ExceptionHandler(LeadNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleLeadNotFound(LeadNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, codeFor(ex), ex.getMessage(), null);
    }

    @ExceptionHandler(DomainNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleDomainNotFound(DomainNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, codeFor(ex), ex.getMessage(), null);
    }

    @ExceptionHandler(DomainAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleDomainExists(DomainAlreadyExistsException ex) {
        return body(HttpStatus.CONFLICT, codeFor(ex), ex.getMessage(), null);
    }

    @ExceptionHandler(InvalidDomainException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidDomain(InvalidDomainException ex) {
        return body(HttpStatus.BAD_REQUEST, codeFor(ex), ex.getMessage(), null);
    }

    @ExceptionHandler(VersionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleVersionNotFound(VersionNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, codeFor(ex), ex.getMessage(), null);
    }

    @ExceptionHandler(VersionNotVisibleException.class)
    public ResponseEntity<Map<String, Object>> handleVersionNotVisible(VersionNotVisibleException ex) {
        return body(HttpStatus.FORBIDDEN, codeFor(ex), ex.getMessage(), null);
    }

    @ExceptionHandler(InvalidKitPasswordException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidKitPassword(InvalidKitPasswordException ex) {
        return body(HttpStatus.UNAUTHORIZED, codeFor(ex), ex.getMessage(), null);
    }

    @ExceptionHandler(TooManyUnlockAttemptsException.class)
    public ResponseEntity<Map<String, Object>> handleTooManyUnlock(TooManyUnlockAttemptsException ex) {
        return body(HttpStatus.TOO_MANY_REQUESTS, codeFor(ex), ex.getMessage(), null);
    }

    @ExceptionHandler(SyncNotConfiguredException.class)
    public ResponseEntity<Map<String, Object>> handleSyncNotConfigured(SyncNotConfiguredException ex) {
        return body(HttpStatus.SERVICE_UNAVAILABLE, codeFor(ex), ex.getMessage(), null);
    }

    @ExceptionHandler(SyncSourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleSyncSourceNotFound(SyncSourceNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, codeFor(ex), ex.getMessage(), null);
    }

    @ExceptionHandler(ExternalAccountNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleExternalAccountNotFound(ExternalAccountNotFoundException ex) {
        return body(HttpStatus.BAD_REQUEST, codeFor(ex), ex.getMessage(), null);
    }

    @ExceptionHandler(SyncUpstreamException.class)
    public ResponseEntity<Map<String, Object>> handleSyncUpstream(SyncUpstreamException ex) {
        return body(HttpStatus.BAD_GATEWAY, codeFor(ex), ex.getMessage(), null);
    }

    @ExceptionHandler(SyncCooldownException.class)
    public ResponseEntity<Map<String, Object>> handleSyncCooldown(SyncCooldownException ex) {
        return body(HttpStatus.TOO_MANY_REQUESTS, codeFor(ex), ex.getMessage(), null);
    }

    /** 400 rather than 404: which of the three reasons it failed is not said. */
    @ExceptionHandler(InvalidResetTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidResetToken(InvalidResetTokenException ex) {
        return body(HttpStatus.BAD_REQUEST, codeFor(ex), ex.getMessage(), null);
    }

    @ExceptionHandler(MediaItemNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleMediaItemNotFound(MediaItemNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, codeFor(ex), ex.getMessage(), null);
    }

    @ExceptionHandler(TooManyMediaItemsException.class)
    public ResponseEntity<Map<String, Object>> handleTooManyMediaItems(TooManyMediaItemsException ex) {
        return body(HttpStatus.CONFLICT, codeFor(ex), ex.getMessage(), null);
    }

    @ExceptionHandler(ShareLinkNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleShareLinkNotFound(ShareLinkNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, codeFor(ex), ex.getMessage(), null);
    }

    @ExceptionHandler(TooManyShareLinksException.class)
    public ResponseEntity<Map<String, Object>> handleTooManyShareLinks(TooManyShareLinksException ex) {
        return body(HttpStatus.CONFLICT, codeFor(ex), ex.getMessage(), null);
    }

    /**
     * A write the database refused as a duplicate.
     *
     * <p>{@link ConstraintRetry} absorbs the racing case, so anything arriving
     * here is a collision that did not clear on a second look -- a value that is
     * genuinely taken. That is the caller's situation to resolve, not a fault of
     * this server, and 409 says so where the previous 500 blamed the wrong side
     * and told an operator to go looking for a bug that is not there.
     *
     * <p>The message is deliberately generic: the constraint name and the
     * offending value are in the exception, and putting either in a response
     * tells a caller about rows they cannot see.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {
        log.warn("Write rejected as a duplicate after retries: {}", ex.getMostSpecificCause().getMessage());
        return body(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION",
                "That value is already taken. Please try a different one.", null);
    }

    /**
     * Normalise explicit ResponseStatusException (e.g. 401 unknown user) into
     * the same {status, error} shape as the rest of the API. Left as a specific
     * type on purpose — a broad Exception catch-all would swallow Spring's own
     * 404/405 handling and mask it as 500. Stack-trace/message leakage is
     * prevented by server.error.include-* in application.yml instead.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        return body(HttpStatus.valueOf(ex.getStatusCode().value()), ex.getReason(), null);
    }

    /** ExampleNotFoundException -> EXAMPLE_NOT_FOUND. */
    static String codeFor(Throwable ex) {
        String name = ex.getClass().getSimpleName().replaceAll("Exception$", "");
        return name.replaceAll("(?<=[a-z0-9])(?=[A-Z])", "_").toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * Adds a stable machine code beside the human message so the frontend can
     * translate without the backend having to know a presentation language.
     *
     * <p>The code is derived from the exception class name rather than written
     * out per handler: a new exception gets one automatically, and there is no
     * second list to keep in step. Renaming a class would change its code, so
     * the ones the frontend actually translates are pinned by
     * ApiErrorCodeTest. Anything the client does not recognise falls back to
     * the message below, which is why this stays additive.
     */
    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message, Object details) {
        return body(status, null, message, details);
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String code,
                                                     String message, Object details) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status.value());
        if (code != null) {
            payload.put("code", code);
        }
        payload.put("error", message);
        if (details != null) {
            payload.put("details", details);
        }
        return ResponseEntity.status(status).body(payload);
    }
}
