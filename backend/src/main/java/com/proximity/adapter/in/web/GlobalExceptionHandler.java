package com.proximity.adapter.in.web;

import com.proximity.application.dto.ErrorResponse;
import com.proximity.application.exception.BusinessNotFoundException;
import com.proximity.application.exception.BusinessOwnershipException;
import com.proximity.application.exception.DuplicateBusinessException;
import com.proximity.application.exception.DuplicateEmailException;
import com.proximity.application.exception.InvalidCredentialsException;
import com.proximity.application.exception.InvalidRadiusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(BusinessNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("BUSINESS_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(DuplicateBusinessException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateBusinessException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("DUPLICATE_BUSINESS", ex.getMessage()));
    }

    @ExceptionHandler(BusinessOwnershipException.class)
    public ResponseEntity<ErrorResponse> handleOwnership(BusinessOwnershipException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("FORBIDDEN", ex.getMessage()));
    }

    @ExceptionHandler(InvalidRadiusException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRadius(InvalidRadiusException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_RADIUS", ex.getMessage()));
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(DuplicateEmailException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("DUPLICATE_EMAIL", ex.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("INVALID_CREDENTIALS", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + ", " + b)
                .orElse("유효하지 않은 요청입니다");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_PARAMETER", message));
    }
}
