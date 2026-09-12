# Testing Guide — URL Shortener (Snaplink)

This document documents every test in the project: what it does, the logic it verifies, and the key mocking pattern used.

> **Revising the annotations/APIs too?** See [`TESTING_ANNOTATIONS.md`](TESTING_ANNOTATIONS.md) — the
> interview-ready reference for every annotation, method, function and class used in this test suite.

Run the full suite with:

```
mvn test
```

**Suite status: 39 tests, 12 classes, all passing.**

---

## Table of contents

| Area | Class | Tests |
|------|-------|-------|
| Core URL logic | `url_shortner.service.UrlShortnerServiceTest` | 10 |
| Analytics enrichment | `url_shortner.service.AnalyticsServiceTest` | 3 |
| Geolocation/IP | `url_shortner.service.enrichment.ClientIPServiceTest` | 3 |
| Caching | `url_shortner.service.cache.InMemoryCacheServiceTest` | 2 |
| Caching | `url_shortner.service.cache.RedisCacheServiceTest` | 2 |
| Event pipeline (sync) | `url_shortner.service.events.SyncEventProducerTest` | 3 |
| Event pipeline (Kafka producer) | `url_shortner.service.kafka.KafkaClickProducerTest` | 2 |
| Event pipeline (Kafka consumer) | `url_shortner.service.kafka.KafkaClickConsumerTest` | 3 |
| REST controller (shortener) | `url_shortner.controller.UrlShortenerControllerTest` | 5 |
| REST controller (analytics) | `url_shortner.controller.AnalyticsControllerTest` | 3 |
| MVC pages | `url_shortner.controller.WebControllerTest` | 2 |
| Spring context | `UrlShortnerApplicationTests` | 1 |

**Total: 39**

---

## Testing approach

Two complementary styles are used:

1. **Pure unit tests** (`@ExtendWith(MockitoExtension.class)`) — services are tested in isolation with mocked
   collaborators (`@Mock` + `@InjectMocks`). Fast, no Spring context, deterministic. Used for:
   `UrlShortnerServiceTest`, `AnalyticsServiceTest`, `ClientIPServiceTest`, both cache tests, and all three
   event-pipeline tests.

2. **Slice tests** (`@WebMvcTest`) — controllers are tested with a real MockMvc dispatcher but only the web
   slice of the Spring context. Every collaborator bean is a `@MockitoBean`. The full request → handler →
   response (and JSON marshalling / view resolution) path is exercised without a database. Used for all three
   controller tests.

Tools used: JUnit 5, Mockito (`verify`, `ArgumentCaptor`, `times`, `never`), MockMvc + JSONPath.

---

## `service/UrlShortnerServiceTest` — core shortening/redirect logic (10 tests)

Tests `UrlShortnerService` (URL validation, alias reuse/dedup, random code generation, caching,
expiration) against mocked `UrlMappingRepository` + `CacheService`.

| Test | Logic under test |
|------|------------------|
| `shortenWithCustomAlias_createsMappingAndReturnsAlias` | A valid new alias must be persisted as a `UrlMapping` (verified via `ArgumentCaptor`), cached under `url:<code>` with any TTL, normalized to lowercase, and returned. |
| `shortenWithAliasTakenByDifferentUrl_throws` | Industry rule: if an alias already maps to a *different* URL, `shortenUrl` must throw `IllegalArgumentException` ("already used") and must **not** touch the database. |
| `shortenWithAliasReusedForSameUrl_returnsExistingWithoutSaving` | If an alias maps to the *same* URL, return the existing code and only re-cache it — no new row may be inserted. |
| `shortenWithInvalidAlias_throws` | Alias validation: too short, contains a space, or is a reserved word (`api`, `admin`, `www`, `analytics`, …) must be rejected before any persistence. |
| `shortenWithoutAlias_reusesExistingUnexpiredMapping` | Without a custom alias, an existing non-expired mapping for the same URL is reused (no new save). |
| `shortenWithoutAlias_generatesNewCodeOnCollisionRetry` | On a fresh URL, a new 10-char code is generated; when the first random code collides (`existsByShortCode` true), the service retries and succeeds on attempt 2 (`times(2)`), then saves. |
| `shorten_givesUpAfterCollisionRetries_throws` | After `MAX_RETRIES` (5) consecutive collisions the service gives up and throws `RuntimeException` — preventing infinite loops. |
| `getOriginalUrl_returnsFromCache_withoutTouchingDatabase` | Fast-path: a cache hit returns immediately and the repository is *never* queried (`verify(repository, never()).findByShortCode(...)`). |
| `getOriginalUrl_cachesDatabaseHit` | On a cache miss, the DB result is re-cached under `url:<code>` so the next call hits the cache. |
| `getOriginalUrl_expiredOrUnknown_returnsEmpty` | Expired codes and unknown codes both yield `Optional.empty()`; expired entries must not be cached. |

