package uk.gov.dwp.engineering.recruitment.booking;

import java.math.BigDecimal;
import uk.gov.dwp.engineering.recruitment.domain.TicketType;

/**
 * The total number of tickets of each type in a purchase, after all ticket request lines have
 * been combined (e.g. two separate "ADULT x 1" lines become two adults).
 *
 * <p>This is an immutable value object that knows how many seats the order needs and what it
 * costs. It does not apply business rules; that is the job of {@link TicketPurchaseValidator}.
 *
 * @param adults   number of adult tickets
 * @param children number of child tickets
 * @param infants  number of infant tickets
 */
public record TicketOrder(long adults, long children, long infants) {

  /**
   * Total number of tickets, including infants. Infants are tickets even though they are free
   * and do not take a seat.
   */
  public long totalTickets() {
    return adults + children + infants;
  }

  /**
   * Number of seats to reserve. Infants sit on an adult's lap, so they are not given a seat.
   */
  public long seatsToReserve() {
    return adults + children;
  }

  /**
   * Total cost of the order in GBP.
   */
  public BigDecimal totalCost() {
    return cost(TicketType.ADULT, adults)
        .add(cost(TicketType.CHILD, children))
        .add(cost(TicketType.INFANT, infants));
  }

  private static BigDecimal cost(final TicketType type, final long count) {
    return TicketPrices.priceOf(type).multiply(BigDecimal.valueOf(count));
  }
}
