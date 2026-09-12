# Snaplink — Testing Annotations, Methods & Concepts (Interview Revision Guide)

This is the companion document to [`TESTING.md`](TESTING.md). `TESTING.md` explains **what** each of the
39 tests verifies. **This** document explains **how** — every annotation, method, function, and class used
in the test suite, with real code from this project, so you can revise the "vocabulary" of the tests before
an interview without re-reading the whole codebase.

> Scope: everything that actually appears under `src/test/java`. A bonus cheat-sheet at the end also covers
> the *production* annotations the tests exercise (MVC, JPA, Lombok, feature flags), because interviewers
> will probe those too.

---

## 1. At-a-glance: everything used in the suite

| Annotation / API | Framework | Where used in this repo | One-line purpose |
|---|---|---|---|
| `@Test` | JUnit 5 (Jupiter) | every test class | Marks a method as a runnable test |
| `@DisplayName("...")` | JUnit 5 | every test class | Human-readable test name in reports/IDEs |
| `@ExtendWith(MockitoExtension.class)` | JUnit 5 + Mockito | all service/cache/event tests | Registers Mockito, enables `@Mock`/`@InjectMocks`, enables strict stubbing |
| `@Mock` | Mockito | same classes as above | Creates a mock of a dependency |
| `@InjectMocks` | Mockito | same classes as above | Builds the class under test and injects the mocks |
| `@SpringBootTest` | Spring Test | `UrlShortnerApplicationTests` | Boots the full application context |
| `@WebMvcTest(Controller.class)` | Spring Test | 3 controller tests | Boots only the web slice for one controller |
| `@MockitoBean` | Spring Test (Boot 3.4+) | 3 controller tests | Replaces a Spring bean with a Mockito mock (replaces deprecated `@MockBean`) |
| `@Autowired` | Spring | 3 controller tests | Injects the auto-configured `MockMvc` |
| `@TestPropertySource(properties=...)` | Spring Test | `UrlShortenerControllerTest` | Injects extra properties (e.g. `PREFIX_WEBSITE_DOMAIN`) at class level |
| `when(..).thenReturn(..)` | Mockito | unit + slice tests | Stub: "when this mock method is called, return …" |
| `when(..).thenThrow(..)` | Mockito | unit + slice tests | Stub: "when called, throw …" |
| `verify(..)` | Mockito | unit + slice tests | Assert: "this mock method was called with these args" |
| `verify(mock, times(n)/never())` | Mockito | unit + slice tests | Assert call count (exactly n / zero) |
| `verifyNoInteractions(mock, ...)` | Mockito | `SyncEventProducerTest` | Assert multiple mocks were not touched at all |
| `any()`, `any(Class)`, `anyString()`, `eq(..)` | Mockito | everywhere | Argument matchers for flexible stubbing/verification |
| `ArgumentCaptor.forClass(..)` | Mockito | several tests | Captures the actual argument passed, then asserts its contents |
| `assertEquals/assertTrue/assertNull/assertNotNull` | JUnit 5 | everywhere | Value assertions |
| `assertThrows(..)` | JUnit 5 | service tests | Asserts an exception type is thrown |
| `assertDoesNotThrow(..)` | JUnit 5 | resilience tests | Asserts a call completes without throwing |
| `containsString(..)` | Hamcrest | `UrlShortenerControllerTest` | Substring matcher (used inside `content().string(..)`) |
| `mockMvc.perform(get/post(..))` | Spring MVC Test | controller tests | Fires a simulated HTTP request |
| `.param(..)`, `.header(..)` | Spring MVC Test | controller tests | Adds query params / request headers |
| `.andExpect(status().isOk()/isFound()/...)` | Spring MVC Test | controller tests | Asserts HTTP status code |
| `.andExpect(header().string("Location", ..))` | Spring MVC Test | controller tests | Asserts a response header value |
| `.andExpect(content().string(..))` | Spring MVC Test | controller tests | Asserts the response body |
| `.andExpect(jsonPath("$.x").value(..))` | Spring MVC Test + JSONPath | `AnalyticsControllerTest` | Assertions on parsed JSON |
| `.andExpect(view().name(..))` | Spring MVC Test | `WebControllerTest` | Asserts which view template a controller resolves |

---

## 2. JUnit 5 annotations

### `@Test`
Marks a method as a test. Without it, a method is just a plain helper and is never executed by the runner.

