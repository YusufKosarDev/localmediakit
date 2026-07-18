package com.localmediakit.shared;

import com.localmediakit.auth.EmailAlreadyUsedException;
import com.localmediakit.auth.InvalidCredentialsException;
import com.localmediakit.mediakit.MediaKitNotFoundException;
import com.localmediakit.mediakit.ReservedSlugException;
import com.localmediakit.mediakit.VersionNotFoundException;
import com.localmediakit.stats.InvalidDemographicsException;
import com.localmediakit.user.PlanLimitExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * Unparseable bodies (bad JSON, unknown enum values like an unsupported
     * platform) get a direct 400. Without this handler the container's error
     * dispatch would kick in and Spring Security would mask the status as 401.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return body(HttpStatus.BAD_REQUEST, "Malformed request body", null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return body(HttpStatus.BAD_REQUEST, "Validation failed", fieldErrors);
    }

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ResponseEntity<Map<String, Object>> handleEmailTaken(EmailAlreadyUsedException ex) {
        return body(HttpStatus.CONFLICT, ex.getMessage(), null);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(InvalidCredentialsException ex) {
        return body(HttpStatus.UNAUTHORIZED, ex.getMessage(), null);
    }

    @ExceptionHandler(ReservedSlugException.class)
    public ResponseEntity<Map<String, Object>> handleReservedSlug(ReservedSlugException ex) {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
    }

    @ExceptionHandler(PlanLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handlePlanLimit(PlanLimitExceededException ex) {
        return body(HttpStatus.FORBIDDEN, ex.getMessage(), null);
    }

    @ExceptionHandler(MediaKitNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleMediaKitNotFound(MediaKitNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    @ExceptionHandler(InvalidDemographicsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidDemographics(InvalidDemographicsException ex) {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
    }

    @ExceptionHandler(VersionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleVersionNotFound(VersionNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message, Object details) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status.value());
        payload.put("error", message);
        if (details != null) {
            payload.put("details", details);
        }
        return ResponseEntity.status(status).body(payload);
    }
}
