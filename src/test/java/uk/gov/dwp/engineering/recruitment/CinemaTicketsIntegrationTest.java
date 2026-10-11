package uk.gov.dwp.engineering.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.dwp.engineering.recruitment.thirdparty.PaymentService;
import uk.gov.dwp.engineering.recruitment.thirdparty.SeatReservationService;

/**
 * Starts the full application on a random port and makes real HTTP calls, to prove the
 * controller, service, JSON mapping and exception handler are wired together correctly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CinemaTicketsIntegrationTest {

  @Value("${local.server.port}")
  private int port;

  @MockitoBean
  private PaymentService paymentService;

  @MockitoBean
  private SeatReservationService seatReservationService;

  private final HttpClient httpClient = HttpClient.newBuilder()
      .proxy(HttpClient.Builder.NO_PROXY)
      .build();

  @Test
  void givenValidBooking_whenPosted_thenCreatedAndThirdPartiesCalled() throws Exception {
    final HttpResponse<String> response = post("""
        {"accountId": 99, "ticketRequests": [
          {"type": "ADULT", "ticketCount": 1}, {"type": "CHILD", "ticketCount": 1}]}
        """);

    assertEquals(201, response.statusCode());
    assertTrue(response.body().contains("\"seatCount\":2"), response.body());
    assertTrue(response.body().contains("\"totalCost\":43.49"), response.body());
    verify(paymentService).debitAccount(99L, new BigDecimal("43.49"));
    verify(seatReservationService).reserveSeats(99L, 2L);
  }

  @Test
  void givenInvalidBooking_whenPosted_thenBadRequestProblemDetail() throws Exception {
    final HttpResponse<String> response = post("""
        {"accountId": 99, "ticketRequests": [{"type": "CHILD", "ticketCount": 1}]}
        """);

    assertEquals(400, response.statusCode());
    assertTrue(response.body().contains(
        "Child and infant tickets cannot be purchased without an adult ticket"), response.body());
    verifyNoInteractions(paymentService, seatReservationService);
  }

  private HttpResponse<String> post(final String json) throws Exception {
    final HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:" + port + "/cinema/bookings"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
