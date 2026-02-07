package com.proximity.adapter.in.web;

import com.proximity.application.exception.BusinessNotFoundException;
import com.proximity.application.exception.BusinessOwnershipException;
import com.proximity.application.exception.DuplicateBusinessException;
import com.proximity.application.exception.DuplicateEmailException;
import com.proximity.application.exception.InvalidCredentialsException;
import com.proximity.application.exception.InvalidRadiusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String CODE_PROPERTY = "code";

    @ExceptionHandler(BusinessNotFoundException.class)
    public ProblemDetail handleNotFound(BusinessNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("사업장을 찾을 수 없습니다");
        problem.setProperty(CODE_PROPERTY, "BUSINESS_NOT_FOUND");
        return problem;
    }

    @ExceptionHandler(DuplicateBusinessException.class)
    public ProblemDetail handleDuplicate(DuplicateBusinessException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("중복된 사업장");
        problem.setProperty(CODE_PROPERTY, "DUPLICATE_BUSINESS");
        return problem;
    }

    @ExceptionHandler(BusinessOwnershipException.class)
    public ProblemDetail handleOwnership(BusinessOwnershipException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("권한 없음");
        problem.setProperty(CODE_PROPERTY, "FORBIDDEN");
        return problem;
    }

    @ExceptionHandler(InvalidRadiusException.class)
    public ProblemDetail handleInvalidRadius(InvalidRadiusException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("유효하지 않은 검색 반경");
        problem.setProperty(CODE_PROPERTY, "INVALID_RADIUS");
        return problem;
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ProblemDetail handleDuplicateEmail(DuplicateEmailException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("중복된 이메일");
        problem.setProperty(CODE_PROPERTY, "DUPLICATE_EMAIL");
        return problem;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("인증 실패");
        problem.setProperty(CODE_PROPERTY, "INVALID_CREDENTIALS");
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + ", " + b)
                .orElse("유효하지 않은 요청입니다");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
        problem.setTitle("유효성 검증 실패");
        problem.setProperty(CODE_PROPERTY, "INVALID_PARAMETER");
        return problem;
    }
}
