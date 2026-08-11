package com.qualitywebsite.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Inspects exception hierarchy and messages to accurately determine if an exception
     * represents a client socket disconnect/abort condition.
     */
    public static boolean isClientAbortException(Throwable ex) {
        if (ex == null) return false;
        Throwable current = ex;
        while (current != null) {
            String className = current.getClass().getName();
            String msg = current.getMessage() != null ? current.getMessage().toLowerCase() : "";

            if (className.contains("ClientAbortException") ||
                className.contains("AsyncRequestNotUsableException") ||
                className.contains("AsyncRequestTimeoutException")) {
                return true;
            }

            if (current instanceof IOException) {
                if (msg.contains("established connection was aborted") ||
                    msg.contains("broken pipe") ||
                    msg.contains("connection reset") ||
                    msg.contains("software caused connection abort") ||
                    msg.contains("connection was closed") ||
                    msg.contains("client disconnected") ||
                    msg.contains("stream closed")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private void logClientAbort(Throwable ex, HttpServletRequest request) {
        String method = request != null ? request.getMethod() : "UNKNOWN";
        String uri = request != null ? request.getRequestURI() : "UNKNOWN";
        String query = (request != null && request.getQueryString() != null && !request.getQueryString().isBlank())
                ? "?" + request.getQueryString() : "";

        log.warn("[Client Abort] Client disconnected while writing response: {} {}{}", method, uri, query);
    }

    @ExceptionHandler({
        AsyncRequestNotUsableException.class,
        AsyncRequestTimeoutException.class,
        IOException.class
    })
    public ResponseEntity<Map<String, Object>> handleClientAbort(
            Exception ex, HttpServletRequest request) {

        if (isClientAbortException(ex)) {
            logClientAbort(ex, request);
            return null; // Return null so Spring MVC does not attempt to serialize a JSON error body to an aborted socket
        }

        return handleAllExceptions(ex, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 400);
        response.put("error", "Bad Request");
        response.put("success", false);
        response.put("message", "Validation failed. Please correct the highlighted fields and try again.");
        response.put("errors", errors);
        response.put("timestamp", Instant.now().toString());

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("[GlobalExceptionHandler] Bad Request (400): {}", ex.getMessage());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 400);
        response.put("error", "Bad Request");
        response.put("success", false);
        response.put("message", ex.getMessage());
        response.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex) {
        log.warn("[GlobalExceptionHandler] Unauthorized (401): {}", ex.getMessage());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 401);
        response.put("error", "Unauthorized");
        response.put("success", false);
        response.put("message", "Authentication required or invalid credentials.");
        response.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("[GlobalExceptionHandler] Forbidden (403): {}", ex.getMessage());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 403);
        response.put("error", "Forbidden");
        response.put("success", false);
        response.put("message", "Access denied. You do not have permission to perform this action.");
        response.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoSuchElementException ex) {
        log.warn("[GlobalExceptionHandler] Resource Not Found (404): {}", ex.getMessage());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 404);
        response.put("error", "Not Found");
        response.put("success", false);
        response.put("message", ex.getMessage() != null ? ex.getMessage() : "Requested resource was not found.");
        response.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest request) {
        String uri = request != null ? request.getRequestURI() : "";
        log.warn("[GlobalExceptionHandler] Static Resource Not Found (404): {}", uri);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 404);
        response.put("error", "Not Found");
        response.put("success", false);
        response.put("message", "Static resource not found: " + uri);
        response.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(DocumentConflictException.class)
    public ResponseEntity<Map<String, Object>> handleDocumentConflict(
            DocumentConflictException ex) {
        log.warn("[GlobalExceptionHandler] Document Conflict (409): {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 409);
        body.put("error", "Conflict");
        body.put("success", false);
        body.put("message", ex.getMessage());
        body.put("documentMasterId", ex.getDocumentMasterId());
        body.put("latestEntityVersion", ex.getLatestEntityVersion());
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class,
                       OptimisticLockingFailureException.class})
    public ResponseEntity<Map<String, Object>> handleJpaOptimisticLock(
            Exception ex) {
        log.warn("[GlobalExceptionHandler] JPA Optimistic Lock Failure (409): {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 409);
        body.put("error", "Conflict");
        body.put("success", false);
        body.put("message",
                "This document has been modified by another administrator. " +
                "Please refresh the page and try again.");
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAllExceptions(Exception ex, HttpServletRequest request) {
        if (isClientAbortException(ex)) {
            logClientAbort(ex, request);
            return null;
        }

        String method = request != null ? request.getMethod() : "UNKNOWN";
        String uri = request != null ? request.getRequestURI() : "UNKNOWN";
        log.error("[GlobalExceptionHandler] Internal Server Error (500) at {} {}: {}", method, uri, ex.getMessage(), ex);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 500);
        response.put("error", "Internal Server Error");
        response.put("success", false);
        response.put("message", "An unexpected error occurred. Please try again.");
        response.put("timestamp", Instant.now().toString());

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}