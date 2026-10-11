package uk.gov.dwp.engineering.recruitment.booking;

import uk.gov.dwp.engineering.recruitment.domain.TicketRequest;
import uk.gov.dwp.engineering.recruitment.exception.InvalidBookingException;

/**
 * Applies the cinema's business rules to a purchase request.
 *
 * <p>All validation happens before any call to a third-party service, so an invalid request
 * never results in a payment or a seat reservation.
 */
public final class TicketPurchaseValidator {

  /** The maximum number of tickets (of any type, including infants) in one purchase. */
  public static final int MAX_TICKETS_PER_PURCHASE = 25;

  static final String ACCOUNT_ID_MISSING = "Account id must be provided";
  static final String ACCOUNT_ID_INVALID = "Account id must be greater than zero";
  static final String NO_TICKET_REQUESTS = "At least one ticket request must be provided";
  static final String NULL_TICKET_REQUEST = "Ticket requests must not contain null entries";
  static final String TICKET_TYPE_MISSING = "Ticket type must be provided for every ticket request";
  static final String NEGATIVE_TICKET_COUNT = "Ticket count must not be negative";
  static final String NO_TICKETS = "At least one ticket must be purchased";
  static final String TOO_MANY_TICKETS =
      "A maximum of " + MAX_TICKETS_PER_PURCHASE + " tickets can be purchased at a time";
  static final String ADULT_REQUIRED =
      "Child and infant tickets cannot be purchased without an adult ticket";
  static final String TOO_MANY_INFANTS =
      "Each infant must sit on an adult's lap, so infants cannot outnumber adults";

  /**
   * Validates the request and, if it is valid, returns the combined order.
   *
   * @param accountId      the purchaser's account id
   * @param ticketRequests the requested tickets
   * @return the combined ticket order
   * @throws InvalidBookingException if any business rule is broken
   */
  public TicketOrder validate(final Long accountId, final TicketRequest... ticketRequests) {
    validateAccountId(accountId);
    final TicketOrder order = combine(ticketRequests);
    validateOrder(order);
    return order;
  }

  private static void validateAccountId(final Long accountId) {
    if (accountId == null) {
      throw new InvalidBookingException(ACCOUNT_ID_MISSING);
    }
    if (accountId <= 0) {
      throw new InvalidBookingException(ACCOUNT_ID_INVALID);
    }
  }

  private static TicketOrder combine(final TicketRequest... ticketRequests) {
    if (ticketRequests == null || ticketRequests.length == 0) {
      throw new InvalidBookingException(NO_TICKET_REQUESTS);
    }

    // long rather than int, so that summing many large counts cannot overflow
    long adults = 0;
    long children = 0;
    long infants = 0;

    for (final TicketRequest request : ticketRequests) {
      validateLine(request);
      switch (request.type()) {
        case ADULT -> adults += request.ticketCount();
        case CHILD -> children += request.ticketCount();
        case INFANT -> infants += request.ticketCount();
      }
    }
    return new TicketOrder(adults, children, infants);
  }

  private static void validateLine(final TicketRequest request) {
    if (request == null) {
      throw new InvalidBookingException(NULL_TICKET_REQUEST);
    }
    if (request.type() == null) {
      throw new InvalidBookingException(TICKET_TYPE_MISSING);
    }
    if (request.ticketCount() < 0) {
      throw new InvalidBookingException(NEGATIVE_TICKET_COUNT);
    }
  }

  private static void validateOrder(final TicketOrder order) {
    if (order.totalTickets() == 0) {
      throw new InvalidBookingException(NO_TICKETS);
    }
    if (order.totalTickets() > MAX_TICKETS_PER_PURCHASE) {
      throw new InvalidBookingException(TOO_MANY_TICKETS);
    }
    if (order.adults() == 0) {
      throw new InvalidBookingException(ADULT_REQUIRED);
    }
    if (order.infants() > order.adults()) {
      throw new InvalidBookingException(TOO_MANY_INFANTS);
    }
  }
}
