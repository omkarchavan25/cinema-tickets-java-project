package uk.gov.dwp.engineering.recruitment;

import org.springframework.stereotype.Service;
import uk.gov.dwp.engineering.recruitment.booking.TicketOrder;
import uk.gov.dwp.engineering.recruitment.booking.TicketPurchaseValidator;
import uk.gov.dwp.engineering.recruitment.domain.BookingConfirmation;
import uk.gov.dwp.engineering.recruitment.domain.TicketRequest;
import uk.gov.dwp.engineering.recruitment.exception.InvalidBookingException;
import uk.gov.dwp.engineering.recruitment.thirdparty.PaymentService;
import uk.gov.dwp.engineering.recruitment.thirdparty.SeatReservationService;

/**
 * Purchases cinema tickets.
 *
 * <p>The flow is: validate the whole request, work out the cost and seats, take payment, then
 * reserve seats. Validation comes first so that nothing is charged or reserved for an invalid
 * request. Payment comes before reservation so that seats are never held for an unpaid booking.
 */
@Service
public class CinemaTicketsServiceImpl implements CinemaTicketsService {

  private final PaymentService paymentService;

  private final SeatReservationService seatReservationService;

  private final TicketPurchaseValidator validator = new TicketPurchaseValidator();

  public CinemaTicketsServiceImpl(PaymentService paymentService,
      SeatReservationService seatReservationService) {
    this.paymentService = paymentService;
    this.seatReservationService = seatReservationService;
  }

  @Override
  public BookingConfirmation purchaseTickets(final Long accountId,
      final TicketRequest... ticketRequests)
      throws InvalidBookingException {

    final TicketOrder order = validator.validate(accountId, ticketRequests);

    paymentService.debitAccount(accountId, order.totalCost());
    seatReservationService.reserveSeats(accountId, order.seatsToReserve());

    return new BookingConfirmation(accountId, order.seatsToReserve(), order.totalCost());
  }
}
