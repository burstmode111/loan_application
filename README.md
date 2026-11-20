# loan_application

Two things I wanted to get right, both from problems I hit in production:

1. **Reliable event publishing** — a business write and its Kafka message must not disagree (transactional outbox).
2. **Oversell-safe credit grabbing** — concurrent requests must not sell more limit than exists.

Stack: Java 21, Spring Boot 4, Spring Data JPA, PostgreSQL, Kafka (KRaft in Docker), Redis.

---

## 1. Transactional outbox

### The problem

The naive version publishes the message inside the business method:

```java
loanRepository.save(loan);
kafkaTemplate.send("loan-events", loanId, payload);
```

Two ways this breaks:

| Failure | Result |
|---|---|
| Message sent, then the transaction rolls back | A message about something that never happened (phantom event) |
| Transaction commits, then sending fails | The event is lost forever, and nobody notices |

`send()` is asynchronous — it returns a future and the real send happens on another thread, so `try/catch` around it does not catch delivery failures. Adding `.get()` makes it synchronous but still does not make it atomic with the database commit.

### The design

```
POST /loans
  └─ @Transactional
       ├─ INSERT loan_application
       └─ INSERT outbox_event (status = PENDING, payload = snapshot)
     COMMIT                      <- "this must be published" is now on disk

OutboxRelay (@Scheduled, every 2s)
  ├─ SELECT * FROM outbox_event WHERE status='PENDING' ORDER BY id LIMIT 50
  ├─ send(topic, aggregateId, payload).get(5, SECONDS)
  ├─ success  -> status = SENT
  └─ failure  -> stay PENDING, retried on the next tick
```

Why this works: the payload is written **in the same database transaction** as the business row, so the database itself guarantees they agree. Publishing becomes a separate, retryable step.

### What it guarantees

- **At-least-once**: a message is never silently dropped. If the process dies before marking SENT, it is sent again after restart.
- **No phantom events**: if the transaction rolls back, the outbox row rolls back with it.
- **Ordering per loan**: the Kafka key is `aggregateId`, so all events for one loan land on the same partition.

### The tradeoff

At-least-once means duplicates are possible, so **consumers must be idempotent**.

### Verified

Five events that had piled up as `PENDING` (created while the relay did not exist yet) were all delivered on the relay's first scan — timestamps spanning 5m25s, offsets 31–35 in the topic.

---

## 2. Credit grabbing: three implementations, measured

100 units of credit, N concurrent requests, each trying to take 1.

| Implementation | Threads | Accepted | Units actually deducted | **Oversold** | Cost |
|---|---|---|---|---|---|
| Read-check-write, no lock | 200 | 200 | 22 | **178** | 167 ms |
| Read-check-write, no lock | 20 000 | 889 | 100 | **789** | — |
| Read-check-write, no lock | 500 | 500 | 53 | **447** | 315 ms |
| `UPDATE ... WHERE remaining >= ?` | 500 | 100 | 100 | **0** | **208 ms** |
| Redis + Lua script | 500 | 100 | 100 | **0** | 425 ms |

### Why implementation #1 oversells

```java
CreditLine line = repository.findById(id);      // every thread reads 100
if (line.getRemainingLimit() < amount) throw …   // every thread passes the check
line.setRemainingLimit(line.getRemainingLimit() - amount);   // every thread writes 99
```

Check and decrement are two separate steps with a window between them. All 500 requests read `100`, all passed validation, all inserted a `grab_record`, and all returned 200. The balance was overwritten to the same value repeatedly, so only 53 units were deducted.

**Note the pattern:** 500 success records, 53 units deducted. The books and the balance disagree — a business-level oversell that a single-threaded test will never reproduce.

### Why #2 is correct

The check and the decrement became one statement:

```sql
UPDATE credit_line SET remaining_limit = remaining_limit - ? 
WHERE id = ? AND remaining_limit >= ?
```

The row lock serializes writers, and each waiter re-evaluates the condition against the latest committed value. Losers affect 0 rows and get a 409.

### Why #3 exists, and why it is *slower* here

The Lua script makes check-and-decrement atomic inside Redis (scripts run without interleaving):

```lua
local cur  = tonumber(redis.call('get', KEYS[1]) or '0')
local need = tonumber(ARGV[1])
if cur < need then return 0 end
redis.call('decrby', KEYS[1], need)
return 1
```

Rejected requests never reach the database: no connection, no transaction, no row lock.

**But on this machine Redis measured slower than the database (425 ms vs 208 ms).** That is the honest result, and the reason matters:

- Redis runs in Docker on Windows — every call is a TCP round trip through the VM, ~0.5 ms each.
- A *failing* `UPDATE` is cheap: 0 rows changed, no WAL write, no new row version. PostgreSQL takes the row lock, sees the condition fails, and moves on.
- So at this scale the database's bad path is cheaper than Redis's network hop.

Redis earns its place when the database stops being local and cheap: remote DB, cross-AZ latency, pre-deduction with asynchronous persistence, and request rates far beyond what a single hot row can serialize.

### Design notes for production

- Add `retryCount`, `lastError`, `nextRetryAt` to the outbox and a backoff schedule.
- Add a cleanup job: archive `SENT` rows older than N days, or the table grows forever.
- Redis and the database can drift (Redis accepted, DB write failed). Production needs either a reconciliation job or an async persistence path that never disagrees.
- No consumer yet, so no dead-letter topic. A poison message would block its partition indefinitely.
- The read-check-write version and its numbers are kept in the repo on purpose — they are the "before" of the comparison.

---

## How to run

```bash
docker compose up -d          # Kafka (KRaft) on localhost:9092
docker run -d --name redis -p 6379:6379 redis:7
```

PostgreSQL must be running with a `loans` database. `application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/loans
spring.datasource.username=${DB_USER:postgres}
spring.datasource.password=${DB_PASSWORD:admin}
spring.jpa.hibernate.ddl-auto=update
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

```bash
./mvnw spring-boot:run
./mvnw test                      # both concurrency tests
```

---

## 60-second version

> I built a loan service with two guarantees I care about.
>
> The first is that if a loan is saved, its event gets published — and if the transaction rolls back, no event goes out. I do that with a transactional outbox: the payload is written in the same transaction as the loan, a scheduled relay picks up pending rows, sends them, and marks them sent. It is at-least-once, so consumers need to be idempotent.
>
> The second is that a limited credit line cannot be oversold. My first version did read-check-write with no lock. Under 500 concurrent requests it accepted all 500 and deducted only 53 units — 447 oversold. Then I fixed it two ways: a single conditional UPDATE, and a Redis Lua script. Both got to zero. On my machine the database version was actually faster, because a failing UPDATE is cheaper than a network hop to Redis — but Redis is what you reach for when the database is remote or the traffic is far beyond what one row can serialize.
