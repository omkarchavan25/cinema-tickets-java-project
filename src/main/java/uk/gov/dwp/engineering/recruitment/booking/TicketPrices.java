package uk.gov.dwp.engineering.recruitment.booking;

import java.math.BigDecimal;
import uk.gov.dwp.engineering.recruitment.domain.TicketType;

/**
 * Price list for each {@link TicketType}, in GBP.
 *
 * <p>Prices are held as {@link BigDecimal} so that money is never subject to floating point
 * rounding. The switch expression is exhaustive, so adding a new {@link TicketType} without a
 * price is a compile-time error rather than a runtime surprise.
 */
public final class TicketPrices {

  public static final BigDecimal ADULT_PRICE = new BigDecimal("25.99");
  public static final BigDecimal CHILD_PRICE = new BigDecimal("17.50");
  public static final BigDecimal INFANT_PRICE = new BigDecimal("0.00");

  private TicketPrices() {
  }

  /**
   * Returns the price of a single ticket of the given type.
   *
   * @param type the ticket type, must not be null
   * @return the unit price in GBP
   */
  public static BigDecimal priceOf(final TicketType type) {
    return switch (type) {
      case ADULT -> ADULT_PRICE;
      case CHILD -> CHILD_PRICE;
      case INFANT -> INFANT_PRICE;
    };
  }
}