Each abstract "behaviour" of the class under test gets its own `@Test` method. Good method names read like a
sentence: `getOriginalUrl_returnsFromCache_withoutTouchingDatabase` = "given a cached value, getOriginalUrl
returns it and never hits the database."

```java
@Test
void shorten_givesUpAfterCollisionRetries_throws() {
    ...
}
```

### `@DisplayName("...")`
A human-readable label shown in reports and IDE test trees instead of the (ugly but precise) method name.
It costs nothing and makes the suite self-documenting.

```java
@Test
@DisplayName("Gives up with RuntimeException when collisions keep occurring")
void shorten_givesUpAfterCollisionRetries_throws() { ... }
```

### `@ExtendWith(MockitoExtension.class)`
JUnit 5's extension mechanism. An extension plugs into the test lifecycle. `MockitoExtension` does three
things for us:

1. **Auto-initialises `@Mock` fields** — you don't need `MockitoAnnotations.openMocks(this)` in `@BeforeEach`.
2. **Wires `@InjectMocks`** — builds the class under test with the mocks injected.
3. **Enables *strict stubbing*** — if you stub a method that the test never actually calls, the test fails
   with `UnnecessaryStubbingException` (see the concept deep-dive).

```java
@ExtendWith(MockitoExtension.class)
class UrlShortnerServiceTest {
    @Mock private UrlMappingRepository repository;
    @Mock private CacheService cacheService;
    @InjectMocks private UrlShortnerService service;
    ...
}
```

**Also worth knowing (not used here):** `@BeforeEach` / `@AfterEach` (per-test setup/teardown),
`@ParameterizedTest` + `@ValueSource`/`@CsvSource` (run one test many times with different inputs — great
for validation rules like alias format).

---

## 3. Mockito annotations

### `@Mock`
Creates a **stand-in object** for a dependency. A mock has:
- **No real logic** — every method returns default values (`null`, `0`, `false`, empty collections) unless stubbed.
- **Recording** — it remembers every call made on it, so `verify(..)` can later assert what happened.

Used for *collaborators* we don't want to run for real (a repository backed by a database, a Redis template,
a Kafka template, a GeoIP lookup, …):

```java
@Mock
private UrlClickAnalyticsRepository analyticsRepository;
```

### `@InjectMocks`
Creates the **class under test** (the real implementation!) and injects the `@Mock` fields into it.
Important nuance — Mockito picks the injection strategy by what's available:

- **Constructor injection wins** if there is a constructor. `AnalyticsService` only has
  `AnalyticsService(GeoIPService geoIpService)`, so `@InjectMocks` calls that constructor.
- Otherwise Mockito uses **setter / field injection** by type (then by name). `UrlShortnerService` and
  `KafkaClickConsumer` have no constructors, so mocks are dropped into their `@Autowired` fields.

```java
@Mock private UrlMappingRepository repository;
@Mock private CacheService cacheService;

@InjectMocks
private UrlShortnerService service;   // <- the REAL service, with mocked deps
```

### `@Mock` vs `@MockBean` vs `@MockitoBean` — when to use which
- `@Mock` — **pure unit tests** (no Spring context). Fast, milliseconds.
- `@MockitoBean` / `@MockBean` — **Spring slice/integration tests** where a real bean *inside the Spring
  context* must be replaced by a mock so that only one thing is tested. `@MockitoBean` is the Boot 3.4+
  replacement and is what we use (`org.springframework.test.context.bean.override.mockito.MockitoBean`).

```java
@MockitoBean
private UrlShortnerService service;   // replaces the real @Service bean inside @WebMvcTest
```

`@MockitoBean` works via Spring's **Bean Override** mechanism (`BeanOverrideHandler`), which is why it lives
in `org.springframework.test.context.bean.override.*`. The old `@MockBean`
(`org.springframework.boot.test.mock.bean.*`) is deprecated in Boot 3.4 and removed in Boot 4.

---

## 4. Spring Test annotations

### `@SpringBootTest`
Boots the **entire application context** (real beans: controllers, services, repos, Kafka/Redis config,
auto-configuration). It's an integration smoke test — if the context can't be wired, it fails.

```java
@SpringBootTest
class UrlShortnerApplicationTests {
    @Test
    void contextLoads() { }
}
```

This app's `contextLoads` connects to the real Supabase PostgreSQL (credentials from `.env`, loaded via
`spring.config.import=optional:file:.env[.properties]`). That's why it's the slowest test (~22s) and why it
needs network access — pure unit tests never touch a database.

