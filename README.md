# Stock Price Tracker

A Spring Boot service that reads intraday stock price CSV files and reports the **daily high and low
price of each stock symbol**, with the timestamp when each happened. Task: [`docs/problem.md`](docs/problem.md).

## 1. How to build and run

Requires **JDK 21** and **Docker**. Gradle is downloaded by the wrapper (`./gradlew`).

```bash
docker compose up -d                       # 1. start PostgreSQL
./gradlew bootRun                          # 2. start the app (Flyway creates the tables)

mkdir -p data/input                        # 3. process the provided file
cp sample-data/stock-prices.csv data/input/
curl -X POST http://localhost:8080/api/v1/folder-scans          # starts the scan (202)
curl http://localhost:8080/api/v1/folder-scans/latest           # repeat until "status": "COMPLETED"

curl http://localhost:8080/api/v1/summaries   # 4. see the result
```

```json
[ { "stockSymbol": "AAPL", "highPrice": 180.123456, "highTimestamp": "2026-09-16T14:39:42.983730Z",
    "lowPrice": 153.215726, "lowTimestamp": "2026-09-16T14:39:33.983730Z" }, ... ]
```

**Multiple files:** copy them into `data/input` (folder set by `FOLDER_DIR`) and call the scan again.
Files are processed one after another in file-name order and stay in the folder. A single file can
also be uploaded: `curl -F file=@prices.csv http://localhost:8080/api/v1/imports`.

| Endpoint | Purpose |
|---|---|
| `GET /api/v1/summaries?date=` | High/low of every symbol for a day (the required output) |
| `GET /api/v1/summaries/{symbol}?date=` | High/low of one symbol |
| `GET /api/v1/summaries/{symbol}/history` | High/low of one symbol for every trading day |
| `POST /api/v1/folder-scans` | Start processing the new CSV files in the folder, in the background (an admin task, see below): `202`, or `409` if a scan is running |
| `GET /api/v1/folder-scans/latest` | Progress of the running scan, or the result of the last one |
| `POST /api/v1/imports` | Upload one CSV file |
| `GET /api/v1/imports`, `GET /api/v1/imports/{id}` | Status of processed files, with rejected rows counted by reason |

Swagger UI: http://localhost:8080/swagger-ui.html. Health check: http://localhost:8080/actuator/health
(includes the database). Tests: `./gradlew test` (the integration tests use Testcontainers and are
skipped automatically without Docker). Logs: one line per processed file; start with
`LOG_LEVEL=DEBUG` to also see every rejected row.

## 2. Architecture and design decisions

**How a file is processed:**
1. Compute the file's SHA-256 checksum; content that was already handled is skipped.
2. Read the CSV **one row at a time**. Invalid rows are skipped and counted by reason; they never
   stop the file.
3. Keep a running high/low per (symbol, trading day): one entry per symbol, whatever the file size.
4. In **one transaction**, merge the results into `daily_summary` and mark the file `COMPLETED`
   (or `COMPLETED_WITH_ERRORS`). If the file is unusable, nothing is saved: a folder file is marked
   `FAILED` (`ERROR` for a technical problem), and an upload gets `400` with nothing stored.

**Key decisions:**

| Decision                                                                                                                        | Why                                                                                                                                                         |
|---------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Pre-compute the high/low value while reading; don't store every price row                                                       | Memory doesn’t grow with file size; a summary is a single-row lookup                                                                                        |
| One row per symbol per day, updated with a PostgreSQL upsert                                                                    | Atomic, so files processed at the same time cannot lose a high or low. Example Two files merge into AAPL’s row at the same time: one has a high of 160, the other 158. PostgreSQL makes the second wait and compare against the latest value, so 160 is kept.|
| If the same high (or low) price appears more than once, the **earliest** time is reported                                       | The result doesn't change if rows are in a different order or split across files                                                                            |
| Files stay in one folder; already handled content is skipped by checksum                                                        | Repeated scans and restarts add no records. Trade-off: each scan reads every file                                                                           |
| One in-memory "claim" per file content while it is processed                                                                    | Identical content arriving at the same time (two uploads, or upload + scan) is processed once; different files still run in parallel.                       |
| `FAILED` (bad file) is not retried; `ERROR` (technical problem) is                                                              | Retrying a bad file fails the same way; an outage should not block a good file                                                                              |
| Scans start by REST call; nothing is read at startup; the folder is set on the server                                           | Processing happens only when asked; callers cannot make the server read arbitrary paths                                                                     |
| The folder scan is an admin (operations) task; uploads are the endpoint for users                                               | An admin or a scheduled job drops any number of files into the server folder and processes them with one call; users send their own file through the upload |
| Folder scans run in the background: `202 Accepted` at once, progress at `/folder-scans/latest`                                  | Many large files can take minutes; nobody waits on an open request, and a client timeout loses nothing                                                      |
| One scan at a time: a scan request while one is running gets `409 Conflict` pointing at its progress; otherwise the scan starts | One folder, so parallel scans would only compete for the same files. The caller is told immediately, and nothing is processed twice                         |

