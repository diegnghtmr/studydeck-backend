package com.studydeck.infrastructure.adapter.in.web;

import com.studydeck.application.exception.ForbiddenException;
import com.studydeck.application.exception.NotFoundException;
import com.studydeck.domain.exception.DomainValidationException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler producing RFC 9457 Problem Details (application/problem+json).
 *
 * <p>Error mappings:
 *
 * <ul>
 *   <li>{@link NotFoundException} → 404
 *   <li>{@link ForbiddenException} → 403
 *   <li>{@link DomainValidationException} → 422
 *   <li>{@link MethodArgumentNotValidException} → 400 with field violations
 *   <li>{@link HttpMessageNotReadableException} (includes unknown fields) → 400
 *   <li>{@link IllegalArgumentException} → 400
 * </ul>
 */
@RestControllerAdvice
class GlobalExceptionHandler {

  private static final String PROBLEM_BASE = "https://studydeck.ai/errors";

  @ExceptionHandler(NotFoundException.class)
  ResponseEntity<ProblemDetail> handleNotFound(NotFoundException ex, HttpServletRequest request) {
    var pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    pd.setTitle("Not Found");
    pd.setType(URI.create(PROBLEM_BASE + "/not-found"));
    pd.setInstance(URI.create(request.getRequestURI()));
    pd.setProperty("resourceType", ex.getResourceType());
    pd.setProperty("resourceId", ex.getResourceId());
    return problemResponse(pd);
  }

  @ExceptionHandler(ForbiddenException.class)
  ResponseEntity<ProblemDetail> handleForbidden(ForbiddenException ex, HttpServletRequest request) {
    var pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    pd.setTitle("Forbidden");
    pd.setType(URI.create(PROBLEM_BASE + "/forbidden"));
    pd.setInstance(URI.create(request.getRequestURI()));
    return problemResponse(pd);
  }

  @ExceptionHandler(DomainValidationException.class)
  ResponseEntity<ProblemDetail> handleDomainValidation(
      DomainValidationException ex, HttpServletRequest request) {
    var pd = ProblemDetail.forStatusAndDetail(HttpStatus.resolve(422), ex.getMessage());
    pd.setTitle("Domain Validation Error");
    pd.setType(URI.create(PROBLEM_BASE + "/domain-validation"));
    pd.setInstance(URI.create(request.getRequestURI()));
    return problemResponse(pd);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    var pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    pd.setTitle("Validation Failed");
    pd.setDetail("One or more fields failed validation");
    pd.setType(URI.create(PROBLEM_BASE + "/validation"));
    pd.setInstance(URI.create(request.getRequestURI()));

    List<Map<String, String>> violations =
        ex.getBindingResult().getAllErrors().stream()
            .map(
                error -> {
                  String field =
                      (error instanceof FieldError fe) ? fe.getField() : error.getObjectName();
                  String message = error.getDefaultMessage();
                  return Map.of("field", field, "message", message != null ? message : "invalid");
                })
            .toList();
    pd.setProperty("violations", violations);
    return problemResponse(pd);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ProblemDetail> handleUnreadable(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    var pd =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Request body is not readable or contains unknown fields");
    pd.setTitle("Bad Request");
    pd.setType(URI.create(PROBLEM_BASE + "/bad-request"));
    pd.setInstance(URI.create(request.getRequestURI()));
    return problemResponse(pd);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ProblemDetail> handleIllegalArgument(
      IllegalArgumentException ex, HttpServletRequest request) {
    var pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    pd.setTitle("Bad Request");
    pd.setType(URI.create(PROBLEM_BASE + "/bad-request"));
    pd.setInstance(URI.create(request.getRequestURI()));
    return problemResponse(pd);
  }

  private ResponseEntity<ProblemDetail> problemResponse(ProblemDetail pd) {
    return ResponseEntity.status(pd.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(pd);
  }
}
