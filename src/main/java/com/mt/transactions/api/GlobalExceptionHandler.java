package com.mt.transactions.api;

import com.mt.transactions.domain.exception.ParentNotFoundException;
import com.mt.transactions.domain.exception.TransactionConflictException;
import com.mt.transactions.domain.exception.TransactionNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

/**
 * Maps errors to RFC 9457 Problem Details. Spring's base handler already covers
 * framework errors (type mismatch, unsupported media type, ...); {@code instance}
 * is filled with the request path by Spring when rendering the body.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(TransactionConflictException.class)
    ProblemDetail handleConflict(TransactionConflictException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** 422 rather than 404: the URI is valid, it is the body that references a missing resource. */
    @ExceptionHandler(ParentNotFoundException.class)
    ProblemDetail handleParentNotFound(ParentNotFoundException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    ProblemDetail handleNotFound(TransactionNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detail = ex.getBindingResult().getAllErrors().stream()
                .map(GlobalExceptionHandler::describe)
                .sorted()
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, detail));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest()
                .body(problem(HttpStatus.BAD_REQUEST, "Malformed JSON body or invalid field type"));
    }

    private static String describe(ObjectError error) {
        if (error instanceof FieldError fieldError && !fieldError.getField().equals("amountFinite")) {
            return fieldError.getField() + ": " + fieldError.getDefaultMessage();
        }
        return error.getDefaultMessage();
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        return problem;
    }
}