### `@WebMvcTest(SomeController.class)`
The opposite philosophy: loads only the **web slice** of the context:
- ✅ The specified controller (+ any `@RestController`/`@ControllerAdvice`/filters/MVC config it pulls in)
- ✅ Spring MVC + Jackson + (here) Thymeleaf auto-configuration
- ❌ **No** `@Service`, `@Repository`, JPA/DataSource, Kafka, Redis — nothing the controller depends on
- ❌ No real database, no network access

Because dependencies are absent, every collaborator is replaced with `@MockitoBean`. This makes controller
tests fast and fully deterministic while still exercising the *real* request → handler → response pipeline
(parameter binding, status codes, headers, JSON marshalling, view resolution).

```java
@WebMvcTest(UrlShortenerController.class)     // only this controller is loaded
class UrlShortenerControllerTest {
    @Autowired private MockMvc mockMvc;       // auto-configured by the slice
    @MockitoBean private UrlShortnerService service;
    @MockitoBean private ClientIPService clientIPService;
    @MockitoBean private EventProducer eventProducer;
    ...
}
```

Note: `@WebMvcTest` **auto-configures `MockMvc`**, so the separate `@AutoConfigureMockMvc` annotation is
only needed if you want MockMvc inside a `@SpringBootTest`.

### `@Autowired`
Standard Spring dependency injection — here it pulls the `MockMvc` bean the slice created:

```java
@Autowired
private MockMvc mockMvc;
```

### `@TestPropertySource(properties = "...")`
Adds properties to the *test's* `Environment` only (never touches the real config). Without this, the
`shorten` endpoint would derive its prefix from the request's scheme+host; we forced a stable value so the
response body is assertable:

```java
@WebMvcTest(UrlShortenerController.class)
@TestPropertySource(properties = "PREFIX_WEBSITE_DOMAIN=https://snap.link/")
class UrlShortenerControllerTest { ... }
```

---

## 5. Mockito core APIs

### Stubbing — `when(mock.call(...)).thenReturn(...)` / `.thenThrow(...)`

Set up a mock's *behaviour before* the test exercises the code under test.

```java
// return a fixed value
when(repository.findByOriginalUrl("https://e.com")).thenReturn(Optional.empty());

// throw on demand (used to simulate the "alias already taken" and DB-down cases)
when(service.shortenUrl("https://e.com", "api", null))
        .thenThrow(new IllegalArgumentException("Invalid custom alias"));
```

### Verifying — `verify(mock).method(...)`, `verify(mock, times(n)).method(...)`, `verify(mock, never()).method(...)`

Assert that a mock was **called the way we expect** after the action ran.

```java
// called exactly once with these args
verify(eventProducer).sendClickEvent("abc", "9.9.9.9", "Mozilla/5.0 TestAgent", "https://source.com");

// called exactly 5 times (one failed collision attempt per retry)
verify(repository, times(5)).existsByShortCode(anyString());

// must NOT have been called at all (a redirect we never wanted to record)
verify(eventProducer, never()).sendClickEvent(any(), any(), any(), any());
```

`verifyNoInteractions(a, b)` is the sledgehammer version — proves neither mock was touched:

```java
verifyNoInteractions(analyticsRepository, urlRepository);
```

### Argument matchers — `any()`, `any(Class)`, `anyString()`, `eq(...)`

When you don't know (or don't care about) the exact value, use a matcher:

- `any()` — any object (including `null`)
- `any(Class)` — any instance of that type, e.g. `any(LocalDateTime.class)`, `any(Duration.class)`
- `anyString()` — any non-null `String`
- `eq(value)` — *exactly* this value (needed when mixing exact values with other matchers)

```java
when(analyticsRepository.findClicksByHour(eq("abc"), any(LocalDateTime.class)))
        .thenReturn(hours);
```

**⚡ The golden rule:** if you use *any* matcher for one argument, you must use matchers for **all** arguments
— never mix `eq("abc")` with a raw `"abc"`. Mixing them makes Mockito throw
`InvalidUseOfMatchersException`. That's exactly why the examples above use `eq(...)` together with `any(...)`.

### `ArgumentCaptor` — capture the actual argument, then inspect it

Sometimes you must assert on the *contents* of an object that was passed to a mock. A captor grabs it for
you. Classic 3-step:

```java
// 1. create the captor for the argument type
ArgumentCaptor<UrlMapping> captor = ArgumentCaptor.forClass(UrlMapping.class);

// 2. verify the call, letting the captor capture the argument
verify(repository).save(captor.capture());

// 3. assert on the captured value
assertEquals("https://example.com", captor.getValue().getOriginalUrl());
assertEquals("mycode", captor.getValue().getShortCode());
```

