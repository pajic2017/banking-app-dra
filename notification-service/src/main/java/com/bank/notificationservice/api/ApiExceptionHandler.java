package com.bank.notificationservice.api;
import com.bank.notificationservice.events.InvalidEvent;
import com.mongodb.MongoException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(InvalidEvent.class) ProblemDetail invalid(InvalidEvent error) {return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,"Correct the event schema/version before replay.");}
    @ExceptionHandler(ConstraintViolationException.class) ProblemDetail parameter(ConstraintViolationException error) {return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Invalid pagination or notification parameter.");}
    @ExceptionHandler({MongoException.class,DataAccessException.class}) ProblemDetail unavailable(RuntimeException error) {return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,"Notification storage is unavailable.");}
}