The logic is unit-tested with Mockito.

**Proposed section** Out of scope for this task (next steps for production):
- **Scan progress is kept in memory:** only the latest scan is shown, and it is gone after a restart
  (every file's result is still stored and listed by `/imports`). Production would store scans in a
  table, each with its own id.
- **No Housekeeping**. Processed files stay in the folder and their records stay in the database, so each scan reads more files over time. Recommendation: a scheduled clean-up that archives or deletes COMPLETED files after a retention period (e.g. 30 days), keeps FAILED ones until reviewed, and removes old records the same way.
- **No authentication**, and uploads may be up to 1 GB. In production the folder scan would be restricted
  to an admin role (e.g. Spring Security), and uploads to signed-in users.
- **Rejected rows are counted, not kept.** Production would offer a downloadable CSV of the rejected
  rows (with line and reason) to fix and upload again; the merge makes re-uploading only the fixed
  rows give the same result.

## 3. Technology stack and why

- **Java 21**: records, sealed types and pattern-matching `switch` let the compiler check that every
  case is handled; virtual threads for cheap concurrency.
- **Spring Boot 3.4**: standard for REST services (web, validation, dependency injection, config).
- **Gradle**: short build file; the wrapper needs no installation.
- **PostgreSQL**: reliable, and its atomic upsert merges results safely.
- **Flyway**: versioned SQL schema; Hibernate only validates it.
- **Spring Data JPA** for file records, **Spring JDBC** for the upsert (which JPA cannot express).
- **Apache Commons CSV**: streams any file size and handles quoting, unlike `split(",")`.
- **springdoc-openapi**: Swagger UI generated from the code.
- **Lombok**: removes boilerplate.
- **JUnit 5 + Mockito, Testcontainers**: unit tests per class, plus tests against a real PostgreSQL.

## 4. Edge cases and data validation

Rule: a row is either fully trusted or skipped. **Values are never guessed or repaired.**

| Row | Result | In the provided file |
|---|---|---|
| Missing `stockSymbol`, `price`, `timestamp` or `exchange` | Rejected | lines 103, 107 |
| Price not a number, or ≤ 0 | Rejected | lines 106 (`N/A`), 104 (`-52.5`) |
| Price with more than 6 decimals | Rejected, not rounded | – |
| Timestamp without time zone or not ISO 8601 | Rejected (the zone would be a guess) | line 105 |
| Missing `volume` | Accepted (optional, not used) | line 102 |
| Invalid `volume` | Rejected (corrupt column, suspect row) | – |
| Out-of-order timestamps | Handled; order does not matter | line 108 |
| Lower-case symbol, spaces, columns in any order | Normalised / matched by header name | – |

| File | Folder scan | Upload |
|---|---|---|
| Not `.csv` | Ignored | `400`, nothing stored |
| Empty, missing header columns, or broken CSV | `FAILED`, reason stored; listed under `failed` in the scan result | `400`, nothing stored |
| Same content processed before (any name) | Skipped, no new record | `SKIPPED_DUPLICATE` |
| Same content being processed right now | Skipped, listed as `inProgress`, handled by the next scan | `409`, nothing stored |
| Database unavailable | `ERROR`, retried next scan | `500` |

**Rejected rows** are not stored one by one. `GET /api/v1/imports/{id}` returns an `errorSummary`
with one entry per reason, so a file with 10,000 bad rows still shows every kind of problem in a few
lines. For the provided file:

```json
"errorSummary": [
  { "code": "MISSING_FIELD",      "count": 2, "firstLine": 103, "example": "Required field 'price' is missing or blank." },
  { "code": "PRICE_NOT_POSITIVE", "count": 1, "firstLine": 104, "example": "Price '-52.500000' must be greater than zero." },
  { "code": "INVALID_TIMESTAMP",  "count": 1, "firstLine": 105, "example": "Timestamp '09/16/2026 14:39:45' is not ..." },
  { "code": "PRICE_NOT_A_NUMBER", "count": 1, "firstLine": 106, "example": "Price 'N/A' is not a valid decimal number." }
]
```

Several files for the same stock symbol and day are merged into one high/low; different days stay separate.