Also used to prove the correct *timestamp* was forwarded (`ArgumentCaptor<LocalDateTime>`), and to count
batch persistence (`ArgumentCaptor<List<UrlClickAnalytics>>`).

**`ArgumentCaptor` vs `eq(...)`:** `eq(value)` asserts the argument **equals** a known value up front;
`ArgumentCaptor` lets you inspect an argument **after** the call when you can't know it in advance
(a timestamp created inside the service, a built entity, etc.).

### `lenient()` — optional escape hatch
With `MockitoExtension`'s strict stubbing, a stub that is never used fails the test
(`UnnecessaryStubbingException`). It's a **test bug** — an unused stub usually means the test isn't doing
what you think. `lenient()` exists for the rare legit case; we never needed it because every stub is used.

> Mockito also offers `@Spy` (partially real object with selected methods overridden) and
> `@Captor` (field-based captor). Not used here — mocks are sufficient for this suite.

---

## 6. MockMvc walkthrough (real code)

`MockMvc` simulates a full HTTP round-trip **without a server**. A controller test is a fluent chain:
`perform(request) → andExpect(matcher)`. `MockMvcRequestBuilders` builds requests; `MockMvcResultMatchers`
builds the matchers.

### A redirect that emits a click event (from `UrlShortenerControllerTest`)

```java
mockMvc.perform(get("/abc")                                   // GET /abc
                .header("User-Agent", "Mozilla/5.0 TestAgent")
                .header("Referer", "https://source.com"))
        .andExpect(status().isFound())                        // 302
        .andExpect(header().string("Location", "https://target.com"));

verify(eventProducer).sendClickEvent("abc", "9.9.9.9", "Mozilla/5.0 TestAgent", "https://source.com");
```

### A POST with query parameters (from `UrlShortenerControllerTest`)

```java
mockMvc.perform(post("/api/shorten")
                .param("url", "https://example.com")
                .param("alias", "myalias")
                .param("expiresAt", "2026-05-01T12:00:00"))
        .andExpect(status().isOk())
        .andExpect(content().string("https://snap.link/myalias"));
```

`.param(name, value)` → becomes a query string (and binds to `@RequestParam`); `.header(name, value)` is
used for `User-Agent`, `Referer`, `Host`, etc.

### Every matcher family used in the suite

| Matcher | What it asserts | Example |
|---|---|---|
| `status().isOk() / isBadRequest() / isNotFound() / isFound()` | HTTP status code (200/400/404/302) | `status().isFound()` |
| `header().string("Location", v)` | One response header equals a value | `header().string("Location", "https://target.com")` |
| `content().string(v)` / `content().string(containsString(v))` | Exact / substring of the body | `content().string(containsString("Invalid custom alias"))` |
| `jsonPath("$.x").value(v)` | A field in the parsed JSON body | `jsonPath("$.totalClicks").value(15)` |
| `view().name("index")` | The resolved view/template name (MVC pages) | `view().name("index")` |

> `andReturn()` returns an `MvcResult` after the assertion chain, which gives access to the raw
> response/model/view if you ever need to inspect more. Not needed here.

---

## 7. JSONPath cheat-sheet (as used in `AnalyticsControllerTest`)

```java
.andExpect(jsonPath("$.totalClicks").value(15))                    // top-level field equals 15
.andExpect(jsonPath("$.recentClicks.length()").value(10))          // array size is 10 (the cap)
.andExpect(jsonPath("$.clicksByCountry[0][0]").value("India"))     // array-of-arrays: row 0, col 0
.andExpect(jsonPath("$.clicksByDevice[0][1]").value(10))           // row 0, col 1
```

- `$.field` — navigate by key
- `[index]` — array index; numeric values come back as JSON numbers, strings as strings
- `.length()` — array size function
- `.value(15)` compares with equality; `.exists()` just checks the path is present (not null)

> Real bug we hit while writing these tests: the controller returns the key `clicksByDevice` (not
> `clicksByDeviceType`), and the first version of the test asserted the wrong key — the test itself caught
> a mismatch between the API contract and the test's assumption. That's the point of the cap-on-10 and
> shape tests.

---

## 8. JUnit assertions used (with what they prove)

