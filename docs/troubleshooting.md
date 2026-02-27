# Troubleshooting & Known Issues

Common issues encountered during development and how to fix them.

## 1. Kotlin + Mockito ArgumentCaptor Crashes

**Symptom:** `NullPointerException` when using `ArgumentCaptor.capture()` in Kotlin test code.

**Cause:** Java's `ArgumentCaptor.capture()` returns `null`, which crashes Kotlin's non-nullable parameters.

**Fix:** Always use `org.mockito.kotlin.check {}` instead:
```kotlin
// ❌ WRONG — crashes in Kotlin
val captor = ArgumentCaptor.forClass(String::class.java)
verify(service).doThing(captor.capture())  // NPE!

// ✅ CORRECT — null-safe with mockito-kotlin
verify(service).triggerWorkflow(
    check { workflowId -> assertEquals("pr_created", workflowId) },
    check { subscribers -> assertEquals(1, subscribers.size) },
    check { payload -> assertEquals("title", payload["title"]) },
)
```

## 2. WireMock Static `verify()` Hits Port 8080

**Symptom:** `InvalidInputException: Error parsing response body '404 page not found' for http://localhost:8080/__admin/requests/count`

**Cause:** `WireMock.verify()` (static) uses a default client on port 8080, not the dynamic port your server is on.

**Fix:** Create a `WireMock(port)` client and use `verifyThat()` on the instance:
```kotlin
// ❌ WRONG — uses port 8080
WireMock.verify(postRequestedFor(urlPathEqualTo("/v1/events/trigger")))

// ✅ CORRECT — uses the correct dynamic port
val wireMockClient = WireMock(wireMockServer.port())
wireMockClient.verifyThat(postRequestedFor(urlPathEqualTo("/v1/events/trigger")))
```

Use the `WireMockServer` instance directly for `stubFor()` and `resetAll()`.

## 3. Novu SDK v1.6.0 Base URL Override

**Symptom:** The Novu Java SDK v1.6.0 doesn't expose a public setter for the API base URL.

**Cause:** `NovuConfig` has a private `baseUrl` field. The `Novu` class creates handlers and Retrofit instances at construction time, all caching the original URL.

**Fix:** Use Java reflection to override the URL at three levels:
1. `Novu.novuConfig.baseUrl` — top-level config
2. `Handler.restHandler.novuConfig.baseUrl` — each handler's config copy
3. `Handler.restHandler.retrofit` — rebuild via `oldRetrofit.newBuilder().baseUrl(url).build()`
4. `Handler.eventsApi` (and similar) — regenerate from rebuilt Retrofit via `newRetrofit.create(ApiInterface::class.java)`

See `NovuService.overrideBaseUrl()` for the full implementation.

> **Important:** Just overriding `NovuConfig.baseUrl` is NOT enough. The `Retrofit` instance and its API proxies cache the URL and must be rebuilt too.

## 4. JPA Entity Annotations on Kotlin Data Classes

**Symptom:** `Not a managed type: class com.notifier.router.api.domain.XYZ` when starting Spring context.

**Cause:** Kotlin data classes used as JPA entities need explicit annotations. Spring Data JPA won't auto-detect them.

**Fix:** Add annotations to every domain class referenced by a `JpaRepository`:
```kotlin
@Entity
@Table(name = "users")
data class User(
    @Id val id: UUID,
    val slackId: String,
    // ... fields
)
```

For fields containing JSON (lists, maps), add:
```kotlin
@JdbcTypeCode(SqlTypes.JSON)
@Column(columnDefinition = "jsonb")
val filters: List<Filter> = emptyList()
```

And ensure embedded data classes implement `Serializable`:
```kotlin
data class Filter(
    val field: String,
    val operator: String,
    val value: Any,
) : Serializable
```

## 6. Gradle Daemon Gets Stuck on Windows

**Symptom:** Gradle tasks hang or produce `¿Desea terminar el trabajo por lotes?` prompt.

**Cause:** Previous Gradle daemons remain active and lock resources.

**Fix:**
```powershell
# Stop all daemons first
.\gradlew.bat --stop

# Or run with --no-daemon for one-off reliability
.\gradlew.bat :api:test --no-daemon
```

If the batch prompt appears, answer `S` (Sí) to terminate it.

## 7. EventService Payload Keys

**Symptom:** Tests fail because the payload keys don't match expectations.

**Cause:** `EventService.processEventAsync()` constructs a specific payload from the webhook body. The payload sent to NovuService contains these keys (for `pr_created`):

| Key | Source |
|-----|--------|
| `title` | `pull_request.title` |
| `description` | `pull_request.body` |
| `url` | `pull_request.html_url` |
| `created_at` | `pull_request.created_at` |

It does NOT include `repo` or `author` in the payload — those are used for filter matching only.

## 8. Spring Boot `@MockBean` Deprecation Warning

**Symptom:** Kotlin compiler warning: `'annotation class MockBean' is deprecated`.

**Cause:** Spring Boot 3.4+ deprecated `org.springframework.boot.test.mock.mockito.MockBean` in favor of `org.springframework.test.context.bean.override.mockito.MockitoBean`.

**Status:** Non-blocking warning. Migration can be done when convenient:
```kotlin
// Old (deprecated but still works)
@MockBean private lateinit var novuService: NovuService

// New (Spring Boot 3.4+)
@MockitoBean private lateinit var novuService: NovuService
```
