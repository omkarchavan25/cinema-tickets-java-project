package uk.gov.dwp.engineering.recruitment.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BookingConfirmationTest {

  @Test
  void givenBookingConfirmation_whenGetAccount_thenExpectedValueReturned() {
    final BookingConfirmation bookingConfirmation = new BookingConfirmation(99L);
    assertEquals(99L, bookingConfirmation.accountId());
  }

  @Test
  void givenFullBookingConfirmation_whenGetFields_thenExpectedValuesReturned() {
    final BookingConfirmation bookingConfirmation =
        new BookingConfirmation(99L, 3L, new BigDecimal("69.48"));
    assertEquals(99L, bookingConfirmation.accountId());
    assertEquals(3L, bookingConfirmation.seatCount());
    assertEquals(new BigDecimal("69.48"), bookingConfirmation.totalCost());
  }
}