| Assertion | Proves | Example usage |
|---|---|---|
| `assertEquals(expected, actual)` | Two values are equal | short code returned is `"mycode"` |
| `assertTrue(boolexpr)` | A condition holds | `ex.getMessage().contains("already used")` |
| `assertNull(actual)` / `assertNotNull(actual)` | A value is null / not null | `buildAnalyticsEntity(null)` returns null |
| `assertThrows(T.class, lambda)` | Calling the lambda throws exactly `T` | `assertThrows(IllegalArgumentException.class, () -> service.shortenUrl(...))` |
| `assertDoesNotThrow(lambda)` | The lambda completes normally (no exception escapes) | Redis-down / DB-down paths must *not* bubble up |

```java
assertThrows(RuntimeException.class, () -> service.shortenUrl("https://e.com", null, null));
assertDoesNotThrow(() -> producer.sendClickEvent("abc", "1.2.3.4", null, null));
```

**Hamcrest:** `containsString("...")` is a Hamcrest matcher (Spring's `content()`/`jsonPath()`
assertions are built on Hamcrest), letting you assert *substring* containment:
`content().string(containsString("Invalid custom alias"))`.

---

## 9. Concept deep-dives

### Mock vs Spy
- **Mock:** empty puppet. Every method returns defaults unless stubbed. Total isolation.
- **Spy:** real object with optional `when(...).thenCallRealMethod()` overrides. You keep real logic but
  intercept specific calls. Spies are for legacy/partial mocking; mocks are the cleaner default.

### Pure unit test vs slice test vs full integration test
| | Pure unit | Slice (`@WebMvcTest`) | Full context (`@SpringBootTest`) |
|---|---|---|---|
| Spring context | none | web layer only | everything |
| DB / Kafka / Redis | no | no | yes (real Supabase) |
| Speed | milliseconds | seconds | tens of seconds |
| What you prove | one class's logic | HTTP layer (binding, status, JSON, views) | everything wires together |
| Used for | services & caches & events | the 3 controllers | context smoke test |

Layer cake theory: unit tests prove *logic*; slice tests prove *HTTP plumbing*; the context test proves
*integration*. Each level is fast enough to run on every commit.

### Why `@WebMvcTest(Class)` takes an argument
Without the class argument, `@WebMvcTest` scans and loads **every** controller in the app — that pulls in
`UrlShortenerController`, `AnalyticsController` and `WebController` and all of *their* dependencies. Passing
the class scopes the slice to one controller (plus whatever it references), which is faster and more
isolated.

### Why we force properties with `@TestPropertySource`
`UrlShortenerController.shorten()` reads `PREFIX_WEBSITE_DOMAIN` from `Environment` and only falls back to
`scheme://host` when it's blank. Running the real config, the value could differ between environments;
forcing it makes the expected response body (`https://snap.link/...`) exactly assertable.

### Strict stubbing — why unused stubs fail
`MockitoExtension` defaults to `Strictness.STRICT_STUBS`:
1. **Unused stubs** → `UnnecessaryStubbingException` (catches copy-paste test bugs).
2. **Wrong argument types** → `PotentialStubbingProblem`/`ArgumentMismatchException` instead of silent pass.
3. **Loose matchers** (e.g. `any()` where `eq("x")` was stubbed) → clear mismatch errors.

This is why each test stubs *only* what that test actually triggers — a feature, not a flaw.

### Why the `Object[]` breakdown lists needed typed variables
Spring Data `@Query` methods like `findClicksByCountry` return `List<Object[]>`. Writing
`thenReturn(List.of(new Object[]{...}))` triggered a javac **generics inference failure** (nested arrays +
varargs). The fix — build the list explicitly — is the pattern you'll see in `AnalyticsControllerTest`:

```java
List<Object[]> countries = new ArrayList<>();
countries.add(new Object[]{"India", 8L});
countries.add(new Object[]{"USA", 7L});
when(analyticsRepository.findClicksByCountry("abc")).thenReturn(countries);
```

### Why checked exceptions needed `throws` on test methods
`objectMapper.readValue(String, Class)` declares checked `JsonProcessingException`. Mockito stubbing calls
that method *at test-write time*, so the test methods simply declare `throws JsonProcessingException`.

---

## 10. Bonus: production annotations the tests exercise (quick cheat-sheet)

The tests exercise these real annotations; interviewers often ask about them, so have a crisp one-liner:

