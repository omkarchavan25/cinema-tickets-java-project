package uk.gov.dwp.engineering.recruitment.domain;

import java.math.BigDecimal;

/**
 * Confirmation of a successful booking.
 *
 * @param accountId the account that was charged
 * @param seatCount the number of seats reserved
 * @param totalCost the total amount charged, in GBP
 */
public record BookingConfirmation(Long accountId, Long seatCount, BigDecimal totalCost) {

  public BookingConfirmation(Long accountId) {
    this(accountId, 0L, BigDecimal.ZERO);
  }
}
