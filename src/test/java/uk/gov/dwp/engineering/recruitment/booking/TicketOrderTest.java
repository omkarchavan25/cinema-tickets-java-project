package uk.gov.dwp.engineering.recruitment.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TicketOrderTest {

  @Test
  void givenMixedOrder_whenCalculated_thenInfantsCountAsTicketsButNotSeats() {
    final TicketOrder order = new TicketOrder(2, 1, 2);

    assertEquals(5, order.totalTickets());
    assertEquals(3, order.seatsToReserve());
    assertEquals(new BigDecimal("69.48"), order.totalCost());
  }

  @Test
  void givenInfantsOnly_whenCalculated_thenCostIsZeroAndNoSeats() {
    final TicketOrder order = new TicketOrder(0, 0, 3);

    assertEquals(0, order.seatsToReserve());
    assertEquals(0, BigDecimal.ZERO.compareTo(order.totalCost()));
  }

  @Test
  void givenAnyOrder_whenCostCalculated_thenScaleIsPence() {
    assertEquals(2, new TicketOrder(1, 1, 1).totalCost().scale());
  }
}
