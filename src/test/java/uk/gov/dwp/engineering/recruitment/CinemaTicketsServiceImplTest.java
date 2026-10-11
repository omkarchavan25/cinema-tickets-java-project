package uk.gov.dwp.engineering.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static uk.gov.dwp.engineering.recruitment.domain.TicketType.ADULT;
import static uk.gov.dwp.engineering.recruitment.domain.TicketType.CHILD;
import static uk.gov.dwp.engineering.recruitment.domain.TicketType.INFANT;

import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.dwp.engineering.recruitment.domain.BookingConfirmation;
import uk.gov.dwp.engineering.recruitment.domain.TicketRequest;
import uk.gov.dwp.engineering.recruitment.exception.InvalidBookingException;
import uk.gov.dwp.engineering.recruitment.thirdparty.PaymentService;
import uk.gov.dwp.engineering.recruitment.thirdparty.SeatReservationService;

@ExtendWith(MockitoExtension.class)
class CinemaTicketsServiceImplTest {

  private static final long ACCOUNT_ID = 1234L;

  @Mock
  private PaymentService paymentService;

  @Mock
  private SeatReservationService seatReservationService;

  private CinemaTicketsService service;

  @BeforeEach
  void setUp() {
    service = new CinemaTicketsServiceImpl(paymentService, seatReservationService);
  }

  // ---------------------------------------------------------------------------------------------
  // Valid purchases
  // ---------------------------------------------------------------------------------------------

  static Stream<Arguments> validPurchases() {
    return Stream.of(
        // adults, children, infants, expected cost, expected seats
        Arguments.of(1, 0, 0, "25.99", 1L),
        Arguments.of(2, 0, 0, "51.98", 2L),
        Arguments.of(1, 1, 0, "43.49", 2L),
        Arguments.of(1, 0, 1, "25.99", 1L),
        Arguments.of(2, 3, 2, "104.48", 5L),
        Arguments.of(1, 24, 0, "445.99", 25L),
        Arguments.of(25, 0, 0, "649.75", 25L),
        Arguments.of(12, 1, 12, "329.38", 13L));
  }

  @ParameterizedTest(name = "{0} adult(s), {1} child(ren), {2} infant(s) costs £{3} for {4} seat(s)")
  @MethodSource("validPurchases")
  void givenValidPurchase_whenPurchaseTickets_thenPaysThenReservesAndConfirms(final int adults,
      final int children, final int infants, final String expectedCost, final long expectedSeats) {

    final BookingConfirmation confirmation = service.purchaseTickets(ACCOUNT_ID,
        new TicketRequest(ADULT, adults),
        new TicketRequest(CHILD, children),
        new TicketRequest(INFANT, infants));

    final BigDecimal cost = new BigDecimal(expectedCost);
    final InOrder order = inOrder(paymentService, seatReservationService);
    order.verify(paymentService).debitAccount(ACCOUNT_ID, cost);
    order.verify(seatReservationService).reserveSeats(ACCOUNT_ID, expectedSeats);
    order.verifyNoMoreInteractions();

    assertEquals(ACCOUNT_ID, confirmation.accountId());
    assertEquals(expectedSeats, confirmation.seatCount());
    assertEquals(cost, confirmation.totalCost());
  }

  @Test
  void givenSameTypeRequestedOnSeveralLines_whenPurchaseTickets_thenCountsAreCombined() {
    final BookingConfirmation confirmation = service.purchaseTickets(ACCOUNT_ID,
        new TicketRequest(ADULT, 1),
        new TicketRequest(CHILD, 1),
        new TicketRequest(ADULT, 1));

    assertEquals(3L, confirmation.seatCount());
    assertEquals(new BigDecimal("69.48"), confirmation.totalCost());
  }

  @Test
  void givenZeroCountLineAlongsideValidTickets_whenPurchaseTickets_thenZeroLineIsIgnored() {
    final BookingConfirmation confirmation = service.purchaseTickets(ACCOUNT_ID,
        new TicketRequest(ADULT, 1),
        new TicketRequest(CHILD, 0));

    assertEquals(1L, confirmation.seatCount());
    assertEquals(new BigDecimal("25.99"), confirmation.totalCost());
  }

  @Test
  void givenOnlyAdultLine_whenPurchaseTickets_thenOtherTypesAreNotRequired() {
    final BookingConfirmation confirmation = service.purchaseTickets(ACCOUNT_ID,
        new TicketRequest(ADULT, 3));

    assertEquals(3L, confirmation.seatCount());
    assertEquals(new BigDecimal("77.97"), confirmation.totalCost());
  }

  // ---------------------------------------------------------------------------------------------
  // Invalid account
  // ---------------------------------------------------------------------------------------------

  @Test
  void givenNullAccountId_whenPurchaseTickets_thenRejected() {
    assertRejected("Account id must be provided", null, new TicketRequest(ADULT, 1));
  }

  @ParameterizedTest
  @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
  void givenAccountIdNotGreaterThanZero_whenPurchaseTickets_thenRejected(final long accountId) {
    assertRejected("Account id must be greater than zero", accountId,
        new TicketRequest(ADULT, 1));
  }

