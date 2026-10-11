# Cinema Tickets

A small Spring Boot REST service that sells cinema tickets. `POST /cinema/bookings` validates
a purchase against the cinema's business rules, takes payment through the `PaymentService`,
reserves seats through the `SeatReservationService` and returns a booking confirmation.

## Approach

The request flows **controller → service → validator → payment → seat reservation**.

1. **Validate everything first.** `TicketPurchaseValidator` checks the account id and every
   ticket request line, combines the lines into a `TicketOrder` (adult, child and infant
   totals), then applies the rules to the combined order. An invalid request throws
   `InvalidBookingException` before any third-party call, so nothing is ever charged or
   reserved for a request that will be rejected.
2. **Calculate.** `TicketOrder` works out the seats needed (adults + children) and the total
   cost. Prices live in `TicketPrices`, held as `BigDecimal` so money is never rounded by
   floating-point arithmetic. Its `switch` is exhaustive, so a new `TicketType` without a
   price will not compile.
3. **Pay, then reserve.** Payment is taken before seats are reserved so that seats are never
   held for a booking that has not been paid for.
4. **Respond.** A successful booking returns `201 Created` with a `BookingConfirmation`
   (account id, seats reserved, total cost). A rejected booking returns `400 Bad Request` with
   an RFC 9457 Problem Detail whose `detail` says which rule was broken.

The validation and pricing code is plain Java with no Spring dependency, which keeps it easy
to unit test and to reuse.

### Code layout

| Class | Responsibility |
|-------|----------------|
| `CinemaTicketsController` | HTTP endpoint (unchanged). |
| `CinemaTicketsServiceImpl` | Orchestrates validate → pay → reserve → confirm. |
| `booking.TicketPurchaseValidator` | All business rules and their error messages. |
| `booking.TicketOrder` | Combined ticket counts; seat count and total cost. |
| `booking.TicketPrices` | Price list per ticket type. |
| `exception.RestExceptionHandler` | Maps `InvalidBookingException` to a 400 Problem Detail. |
| `domain.BookingConfirmation` | Extended with `seatCount` and `totalCost` (see below). |

## Business rules

| # | Rule | Result |
|---|------|--------|
| 1 | Account id must be provided and greater than zero | Rejected – 400 |
| 2 | At least one ticket request must be provided | Rejected – 400 |
| 3 | A ticket request must not be null and must have a ticket type | Rejected – 400 |
| 4 | A ticket count must not be negative | Rejected – 400 |
| 5 | At least one ticket must be bought in total | Rejected – 400 |
| 6 | No more than 25 tickets (all types, infants included) per purchase | Rejected – 400 |
| 7 | Child and infant tickets need at least one adult ticket | Rejected – 400 |
| 8 | Infants cannot outnumber adults (one infant per adult lap) | Rejected – 400 |
| 9 | Prices: adult £25.99, child £17.50, infant £0 | Charged via `PaymentService` |
| 10 | Adults and children get a seat; infants do not | Reserved via `SeatReservationService` |
| – | Malformed JSON, unknown ticket type, missing body, wrong field types | Rejected – 400 (Spring) |
| – | Content type other than JSON | Rejected – 415 (Spring) |

## Assumptions and ambiguities