---

## `service/AnalyticsServiceTest` — click-event enrichment (3 tests)

Tests `AnalyticsService.buildAnalyticsEntity(ClickEvent)` — the pipeline that turns a raw click into the
persisted analytics row with device/browser/OS detection (uap-java) plus geolocation (GeoIPService).

| Test | Logic under test |
|------|------------------|
| `buildAnalyticsEntity_mapsFieldsAndEnriches` | A real Chrome-on-Windows desktop user agent is parsed into browser=Chrome, OS=Windows, deviceType=Desktop; the IP is geolocated via the mocked `GeoIPService`; all click fields (code, IP, referer, timestamp) are copied onto the entity. |
| `buildAnalyticsEntity_nullEvent_returnsNull` | Guard clause: a null event — or an event without a short code — must yield null so downstream code can skip persistence safely. |
| `buildAnalyticsEntity_blankUserAgent_stillGeocodes` | A blank user agent skips UA parsing (browser/OS/device stay null) but geolocation still runs — analytics are never lost because of a missing UA. |

---

## `service/enrichment/ClientIPServiceTest` — client IP resolution (3 tests)

Tests the header-based IP resolution used behind a load balancer / proxy (`X-Forwarded-For` →
`X-Real-IP` → `X-Client-IP` → remote address).