  // ---------------------------------------------------------------------------------------------
  // Invalid ticket request lines
  // ---------------------------------------------------------------------------------------------

  @Test
  void givenNullTicketRequestArray_whenPurchaseTickets_thenRejected() {
    assertRejected("At least one ticket request must be provided", ACCOUNT_ID,
        (TicketRequest[]) null);
  }

  @Test
  void givenNoTicketRequests_whenPurchaseTickets_thenRejected() {
    assertRejected("At least one ticket request must be provided", ACCOUNT_ID);
  }

  @Test
  void givenNullTicketRequestEntry_whenPurchaseTickets_thenRejected() {
    assertRejected("Ticket requests must not contain null entries", ACCOUNT_ID,
        new TicketRequest(ADULT, 1), null);
  }

  @Test
  void givenTicketRequestWithoutType_whenPurchaseTickets_thenRejected() {
    assertRejected("Ticket type must be provided for every ticket request", ACCOUNT_ID,
        new TicketRequest(ADULT, 1), new TicketRequest(null, 1));
  }

  @Test
  void givenNegativeTicketCount_whenPurchaseTickets_thenRejected() {
    assertRejected("Ticket count must not be negative", ACCOUNT_ID,
        new TicketRequest(ADULT, 2), new TicketRequest(CHILD, -1));
  }

  @Test
  void givenAllTicketCountsZero_whenPurchaseTickets_thenRejected() {
    assertRejected("At least one ticket must be purchased", ACCOUNT_ID,
        new TicketRequest(ADULT, 0), new TicketRequest(CHILD, 0));
  }

  // ---------------------------------------------------------------------------------------------
  // Maximum of 25 tickets
  // ---------------------------------------------------------------------------------------------

  @Test
  void givenMoreThan25AdultTickets_whenPurchaseTickets_thenRejected() {
    assertRejected("A maximum of 25 tickets can be purchased at a time", ACCOUNT_ID,
        new TicketRequest(ADULT, 26));
  }

  @Test
  void givenMoreThan25TicketsAcrossTypes_whenPurchaseTickets_thenRejected() {
    assertRejected("A maximum of 25 tickets can be purchased at a time", ACCOUNT_ID,
        new TicketRequest(ADULT, 10), new TicketRequest(CHILD, 10),
        new TicketRequest(INFANT, 6));
  }

  @Test
  void givenInfantsTakeTotalOver25_whenPurchaseTickets_thenRejectedBecauseInfantsAreTickets() {
    // 13 adults + 13 infants = 26 tickets, but only 13 seats
    assertRejected("A maximum of 25 tickets can be purchased at a time", ACCOUNT_ID,
        new TicketRequest(ADULT, 13), new TicketRequest(INFANT, 13));
  }

  @Test
  void givenMoreThan25AcrossDuplicateLines_whenPurchaseTickets_thenRejected() {
    assertRejected("A maximum of 25 tickets can be purchased at a time", ACCOUNT_ID,
        new TicketRequest(ADULT, 20), new TicketRequest(ADULT, 6));
  }

  @Test
  void givenCountsThatWouldOverflowAnInt_whenPurchaseTickets_thenRejectedNotOverflowed() {
    assertRejected("A maximum of 25 tickets can be purchased at a time", ACCOUNT_ID,
        new TicketRequest(ADULT, Integer.MAX_VALUE), new TicketRequest(ADULT, Integer.MAX_VALUE));
  }

  // ---------------------------------------------------------------------------------------------
  // Adult required / infants on laps
  // ---------------------------------------------------------------------------------------------

  @Test
  void givenChildWithoutAdult_whenPurchaseTickets_thenRejected() {
    assertRejected("Child and infant tickets cannot be purchased without an adult ticket",
        ACCOUNT_ID, new TicketRequest(CHILD, 2));
  }

  @Test
  void givenInfantWithoutAdult_whenPurchaseTickets_thenRejected() {
    assertRejected("Child and infant tickets cannot be purchased without an adult ticket",
        ACCOUNT_ID, new TicketRequest(INFANT, 1));
  }

  @Test
  void givenChildAndInfantWithZeroAdults_whenPurchaseTickets_thenRejected() {
    assertRejected("Child and infant tickets cannot be purchased without an adult ticket",
        ACCOUNT_ID, new TicketRequest(ADULT, 0), new TicketRequest(CHILD, 1),
        new TicketRequest(INFANT, 1));
  }

  @Test
  void givenMoreInfantsThanAdults_whenPurchaseTickets_thenRejected() {
    assertRejected("Each infant must sit on an adult's lap, so infants cannot outnumber adults",
        ACCOUNT_ID, new TicketRequest(ADULT, 1), new TicketRequest(INFANT, 2));
  }

  private void assertRejected(final String expectedMessage, final Long accountId,
      final TicketRequest... ticketRequests) {
    final InvalidBookingException exception = assertThrows(InvalidBookingException.class,
        () -> service.purchaseTickets(accountId, ticketRequests));
    assertEquals(expectedMessage, exception.getMessage());
    verifyNoInteractions(paymentService, seatReservationService);
  }
}
