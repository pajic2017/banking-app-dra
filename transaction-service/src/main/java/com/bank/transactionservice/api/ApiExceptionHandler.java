package com.bank.transactionservice.api;
import com.bank.transactionservice.service.TransactionFailure;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(TransactionFailure.class)
    ProblemDetail failure(TransactionFailure error) { return problem(error.status(), error.getMessage()); }
    @ExceptionHandler({IllegalArgumentException.class,ConstraintViolationException.class})
    ProblemDetail invalid(RuntimeException error) {
        return problem(HttpStatus.BAD_REQUEST, error instanceof IllegalArgumentException ? error.getMessage() :
                "IDs must be positive; page >= 0, size 1–100, Idempotency-Key 1–100 ASCII letters/digits/._:-.");
    }
    @Override protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException error,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var body = problem(HttpStatus.BAD_REQUEST, "Correct invalid transaction fields.");
        var fields = new LinkedHashMap<String,String>();
        error.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(),f.getDefaultMessage()));
        body.setProperty("errors",fields);
        return handleExceptionInternal(error,body,headers,HttpStatus.BAD_REQUEST,request);
    }
    private ProblemDetail problem(HttpStatus status, String detail) {
        var body = ProblemDetail.forStatusAndDetail(status,detail); body.setTitle(status.getReasonPhrase()); return body;
    }
}
