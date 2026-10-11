package uk.gov.dwp.engineering.recruitment.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps exceptions to RFC 9457 Problem Detail responses.
 *
 * <p>A request that breaks a business rule is the caller's fault, so it is a 400 Bad Request.
 * Malformed JSON, unknown ticket types and a missing body are already turned into 400 Problem
 * Details by {@link ResponseEntityExceptionHandler}.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

  static final String INVALID_BOOKING_TITLE = "Invalid booking request";

  @ExceptionHandler(InvalidBookingException.class)
  protected ProblemDetail handleInvalidBookingException(final InvalidBookingException ex) {
    final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
        ex.getMessage());
    problemDetail.setTitle(INVALID_BOOKING_TITLE);
    return problemDetail;
  }
}
