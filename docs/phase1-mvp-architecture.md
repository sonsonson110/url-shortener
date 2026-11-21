# Phase 1 MVP: Create and Redirect (Reading Guide)

A short guide to how the MVP works and why it is built this way. Read it before the code.

## 1. What the MVP does (and does not)

**Does:**

- Create a short link from a long URL.
- Redirect a short code to the long URL.
- Expose RED metrics and two custom counters.

**Does not (deliberately deferred):**

| Deferred | Picked up in |
| --- | --- |
| Redis cache, stampede protection | Weeks 6-7 |
| 301 vs 302 ADR | Weeks 6-7 |
| Benchmarks, `EXPLAIN ANALYZE` | Weeks 8-9 |
| Multi-instance and counter ranges | Week 10 |
| Click counting (`hit_count`) | Phase 3 |
| Custom aliases, accounts, UI | Backlog (`docs/BACKLOG.md`) |

## 2. Request flows

### Create

```text
Client
  │  POST /api/v1/links {"longUrl": "https://example.com/long"}
  ▼
LinkController ── validates the body (http/https only, max length)
  ▼
LinkService
  1. id   = SELECT nextval('links_id_seq')      (Postgres hands out a unique number)
  2. code = Base62.encode(id)                    (e.g. 125 -> "21")
  3. INSERT INTO links (id, short_code, long_url, expires_at)   (one write)
  ▼
201 {"shortCode": "21", "shortUrl": "<base-url>/21", ...}
```

### Redirect

```text
Client
  │  GET /21
  ▼
RedirectController (path restricted to [0-9A-Za-z]{1,12})
  ▼
LinkService.resolve("21")
  ├─ not found  -> 404
  ├─ expired    -> 410
  └─ found      -> 302 with Location: https://example.com/long
```

## 3. Package layout

Everything lives in `com.urlshortener.shortener`:

| Class | One-line responsibility |
| --- | --- |
| `LinkController` | `POST /api/v1/links`: management API, thin |
| `RedirectController` | `GET /{code}`: public redirect, the future hot path |
| `LinkService` | Create and resolve logic; owns the ID step |
| `Base62` | Pure function: number to base62 string (written by the owner) |
| `Link` | JPA entity for the `links` table |
| `LinkRepository` | `findByShortCode` and `nextId()` (reads the sequence) |
| `CreateLinkRequest`, `LinkResponse` | Request and response records |
| `ShortenerProperties` | `app.shortener.*` config (base URL, max URL length) |
| `ShortenerExceptionHandler` | Maps exceptions to 400/404/410 `ProblemDetail` |

## 4. ID generation (ADR 0002)

**Idea:** a Postgres sequence is an atomic counter. Each `nextval()` returns a number nobody else will get. Base62 turns that number into a short string using `0-9a-zA-Z`.

Worked example: `125 = 2 * 62 + 1`, so the digits are `2` and `1`, giving `"21"`.

**Why it cannot collide:** two app instances calling `nextval()` at the same time always receive different numbers, and base62 is a one-to-one mapping. So different ids always produce different codes. The `UNIQUE` constraint on `short_code` is a safety net, not the mechanism.

**Why fetch the id first:** `short_code` is `NOT NULL` and derives from the id. Fetching the id before the INSERT lets us write the row once, with no placeholder and no follow-up UPDATE.

**Entity detail:** since we assign the id ourselves, `Link` implements `Persistable` so Spring Data issues a plain INSERT instead of a merge (which adds a SELECT first).

**Weaknesses (accepted for the MVP):**

- Codes are sequential, so they are guessable. Acceptable: no accounts or private links yet.
- One extra DB round trip per create. Counter ranges (week 10) would remove it.
- Gaps in the sequence are normal (rolled-back transactions still consume numbers).

## 5. Key choices

- **Separate redirect controller:** gives short public URLs (`host/21`) and keeps the hot path apart from the management API, so metrics, caching and rate limiting can target it later.
- **302, not 301:** browsers do not cache it, so every click reaches the server and expiry and counts stay accurate. Revisited with real numbers in the weeks 6-7 ADR.
- **`hit_count` is not updated on redirect:** that would put a database write on the hottest path. Proper counting is Phase 3 (click analytics).

## 6. Errors and metrics

| Case | Response |
| --- | --- |
| Invalid or non-http(s) URL, too long | `400` ProblemDetail |
| Unknown code | `404` |
| Expired link | `410` |

Metrics:

- `http_server_requests` (automatic): rate, errors and duration per route.
- `links_created_total`: links created.
- `links_redirects_total{outcome="hit|not_found|expired"}`: redirect results.

## 7. Self-check questions

- Why can two instances never produce the same short code?
- Why does fetching the id first avoid a second UPDATE?
- What does `Persistable` save us from, and what would the SQL log show without it?
- Why is a sequence allowed to have gaps?
- What breaks first if this runs 100:1 reads to writes with no cache?
