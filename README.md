# Cinema Tickets

A Spring Boot REST API for buying cinema tickets.

You send a booking to `POST /cinema/bookings`. The service checks it against the rules,
charges the account, reserves the seats and sends back a confirmation.

## How it works

Every booking goes through four steps, in this order:

1. **Check the request.** If any rule is broken, the booking is rejected straight away.
   Nothing is charged and no seats are reserved.
2. **Work out the cost and the seats.** Adults and children need a seat. Infants are free
   and sit on an adult's lap, so they don't get a seat.
3. **Take the payment** by calling `PaymentService`.
4. **Reserve the seats** by calling `SeatReservationService`.

I take payment before reserving seats so that seats are never held for a booking that
hasn't been paid for.

A good booking returns **201 Created** with the account id, the number of seats and the
total cost. A bad booking returns **400 Bad Request** with a message that says which rule
was broken.

## Where the code is

| Class | What it does |
|-------|--------------|
| `CinemaTicketsServiceImpl` | Runs the four steps above. |
| `booking/TicketPurchaseValidator` | Holds all the business rules and error messages. |
| `booking/TicketOrder` | Adds up adults, children and infants; works out seats and cost. |
| `booking/TicketPrices` | The price of each ticket type. |
| `exception/RestExceptionHandler` | Turns a broken rule into a 400 response. |
| `domain/BookingConfirmation` | The response. I added `seatCount` and `totalCost` to it. |

I kept the rules and the pricing in plain Java classes, separate from Spring. That makes
them easy to test and easy to change.

## Prices

| Ticket | Price | Gets a seat? |
|--------|-------|--------------|
| ADULT | £25.99 | Yes |
| CHILD | £17.50 | Yes |
| INFANT | £0.00 | No |

Money is stored as `BigDecimal`, not `double`, so the totals are always exact to the penny.

## When a booking is rejected

- The account id is missing, zero or negative.
- No tickets are sent, or a ticket has no type.
- A ticket count is negative.
- The total number of tickets is zero.
- There are more than 25 tickets in total.
- There are child or infant tickets but no adult ticket.
- There are more infants than adults.

Spring also rejects bad JSON, unknown ticket types (for example `SENIOR`) and requests that
aren't JSON.

## Decisions I made

The brief left some things open. This is what I decided and why.

- **Infants count towards the 25-ticket limit.** The rule says 25 *tickets*, and an infant
  ticket is still a ticket, even though it's free.
- **No more infants than adults.** Each infant sits on an adult's lap, so each infant needs
  their own adult.
- **No limit on children per adult.** The brief only says an adult must be there, so I
  didn't invent a limit.
- **A count of 0 is allowed, but the booking must have at least one ticket.** The API spec
  allows 0, so `{"type": "CHILD", "ticketCount": 0}` is simply ignored.
- **The same ticket type can appear twice.** The counts are added together before the rules
  are checked, so splitting a request into lines can't get around the limits.
- **Third-party failures are not handled.** The brief says the payment and seat services
  always work. In a real system, if the seat reservation failed after payment, we'd need to
  refund the customer.
- **Rejected bookings return 400, not 501.** The template returned 501 (Not Implemented).
  A broken rule is the caller's mistake, so 400 is the right code.
- **JSON field names.** The API spec (`cinema-tickets.yaml`) uses `tickets` and `count`, but
  the domain classes, which I wasn't allowed to change, use `ticketRequests` and
  `ticketCount`. The API uses the domain names.

## Running it

You need Java 21 and Maven.

    mvn test
    mvn spring-boot:run

## Tests

There are 71 tests, and they all pass.

- `CinemaTicketsServiceImplTest` checks every rule. For a good booking it checks the exact
  amount charged, the seats reserved, and that payment happens first. For a bad booking it
  checks that nothing is charged or reserved.
- `CinemaTicketsControllerTest` sends real JSON to the endpoint and checks the 201 and 400
  responses.
- `CinemaTicketsIntegrationTest` starts the whole app and makes real HTTP calls.
- `TicketOrderTest` and `TicketPricesTest` check the prices and the seat count.

## Example

Request: 2 adults, 1 child and 1 infant.

    curl -i -X POST http://localhost:8080/cinema/bookings \
      -H "Content-Type: application/json" \
      -d '{"accountId": 1234, "ticketRequests": [
            {"type": "ADULT", "ticketCount": 2},
            {"type": "CHILD", "ticketCount": 1},
            {"type": "INFANT", "ticketCount": 1}]}'

Response: 201 Created. That's 3 seats, and the cost is 2 x £25.99 + £17.50 = £69.48.

    {"accountId":1234,"seatCount":3,"totalCost":69.48}

Request: a child with no adult.

    curl -i -X POST http://localhost:8080/cinema/bookings \
      -H "Content-Type: application/json" \
      -d '{"accountId": 1234, "ticketRequests": [{"type": "CHILD", "ticketCount": 1}]}'

Response: 400 Bad Request.

    {"status":400,"title":"Invalid booking request",
     "detail":"Child and infant tickets cannot be purchased without an adult ticket"}

## What I would improve

- The payment and seat stub classes read their URLs too early, so the URLs are empty. They
  never make real calls, so I left them alone. The fix is to pass the URL in through the
  constructor.
- The prices and the 25-ticket limit are fixed in the code. They could move into
  configuration if they change often.
- If the services could fail, I would check their responses and add a refund step.