| Ambiguity | My decision | Reason |
|-----------|-------------|--------|
| Is a request for more infants than adults valid? | Rejected | Each infant sits on an adult's lap, so there must be at least one adult per infant. |
| Do infants count towards the 25-ticket limit? | Yes | The rule says 25 *tickets*, and an infant ticket is still a ticket even though it is free and has no seat. |
| Is a ticket line with a count of 0 valid? | Allowed and ignored | The OpenAPI spec gives `minimum: 0` and uses 0 as an example. The purchase as a whole must still contain at least one ticket. |
| Is a negative count valid? | Rejected | It has no meaning and could otherwise reduce the price or seat count. |
| Can the same ticket type appear on more than one line? | Yes, the counts are added together | Rules are applied to the combined totals, so splitting lines cannot get around the 25-ticket or infant limits. |
| Is there a limit on children per adult? | No | The rules only require at least one adult; I did not invent a ratio. |
| Is an account id of `null`, 0 or negative valid? | Rejected | Only ids greater than zero are valid accounts. |
| What if a third-party call fails? | Not handled | The brief says both services have no defects and always succeed, so there is no retry, refund or rollback. In a real system a failed reservation after payment would need a refund or compensation step. |
| Which call happens first? | Payment, then reservation | Seats are not held for an unpaid booking. |
| What status should a rejected booking return? | 400 Bad Request | The caller sent an invalid request. The template's handler returned 501 Not Implemented, which I changed. |
| What does a successful response contain? | `accountId`, `seatCount`, `totalCost` | Matches the `BookingConfirmation` schema in `cinema-tickets.yaml`. I extended the record without breaking it: the original one-argument constructor and `accountId()` still exist. |
| JSON field names | `ticketRequests` and `ticketCount` | The `cinema-tickets.yaml` spec says `tickets` and `count`, but the domain records (which must not be changed) bind to `ticketRequests` and `ticketCount`. I kept the domain as it is and documented the actual contract here. |
| Currency and rounding | GBP, `BigDecimal`, 2 decimal places | Exact arithmetic for money. |
| Can counts overflow? | No | Counts are summed as `long`, so even very large values are correctly rejected by the 25-ticket rule. |

## Running it

Requires Java 21 and Maven.

    mvn test
    mvn spring-boot:run

## Tests

* `CinemaTicketsServiceImplTest` – every rule and every rejection, with mocked third-party
  services. Checks the exact amount paid and seats reserved, that payment happens before
  reservation, and that nothing is called when a request is rejected.
* `CinemaTicketsControllerTest` – the HTTP contract through MockMvc with the real service and
  exception handler: 201 responses, a 400 Problem Detail for each broken rule, and 400/415
  for unreadable requests.
* `CinemaTicketsIntegrationTest` – starts the whole application on a random port and makes
  real HTTP calls for one valid and one invalid booking.
* `TicketOrderTest`, `TicketPricesTest` – pricing and seat calculation.

## Example requests

Valid booking:

    curl -i -X POST http://localhost:8080/cinema/bookings \
      -H "Content-Type: application/json" \
      -d '{"accountId": 1234, "ticketRequests": [
            {"type": "ADULT", "ticketCount": 2},
            {"type": "CHILD", "ticketCount": 1},
            {"type": "INFANT", "ticketCount": 1}]}'

    HTTP/1.1 201
    Content-Type: application/json

    {"accountId":1234,"seatCount":3,"totalCost":69.48}

Invalid booking (a child with no adult):

    curl -i -X POST http://localhost:8080/cinema/bookings \
      -H "Content-Type: application/json" \
      -d '{"accountId": 1234, "ticketRequests": [{"type": "CHILD", "ticketCount": 1}]}'

    HTTP/1.1 400
    Content-Type: application/problem+json

    {"detail":"Child and infant tickets cannot be purchased without an adult ticket",
     "instance":"/cinema/bookings","status":400,"title":"Invalid booking request"}

## Known limitations

* The `@Value` fields in the third-party stubs (`PaymentServiceImpl`,
  `SeatReservationServiceImpl`) are read inside the constructor, before Spring has injected
  them, so the `RestClient` base URLs are `null`. The stubs never make a real call, and the
  brief treats them as external, so I left them unchanged. The fix would be constructor
  injection: `@Value("${payment-service.url}") String url` as a constructor parameter.
* The status codes returned by the third-party services are not checked, because the brief
  says they always succeed.
* Prices and the 25-ticket limit are constants. If they changed often they could move to
  configuration.
* The JSON field names differ from `cinema-tickets.yaml` (see above).