| Test | Logic under test |
|------|------------------|
| `getClientIP_usesFirstForwardedAddress` | `X-Forwarded-For` is authoritative and only the *first* address in `a.b.c.d, 10.0.0.1` is used (the client's real IP, not internal proxies). |
| `getClientIP_skipsUnknownForwardedFor_usesXRealIp` | A literal `unknown` value in `X-Forwarded-For` is treated as missing and resolution falls through to `X-Real-IP`. |
| `getClientIP_fallsBackToRemoteAddr` | For direct connections with no proxy headers, the raw socket address is returned. |

---

## `service/cache/InMemoryCacheServiceTest` — local cache (2 tests)

| Test | Logic under test |
|------|------------------|
| `setAndGet_roundTripsValue` | Values written with a TTL are returned by `get` (verify the map stores and retrieves correctly). |
| `get_expiredEntryOrUnknownKey_returnsNull` | Expired entries are evicted (return null, matching a miss) and unknown keys return null — callers cannot accidentally receive stale data. |

---

## `service/cache/RedisCacheServiceTest` — Redis cache (2 tests)

| Test | Logic under test |
|------|------------------|
| `setAndGet_delegateToRedis` | `set` delegates to `redisTemplate.opsForValue().set(key, value, ttl)` (TTL passed through as `Duration`) and `get` delegates to `.get(key)`. |
| `redisUnavailable_noThrow` | Resilience: when Redis is down (`opsForValue()` throws — e.g. connection refused), `get` returns null and `set` swallows the failure instead of crashing the request. |

---

## `service/events/SyncEventProducerTest` — the default event path (3 tests)

Tests `SyncEventProducer` — the blocking producer used when Kafka is disabled (the current setup).

| Test | Logic under test |
|------|------------------|
| `sendClickEvent_savesAnalyticsAndIncrementsCount` | A click event is persisted into the analytics table (`save`) and the parent mapping's `clickCount` is incremented by 1 atomically. |
| `sendClickEvent_nullEntity_skipsDatabase` | When `buildAnalyticsEntity` returns null (unparseable event), neither repository is touched — no junk rows. |
| `sendClickEvent_repositoryFailure_isSwallowed` | Database failures are logged and swallowed, never propagated to the caller — a failed click must not break the redirect. |

---

## `service/kafka/KafkaClickProducerTest` — async producer (2 tests)

| Test | Logic under test |
|------|------------------|
| `sendClickEvent_serializesAndSends` | The event is serialized to JSON via `ObjectMapper` and sent to the `click-events` topic. |
| `sendClickEvent_serializationFailure_noSend_noThrow` | If serialization fails the event is dropped silently (no send, no exception) — the redirect flow stays unaffected. |

---

## `service/kafka/KafkaClickConsumerTest` — batch consumer (3 tests)

Tests `KafkaClickConsumer` — listens to `click-events` and persists batches (this is the code path used
when Kafka is enabled).

| Test | Logic under test |
|------|------------------|
| `processBatch_savesAll_aggregatesCounts_acknowledges` | A batch of 2 events is fully persisted via `saveAll` (captured with `ArgumentCaptor`), click counts are *aggregated per short code* (`incrementClickCountBy("abc", 2)` — one DB update instead of two), and the Kafka offset is acknowledged. |
| `processBatch_parseError_skipsBadEvent_stillPersistsValid` | A malformed JSON event is logged and skipped while the remaining valid events are still persisted with correct per-code counts — one bad message cannot poison the whole batch. |
| `processBatch_emptyOrNullAck_doesNotCrash` | An empty batch touches nothing, and a null `Acknowledgment` is handled gracefully (no NPE) so the listener survives. |

---

## `controller/UrlShortenerControllerTest` — REST shortening/redirect (5 tests)

Slice test (`@WebMvcTest`) with `@MockitoBean` for `UrlShortnerService`, `ClientIPService`, `EventProducer`.
The redirect domain `PREFIX_WEBSITE_DOMAIN` is forced to `https://snap.link/` via `@TestPropertySource`.

| Test | Logic under test |
|------|------------------|
| `shorten_returnsPrefixedShortUrl` | `POST /api/shorten?url=...` returns 200 with the full short URL built as `PREFIX_WEBSITE_DOMAIN + code`. |
| `shorten_forwardsAliasAndExpiry` | The `alias` and ISO `expiresAt` parameters are forwarded to the service (expiry captured and asserted exactly — proves `@DateTimeFormat` binding works). |
| `shorten_aliasConflict_returns400` | `IllegalArgumentException` from the service is mapped to HTTP 400 with the exception message in the body (`@ExceptionHandler`). |
| `redirect_found_returns302AndEmitsEvent` | `GET /{code}` for a known code returns 302 with the correct `Location` header and emits a click event carrying the code, resolved client IP, User-Agent and Referer. |
| `redirect_notFound_returns404` | Unknown codes return 404 and **no** click event is emitted (a click only counts when the redirect actually happens). |

---

## `controller/AnalyticsControllerTest` — analytics REST (3 tests)

| Test | Logic under test |
|------|------------------|
| `getAnalytics_returnsAllSections` | `GET /api/analytics/{code}` returns the full payload: `totalClicks`, `recentClicks` **capped at 10**, and the country / device / hourly / referrer breakdowns with correct aggregate values. |
| `getAnalytics_noClicks_returnsEmptyShape` | A code with zero clicks returns totals of 0 with empty (not null) sections — the consumer-facing shape is stable. |
| `getAnalytics_hourlyWindowCoversLast24Hours` | The hourly breakdown query is bounded to the trailing ~24h — the `since` timestamp handed to the repository is captured and proven to be `now − 24h ` (within a minute tolerance). |

---

## `controller/WebControllerTest` — renderable pages (2 tests)

| Test | Logic under test |
|------|------------------|
| `home_rendersIndexView` | `GET /` and the legacy `GET /index.html` both resolve to the `index` view (200 OK) — the back-compat alias works. |
| `analytics_rendersAnalyticsView` | `GET /analytics` and legacy `GET /analytics.html` both resolve to the `analytics` view — no more 404s for the old `.html` routes. |

---

## `UrlShortnerApplicationTests` — context sanity (1 test)

`contextLoads()` boots the full Spring context. It is an integration smoke test that proves all beans
(controllers, services, repositories, cache, event producers, Kafka/Redis config when enabled) wire up
correctly against the configured data source. It relies on `.env` (loaded via
`spring.config.import=optional:file:.env[.properties]`) providing the database credentials and
`SPRING_PROFILES_ACTIVE=lite`.

---

## Notes for interview Q&A

- **Why `@WebMvcTest` + `@MockitoBean` instead of `@SpringBootTest` + MockMvc?**
  `@WebMvcTest` boots only the web slice (fast, no DB/Kafka/Redis), so controller tests are isolated and
  deterministic; `@MockitoBean` (Boot 3.4+) replaces the deprecated `@MockBean`.
- **Why the cap-on-10 for `recentClicks` is important:** the analytics page only renders the last 10 clicks;
  the test proves the *service/controller* already bounds the data sent to the front-end.
- **Why aggregation in the Kafka consumer matters:** it turns N per-message `UPDATE` statements into a single
  aggregated `UPDATE` per code, cutting DB load under high click volume.
- **Failure resilience is tested, not just happy paths:** swallowed DB/Kafka/serialization errors, expired
  entries, missing geo lookup data, and malformed batch messages are all covered, because in a real URL
  shortener a click must never break the redirect.