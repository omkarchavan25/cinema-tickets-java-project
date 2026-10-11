package uk.gov.dwp.engineering.recruitment;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.gov.dwp.engineering.recruitment.exception.RestExceptionHandler;
import uk.gov.dwp.engineering.recruitment.thirdparty.PaymentService;
import uk.gov.dwp.engineering.recruitment.thirdparty.SeatReservationService;

/**
 * Tests the HTTP contract of {@link CinemaTicketsController}: status codes, response bodies and
 * Problem Details for every business rule. The real service and exception handler are used, and
 * only the two third-party services are mocked, so these tests exercise the rules end to end
 * through the web layer.
 */
@ExtendWith(MockitoExtension.class)
class CinemaTicketsControllerTest {

  private static final String BOOKINGS_URL = "/cinema/bookings";

  @Mock
  private PaymentService paymentService;

  @Mock
  private SeatReservationService seatReservationService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    final CinemaTicketsService service =
        new CinemaTicketsServiceImpl(paymentService, seatReservationService);
    mockMvc = MockMvcBuilders.standaloneSetup(new CinemaTicketsController(service))
        .setControllerAdvice(new RestExceptionHandler())
        .build();
  }

  // ---------------------------------------------------------------------------------------------
  // Successful bookings
  // ---------------------------------------------------------------------------------------------

  @Test
  void givenSingleAdult_whenPostBooking_thenCreatedWithConfirmation() throws Exception {
    postBooking("""
        {"accountId": 1234, "ticketRequests": [{"type": "ADULT", "ticketCount": 1}]}
        """)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.accountId").value(1234))
        .andExpect(jsonPath("$.seatCount").value(1))
        .andExpect(jsonPath("$.totalCost").value(25.99));

    verify(paymentService).debitAccount(1234L, new BigDecimal("25.99"));
    verify(seatReservationService).reserveSeats(1234L, 1L);
  }

  @Test
  void givenAdultsChildrenAndInfants_whenPostBooking_thenInfantsAreFreeAndGetNoSeat()
      throws Exception {
    postBooking("""
        {
          "accountId": 42,
          "ticketRequests": [
            {"type": "ADULT", "ticketCount": 2},
            {"type": "CHILD", "ticketCount": 3},
            {"type": "INFANT", "ticketCount": 2}
          ]
        }
        """)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.accountId").value(42))
        .andExpect(jsonPath("$.seatCount").value(5))
        .andExpect(jsonPath("$.totalCost").value(104.48));

    verify(paymentService).debitAccount(42L, new BigDecimal("104.48"));
    verify(seatReservationService).reserveSeats(42L, 5L);
  }

  @Test
  void givenExactly25Tickets_whenPostBooking_thenCreated() throws Exception {
    postBooking("""
        {
          "accountId": 7,
          "ticketRequests": [
            {"type": "ADULT", "ticketCount": 12},
            {"type": "CHILD", "ticketCount": 1},
            {"type": "INFANT", "ticketCount": 12}
          ]
        }
        """)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.seatCount").value(13))
        .andExpect(jsonPath("$.totalCost").value(329.38));
  }

  // ---------------------------------------------------------------------------------------------
  // Requests rejected by business rules
  // ---------------------------------------------------------------------------------------------

  static Stream<Arguments> invalidBookings() {
    return Stream.of(
        Arguments.of("account id missing",
            """
                {"ticketRequests": [{"type": "ADULT", "ticketCount": 1}]}""",
            "Account id must be provided"),
        Arguments.of("account id zero",
            """
                {"accountId": 0, "ticketRequests": [{"type": "ADULT", "ticketCount": 1}]}""",
            "Account id must be greater than zero"),
        Arguments.of("account id negative",
            """
                {"accountId": -5, "ticketRequests": [{"type": "ADULT", "ticketCount": 1}]}""",
            "Account id must be greater than zero"),
        Arguments.of("ticket requests missing",
            """
                {"accountId": 1}""",
            "At least one ticket request must be provided"),
        Arguments.of("ticket requests empty",
            """
                {"accountId": 1, "ticketRequests": []}""",
            "At least one ticket request must be provided"),
        Arguments.of("ticket request entry null",
            """
                {"accountId": 1, "ticketRequests": [{"type": "ADULT", "ticketCount": 1}, null]}""",
            "Ticket requests must not contain null entries"),
        Arguments.of("ticket type missing",
            """
                {"accountId": 1, "ticketRequests": [{"ticketCount": 1}]}""",
            "Ticket type must be provided for every ticket request"),
        Arguments.of("negative ticket count",
            """
                {"accountId": 1, "ticketRequests": [{"type": "ADULT", "ticketCount": -1}]}""",
            "Ticket count must not be negative"),
        Arguments.of("all counts zero",
            """
                {"accountId": 1, "ticketRequests": [{"type": "ADULT", "ticketCount": 0}]}""",
            "At least one ticket must be purchased"),
        Arguments.of("26 adults",
            """
                {"accountId": 1, "ticketRequests": [{"type": "ADULT", "ticketCount": 26}]}""",
            "A maximum of 25 tickets can be purchased at a time"),
        Arguments.of("26 tickets including infants",
            """
                {"accountId": 1, "ticketRequests": [
                  {"type": "ADULT", "ticketCount": 13}, {"type": "INFANT", "ticketCount": 13}]}""",
            "A maximum of 25 tickets can be purchased at a time"),
        Arguments.of("child without adult",
            """
                {"accountId": 1, "ticketRequests": [{"type": "CHILD", "ticketCount": 1}]}""",
            "Child and infant tickets cannot be purchased without an adult ticket"),
        Arguments.of("infant without adult",
            """
                {"accountId": 1, "ticketRequests": [{"type": "INFANT", "ticketCount": 1}]}""",
            "Child and infant tickets cannot be purchased without an adult ticket"),
        Arguments.of("more infants than adults",
            """
                {"accountId": 1, "ticketRequests": [
                  {"type": "ADULT", "ticketCount": 1}, {"type": "INFANT", "ticketCount": 2}]}""",
            "Each infant must sit on an adult's lap, so infants cannot outnumber adults"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidBookings")
  void givenInvalidBooking_whenPostBooking_thenBadRequestProblemDetailAndNothingCharged(
      final String description, final String body, final String expectedDetail) throws Exception {
    postBooking(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Invalid booking request"))
        .andExpect(jsonPath("$.detail").value(expectedDetail));

    verifyNoInteractions(paymentService, seatReservationService);
  }

  // ---------------------------------------------------------------------------------------------
  // Requests that cannot be read at all
  // ---------------------------------------------------------------------------------------------

  static Stream<Arguments> unreadableBodies() {
    return Stream.of(
        Arguments.of("malformed JSON", "{\"accountId\": 1, \"ticketRequests\": ["),
        Arguments.of("unknown ticket type",
            """
                {"accountId": 1, "ticketRequests": [{"type": "SENIOR", "ticketCount": 1}]}"""),
        Arguments.of("account id not a number",
            """
                {"accountId": "abc", "ticketRequests": [{"type": "ADULT", "ticketCount": 1}]}"""),
        Arguments.of("empty body", ""));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("unreadableBodies")
  void givenUnreadableBody_whenPostBooking_thenBadRequestAndNothingCharged(
      final String description, final String body) throws Exception {
    postBooking(body)
        .andExpect(status().isBadRequest());

    verifyNoInteractions(paymentService, seatReservationService);
  }

  @Test
  void givenNonJsonContentType_whenPostBooking_thenUnsupportedMediaType() throws Exception {
    mockMvc.perform(post(BOOKINGS_URL)
            .contentType(MediaType.TEXT_PLAIN)
            .content("ADULT x 1"))
        .andExpect(status().isUnsupportedMediaType());

    verifyNoInteractions(paymentService, seatReservationService);
  }

  private ResultActions postBooking(final String body) throws Exception {
    return mockMvc.perform(post(BOOKINGS_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON)
        .content(body));
  }
}
