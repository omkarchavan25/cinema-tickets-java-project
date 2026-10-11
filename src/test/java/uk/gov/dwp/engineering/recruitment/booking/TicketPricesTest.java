package uk.gov.dwp.engineering.recruitment.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.gov.dwp.engineering.recruitment.domain.TicketType;

class TicketPricesTest {

  @ParameterizedTest
  @CsvSource({"ADULT, 25.99", "CHILD, 17.50", "INFANT, 0.00"})
  void givenTicketType_whenPriceOf_thenPriceFromPriceTableReturned(final TicketType type,
      final String expectedPrice) {
    assertEquals(new BigDecimal(expectedPrice), TicketPrices.priceOf(type));
  }
}