| Annotation | Meaning |
|---|---|
| `@RestController` | `@Controller` + implicit `@ResponseBody` — every handler's return value is serialized to JSON. `@Controller` returns a **view/template name** instead (see `WebController`). |
| `@GetMapping("/api/shorten")`, `@PostMapping(..)`, `@RequestMapping("/api/analytics")` | Route mapping: `@PostMapping` used for the mutating shortener endpoint; `@GetMapping` for reads/redirects. |
| `@RequestParam(required = false)` | Binds a query parameter; optional params (`alias`, `expiresAt`) need `required=false`. |
| `@PathVariable` | Binds a URL segment: `GET /{shortCode}` → `@PathVariable String shortCode`. |
| `@DateTimeFormat(iso = ISO.DATE_TIME)` | Converts an ISO string (e.g. `2026-05-01T12:00:00`) into `LocalDateTime`. The controller test proves this conversion works end-to-end. |
| `@ExceptionHandler(IllegalArgumentException.class)` | Controller-level error mapper: validation/alias-conflict exceptions become HTTP 400 with the message in the body. |
| `@Transactional` | All-or-nothing commit — e.g. analytics `save` + `incrementClickCountBy` commit together (url_shortner service + event producers). |
| `@ConditionalOnProperty(name="app.features.kafka.enabled", ...)` | Feature flag: swaps `SyncEventProducer` ⇄ `KafkaClickProducer` and cache impls at runtime based on config. |
| `@Service`, `@Repository` | Stereotypes; `@Repository` also lets translation of DB exceptions happen. |
| Lombok `@Data`, `@NoArgsConstructor` | Generate getters/setters/equals/hashCode/toString + a no-arg constructor. |
| `@Entity`, `@Table(name=..)`, `@Column`, `@PrePersist` | JPA mapping of `UrlMapping` / `UrlClickAnalytics` to the `url_mapping` / `url_click_analytics` tables (schema auto-created via `ddl-auto=update`). |
| `@Modifying` + `@Query("UPDATE ...")` | Custom bulk update (the `incrementClickCountBy` click counter). |
| `@KafkaListener(topics = "click-events", ...)` | Consumer entry point for the async click pipeline (used only when Kafka is enabled). |

---

## 11. Interview Q&A (answer these out loud, in 1–2 sentences)

1. **"What does `@WebMvcTest` load?"** Only the web slice: the given controller, Spring MVC/Jackson, and
   Thymeleaf config. No services, JPA, DataSource, Kafka, or Redis — so collaborators must be
   `@MockitoBean`.

2. **"`@MockBean` vs `@MockitoBean`?"** Same job (replace a Spring bean with a Mockito mock in a test
   context), but `@MockitoBean` is the Boot 3.4+ bean-override API (`org.springframework.test.context
   .bean.override.mockito`); `@MockBean` is deprecated.

3. **"`@Mock` vs `@MockBean`?"** `@Mock` is used in pure unit tests with no Spring context;
   `@MockBean`/`@MockitoBean` replaces a real bean inside a running Spring test context.

4. **"When does `@InjectMocks` use constructor injection?"** When the class under test has a constructor —
   Mockito prefers it; otherwise it falls back to field/setter injection (our `UrlShortnerService` and
   `KafkaClickConsumer` are field-injected).

5. **"Why 'all matchers or no matchers'?"** Mockito can't tell whether a raw value is a literal or a
   matcher, so mixing them raises `InvalidUseOfMatchersException`. `eq(...)` exists specifically so you can
   intermix exact values with `any(...)`.

6. **"What is strict stubbing?"** `MockitoExtension` fails the test when you stub something that's never
   called (`UnnecessaryStubbingException`). It catches tests that stub the wrong thing or don't exercise
   the code they think they do.

7. **"`verify(eq(value))` vs `ArgumentCaptor`?"** `eq` asserts the argument equals a known value; a captor
   grabs and inspects an argument you can't know in advance (e.g. a timestamp computed inside the service).

8. **"Why `@TestPropertySource` here?"** To force `PREFIX_WEBSITE_DOMAIN` to a fixed value so the HTTP
   response body from `POST /api/shorten` is exactly assertable instead of depending on request host.

9. **"How do your tests prove resilience?"** `assertDoesNotThrow` + swallowed-error paths: Redis down,
   DB down, Kafka serialization failure, malformed batch events — each is tested to degrade gracefully
   (return null / skip row / log) and never break the redirect flow.

10. **"What did the tests catch while you wrote them?"** A real API contract mismatch: the controller
    returns `clicksByDevice`, not `clicksByDeviceType` — an assertion on actual JSON found it. JSONPath
    assertions validate the *real* serialized payload, not just method return values.

---

*End of revision guide. Combine with `TESTING.md` (what the 39 tests verify) for a complete interview
pack.*