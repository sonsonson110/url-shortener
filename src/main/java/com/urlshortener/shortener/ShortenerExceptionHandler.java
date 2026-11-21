package com.urlshortener.shortener;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps domain exceptions to RFC 7807 {@link ProblemDetail} responses. Extending
 * {@link ResponseEntityExceptionHandler} also turns standard Spring errors (bad JSON, failed
 * {@code @Valid}) into ProblemDetail with the right status.
 */
@RestControllerAdvice
public class ShortenerExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(InvalidUrlException.class)
    ProblemDetail handleInvalidUrl(InvalidUrlException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(LinkNotFoundException.class)
    ProblemDetail handleNotFound(LinkNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(LinkExpiredException.class)
    ProblemDetail handleExpired(LinkExpiredException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.GONE, ex.getMessage());
    }
}
