package com.bank.customerservice.api;

import com.bank.customerservice.service.CustomerNotFoundException;
import com.bank.customerservice.service.AccountOverviewUnavailable;
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
    @ExceptionHandler(CustomerNotFoundException.class)
    ProblemDetail missing(CustomerNotFoundException error) {
        return problem(HttpStatus.NOT_FOUND, "Customer not found", error.getMessage());
    }

    @ExceptionHandler(AccountOverviewUnavailable.class)
    ProblemDetail overviewUnavailable(AccountOverviewUnavailable error) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE,"Account overview unavailable",error.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(DataIntegrityViolationException error) {
        // Do not leak SQL, database details or the customer's email into error responses.
        return problem(HttpStatus.CONFLICT, "Customer conflict", "Customer data conflicts with an existing record; email must be unique.");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrentUpdate(OptimisticLockingFailureException error) {
        return problem(HttpStatus.CONFLICT, "Concurrent update", "Customer was changed concurrently. Retrieve the current record and retry.");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail invalidParameter(ConstraintViolationException error) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid parameter", "IDs must be positive; page must be nonnegative and size must be between 1 and 100.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException error,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var body = problem(HttpStatus.BAD_REQUEST, "Validation failed", "Correct the invalid customer fields.");
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
