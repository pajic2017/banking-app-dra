package com.bank.accountservice.api;

import com.bank.accountservice.service.AccountFailure;
import com.bank.accountservice.domain.AccountRuleViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(AccountFailure.class)
    ProblemDetail missing(AccountFailure error) {
        return problem(error.status(), error.status().getReasonPhrase(), error.getMessage());
    }

    @ExceptionHandler(AccountRuleViolation.class)
    ProblemDetail rule(AccountRuleViolation error) {
        return problem(HttpStatus.CONFLICT,"Account status conflict",error.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(DataIntegrityViolationException error) {
        // Do not leak SQL, database details or account data into error responses.
        return problem(HttpStatus.CONFLICT, "Account conflict", "Account data conflicts with an existing record; account numbers must be unique.");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrentUpdate(OptimisticLockingFailureException error) {
        return problem(HttpStatus.CONFLICT, "Concurrent update", "Account was changed concurrently. Retrieve the current record and retry.");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail invalidParameter(ConstraintViolationException error) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid parameter", "IDs must be positive; page must be nonnegative and size must be between 1 and 100.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException error,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var body = problem(HttpStatus.BAD_REQUEST, "Validation failed", "Correct the invalid account fields.");
        var errors = new LinkedHashMap<String, String>();
        error.getBindingResult().getFieldErrors().forEach(field -> errors.putIfAbsent(field.getField(), field.getDefaultMessage()));
        body.setProperty("errors", errors);
        return handleExceptionInternal(error, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        var result = ProblemDetail.forStatusAndDetail(status, detail);
        result.setTitle(title);
        return result;
    }
}
