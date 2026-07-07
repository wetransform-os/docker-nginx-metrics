# Automated Image Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Automated tests (runnable locally via `./gradlew test` and in CI on PRs) that build the `wetransform/nginx-metrics` Docker image, run it, and verify accessibility and correct metric recording.

**Architecture:** A minimal Gradle build (Groovy DSL, wetransform conventions plugin) whose only job is running Kotlin JUnit tests. A `buildDockerImage` task builds the image with a `:test` tag before the `test` task runs. Tests use Testcontainers to start the image (and, for the proxy scenario, an upstream container on a shared network) and assert on the Prometheus text output of the exporter on port 6387.

**Tech Stack:** Gradle 9.6.1 (Groovy DSL), Kotlin 2.3.21, JUnit Jupiter 6.1.1, Testcontainers 1.21.4, `to.wetransform.conventions` 3.0.0, JDK Temurin 21, mise.

**Spec:** `docs/superpowers/specs/2026-07-07-automated-image-tests-design.md`

## Global Constraints

- Gradle config in **Groovy DSL** (`build.gradle`, `settings.gradle`) — mirror conversion-gdal; test code in **Kotlin**.
- Plugin versions: `to.wetransform.conventions` **3.0.0**, `to.wetransform.settings.default` **3.0.0**, `org.jetbrains.kotlin.jvm` **2.3.21**.
- Version catalog file must be named exactly `gradle/test-libs.versions.toml` (the settings plugin registers it as `testLibs`). Versions: junit `6.1.1`, testcontainers `1.21.4`, slf4j `2.0.18`.
- Java toolchain: **21** (Temurin locally via mise).
- Docker image tag used by tests: `wetransform/nginx-metrics:test`.
- CI reusable workflow pin: `wetransform/gha-workflows/.github/workflows/gradle-library-check.yml@109404d78d84f07b3bb374a8171f8df4c13a7e88 # v4.1.4`.
- Commits: Conventional Commits, **no** `Co-authored-by` footer, no JIRA reference (none exists for this work). Work happens on branch `automated-image-tests`.
- These tests verify the **existing** image. Expected first-run result of each new test is PASS. If a test FAILS, debug the assumption (container logs are attached to test output) — do not weaken assertions to force a pass without understanding why.
- Sandbox note: Gradle/test runs need network access to `services.gradle.org`, `plugins.gradle.org`, `repo.maven.apache.org`/`repo1.maven.org`, `artifactory.wetransform.to`, Docker Hub (`registry-1.docker.io`, `auth.docker.io`, `production.cloudflare.docker.com`), and `github.com` (s6-overlay download during `docker build`). If a domain is blocked by the proxy, ask the user to allow it.

---

### Task 1: Gradle build scaffolding

**Files:**
- Create: `mise.toml`
- Create: `.gitignore`
- Create: `settings.gradle`
- Create: `gradle/test-libs.versions.toml`
- Create: `build.gradle`
- Create (generated): `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`

**Interfaces:**
- Consumes: existing `Dockerfile`, `nginx.conf`, `default.yml`, `services.d/` (Docker build context).
- Produces: `./gradlew test` entry point; Gradle task `buildDockerImage` producing local image `wetransform/nginx-metrics:test`; `testLibs` catalog aliases `testLibs.junit.jupiter`, `testLibs.testcontainers.junit`, `testLibs.junit.platform.launcher`, `testLibs.slf4j.simple`.

- [ ] **Step 1: Write `mise.toml` and `.gitignore`**

`mise.toml`:

```toml
[tools]
java = "temurin-21"
# gradle = "9.6.1" # Gradle wrapper should be used instead
```

`.gitignore`:

```
.gradle/
build/
.kotlin/
```

- [ ] **Step 2: Write `settings.gradle`**

Same pattern as conversion-gdal (no subprojects here):

```groovy
pluginManagement {
  repositories {
    gradlePluginPortal()
    maven {
      url 'https://artifactory.wetransform.to/artifactory/local'
    }
  }
}

plugins {
  id 'to.wetransform.settings.default' version '3.0.0'
}

rootProject.name = 'docker-nginx-metrics'
```

- [ ] **Step 3: Write `gradle/test-libs.versions.toml`**

Copied from conversion-gdal, minus the unused `groovy-json` entry:

```toml
[versions]
junit = "6.1.1"
testcontainers = "1.21.4"
slf4j = "2.0.18"

[libraries]
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit" }
junit-platform-launcher = { module = "org.junit.platform:junit-platform-launcher" }

testcontainers-junit = { module = "org.testcontainers:junit-jupiter", version.ref = "testcontainers" }

slf4j-simple = { module = "org.slf4j:slf4j-simple", version.ref = "slf4j" }
```

- [ ] **Step 4: Write `build.gradle`**

```groovy
plugins {
  id 'java'
  id 'org.jetbrains.kotlin.jvm' version '2.3.21'
  id 'to.wetransform.conventions' version '3.0.0'
}

wetransform {
  setup()
}

repositories {
  mavenCentral()
}

kotlin {
  jvmToolchain(21)
}

dependencies {
  testImplementation testLibs.junit.jupiter
  testImplementation testLibs.testcontainers.junit
  testRuntimeOnly testLibs.junit.platform.launcher

  testImplementation testLibs.slf4j.simple
}

tasks.register('buildDockerImage', Exec) {
  commandLine 'docker', 'build', '-t', 'wetransform/nginx-metrics:test', '.'
}

test {
  useJUnitPlatform()
  dependsOn buildDockerImage
  // re-run tests when the image definition changes, not only when test code changes
  inputs.files('Dockerfile', 'nginx.conf', 'default.yml')
  inputs.dir('services.d')
}
```

- [ ] **Step 5: Generate the Gradle wrapper**

```bash
cd /workspace
mise install
mise exec gradle@9.6.1 -- gradle wrapper --gradle-version 9.6.1
./gradlew --version
```

Expected: wrapper files created; `./gradlew --version` prints `Gradle 9.6.1`.

If `wetransform { setup() }` or the Kotlin plugin causes a configuration error here, read the error carefully — likely causes are a blocked repository domain (ask the user to allow it) or a conventions-plugin/Kotlin interaction (check what `setup()` configures before changing anything).

- [ ] **Step 6: Verify image build and empty test run**

```bash
./gradlew buildDockerImage
docker image inspect wetransform/nginx-metrics:test --format 'OK {{.Id}}'
./gradlew test
```

Expected: both Gradle invocations end with `BUILD SUCCESSFUL`; `docker image inspect` prints `OK sha256:...`; `test` reports NO-SOURCE/up-to-date (no test classes yet).

- [ ] **Step 7: Commit**

```bash
git add mise.toml .gitignore settings.gradle build.gradle gradle/
git commit -m "build: add Gradle setup for automated image tests"
```

---

### Task 2: Test support code and basic access test

**Files:**
- Create: `src/test/kotlin/to/wetransform/nginxmetrics/support/NginxMetricsContainer.kt`
- Create: `src/test/kotlin/to/wetransform/nginxmetrics/support/Metrics.kt`
- Create: `src/test/kotlin/to/wetransform/nginxmetrics/BasicAccessTest.kt`

**Interfaces:**
- Consumes: image `wetransform/nginx-metrics:test` built by Task 1.
- Produces (used by Tasks 3–7):
  - `class NginxMetricsContainer : GenericContainer<NginxMetricsContainer>` — constants `IMAGE`, `HTTP_PORT = 80`, `METRICS_PORT = 6387`; `fun metricsEndpoint(): URI`; `fun get(path: String, port: Int = HTTP_PORT, headers: Map<String, String> = emptyMap()): HttpResponse<String>`
  - `data class MetricSample(val name: String, val labels: Map<String, String>, val value: Double)`
  - `object Metrics` — `fun fetch(endpoint: URI): List<MetricSample>`; `fun parse(body: String): List<MetricSample>`; extension `fun List<MetricSample>.value(name: String, labels: Map<String, String> = emptyMap()): Double?`; `fun awaitMetric(endpoint: URI, name: String, labels: Map<String, String> = emptyMap(), timeout: Duration = Duration.ofSeconds(10), condition: (Double?) -> Boolean)`

- [ ] **Step 1: Write `NginxMetricsContainer.kt`**

```kotlin
package to.wetransform.nginxmetrics.support

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.containers.wait.strategy.WaitAllStrategy
import org.testcontainers.utility.DockerImageName

/**
 * The nginx-metrics image under test: nginx on port 80, nginxlog-exporter on port 6387.
 */
class NginxMetricsContainer :
  GenericContainer<NginxMetricsContainer>(DockerImageName.parse(IMAGE)) {

  companion object {
    const val IMAGE = "wetransform/nginx-metrics:test"
    const val HTTP_PORT = 80
    const val METRICS_PORT = 6387

    private val log = LoggerFactory.getLogger(NginxMetricsContainer::class.java)
    private val client: HttpClient = HttpClient.newHttpClient()
  }

  init {
    withExposedPorts(HTTP_PORT, METRICS_PORT)
    withLogConsumer(Slf4jLogConsumer(log).withPrefix("nginx-metrics"))
    waitingFor(
      WaitAllStrategy()
        .withStrategy(Wait.forHttp("/").forPort(HTTP_PORT).forStatusCode(200))
        .withStrategy(Wait.forHttp("/metrics").forPort(METRICS_PORT).forStatusCode(200))
        .withStartupTimeout(Duration.ofSeconds(60))
    )
  }

  fun metricsEndpoint(): URI =
    URI.create("http://$host:${getMappedPort(METRICS_PORT)}/metrics")

  /** GET [path] on the mapped [port] of this container and return the raw response. */
  fun get(
    path: String,
    port: Int = HTTP_PORT,
    headers: Map<String, String> = emptyMap(),
  ): HttpResponse<String> {
    val builder = HttpRequest.newBuilder(URI.create("http://$host:${getMappedPort(port)}$path"))
    headers.forEach { (name, value) -> builder.header(name, value) }
    return client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString())
  }
}
```

- [ ] **Step 2: Write `Metrics.kt`**

```kotlin
package to.wetransform.nginxmetrics.support

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class MetricSample(
  val name: String,
  val labels: Map<String, String>,
  val value: Double,
)

/**
 * Minimal client/parser for the Prometheus text exposition format, sufficient for
 * the metrics emitted by nginxlog-exporter (no escaped quotes in label values).
 */
object Metrics {

  private val client: HttpClient = HttpClient.newHttpClient()

  private val sampleLine = Regex("""^([a-zA-Z_:][a-zA-Z0-9_:]*)(?:\{([^}]*)})?\s+(\S+)""")
  private val labelPair = Regex("""([a-zA-Z_][a-zA-Z0-9_]*)="([^"]*)"""")

  fun fetch(endpoint: URI): List<MetricSample> {
    val response = send(endpoint)
    check(response.statusCode() == 200) { "GET $endpoint returned ${response.statusCode()}" }
    return parse(response.body())
  }

  fun parse(body: String): List<MetricSample> =
    body.lineSequence()
      .filter { it.isNotBlank() && !it.startsWith("#") }
      .mapNotNull { line ->
        val match = sampleLine.find(line) ?: return@mapNotNull null
        val (name, labelBlock, value) = match.destructured
        val labels = labelPair.findAll(labelBlock)
          .associate { it.groupValues[1] to it.groupValues[2] }
        value.toDoubleOrNull()?.let { MetricSample(name, labels, it) }
      }
      .toList()

  /** Value of the first sample named [name] whose labels contain all entries of [labels]. */
  fun List<MetricSample>.value(name: String, labels: Map<String, String> = emptyMap()): Double? =
    firstOrNull { sample ->
      sample.name == name && labels.all { (key, value) -> sample.labels[key] == value }
    }?.value

  /**
   * Polls [endpoint] until [condition] holds for the matching sample's value (null while
   * absent). Log tailing makes metric updates asynchronous, so assertions on recorded
   * metrics must poll. Fails with the last /metrics snapshot for diagnosis.
   */
  fun awaitMetric(
    endpoint: URI,
    name: String,
    labels: Map<String, String> = emptyMap(),
    timeout: Duration = Duration.ofSeconds(10),
    condition: (Double?) -> Boolean,
  ) {
    val deadline = System.nanoTime() + timeout.toNanos()
    var lastBody = "<no response>"
    while (System.nanoTime() < deadline) {
      val response = send(endpoint)
      if (response.statusCode() == 200) {
        lastBody = response.body()
        if (condition(parse(lastBody).value(name, labels))) {
          return
        }
      }
      Thread.sleep(250)
    }
    throw AssertionError(
      "Metric $name$labels did not reach the expected condition within $timeout.\n" +
        "Last /metrics snapshot:\n$lastBody"
    )
  }

  private fun send(endpoint: URI): HttpResponse<String> =
    client.send(
      HttpRequest.newBuilder(endpoint).GET().build(),
      HttpResponse.BodyHandlers.ofString(),
    )
}
```

- [ ] **Step 3: Write `BasicAccessTest.kt`**

```kotlin
package to.wetransform.nginxmetrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import to.wetransform.nginxmetrics.support.Metrics
import to.wetransform.nginxmetrics.support.NginxMetricsContainer

class BasicAccessTest {

  @Test
  fun `nginx serves content and the exporter records it`() {
    NginxMetricsContainer().use { container ->
      container.start()

      val response = container.get("/")
      assertEquals(200, response.statusCode())
      assertTrue(response.body().contains("nginx"), "expected the nginx welcome page")

      Metrics.awaitMetric(
        container.metricsEndpoint(),
        "nginx_http_response_count_total",
        mapOf("method" to "GET", "status" to "200"),
      ) { value -> value != null && value >= 1.0 }
    }
  }
}
```

- [ ] **Step 4: Run the test**

```bash
./gradlew test --tests 'to.wetransform.nginxmetrics.BasicAccessTest' --info
```

Expected: PASS (`BUILD SUCCESSFUL`, 1 test completed). On failure, container stdout/stderr appears in the test log via the log consumer — check whether nginx/exporter started and whether the metric name/labels match what `/metrics` actually serves (the failure message contains the full snapshot).

- [ ] **Step 5: Commit**

```bash
git add src/test/kotlin
git commit -m "test: add testcontainers support code and basic access test"
```

---

### Task 3: Metric value recording test

**Files:**
- Create: `src/test/kotlin/to/wetransform/nginxmetrics/MetricsRecordingTest.kt`

**Interfaces:**
- Consumes: `NginxMetricsContainer`, `Metrics.awaitMetric`, `Metrics.fetch`, `List<MetricSample>.value` from Task 2.
- Produces: nothing used by later tasks.

- [ ] **Step 1: Write `MetricsRecordingTest.kt`**

The container's wait strategy already issues a few `GET /` requests during startup, so the 200 count starts non-zero: take a baseline after startup traffic settles and assert exact deltas. No 404s occur during startup, so the 404 count is asserted absolutely.

```kotlin
package to.wetransform.nginxmetrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import to.wetransform.nginxmetrics.support.Metrics
import to.wetransform.nginxmetrics.support.Metrics.value
import to.wetransform.nginxmetrics.support.NginxMetricsContainer

class MetricsRecordingTest {

  @Test
  fun `response counts increment by the exact number of requests per status`() {
    NginxMetricsContainer().use { container ->
      container.start()
      val metrics = container.metricsEndpoint()

      val ok = mapOf("method" to "GET", "status" to "200")
      val notFound = mapOf("method" to "GET", "status" to "404")

      // wait until the wait-strategy startup requests are reflected, then let the
      // exporter settle before taking the baseline
      Metrics.awaitMetric(metrics, "nginx_http_response_count_total", ok) { it != null }
      Thread.sleep(1000)
      val base200 = Metrics.fetch(metrics).value("nginx_http_response_count_total", ok) ?: 0.0

      repeat(5) { assertEquals(200, container.get("/").statusCode()) }
      repeat(3) { assertEquals(404, container.get("/does-not-exist").statusCode()) }

      Metrics.awaitMetric(metrics, "nginx_http_response_count_total", ok) { it == base200 + 5 }
      Metrics.awaitMetric(metrics, "nginx_http_response_count_total", notFound) { it == 3.0 }

      // response time summary is populated for the recorded requests
      Metrics.awaitMetric(metrics, "nginx_http_response_time_seconds_count", ok) {
        it != null && it > 0
      }
    }
  }
}
```

- [ ] **Step 2: Run the test**

```bash
./gradlew test --tests 'to.wetransform.nginxmetrics.MetricsRecordingTest'
```

Expected: PASS. If the exact-delta assertion is flaky (startup request still in flight at baseline time), the failure snapshot shows the counter value — extend the settle sleep only if evidence shows late startup log lines.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/to/wetransform/nginxmetrics/MetricsRecordingTest.kt
git commit -m "test: verify exact response counts per status label"
```

---

### Task 4: Compression test

**Files:**
- Create: `src/test/kotlin/to/wetransform/nginxmetrics/CompressionTest.kt`

**Interfaces:**
- Consumes: `NginxMetricsContainer.get(path, headers)` from Task 2.
- Produces: nothing used by later tasks.

- [ ] **Step 1: Write `CompressionTest.kt`**

`nginx.conf` enables `gzip on` and `brotli on`; the welcome page is `text/html` (the default compressible type for both modules). Java's `HttpClient` neither sets `Accept-Encoding` itself nor decompresses, so the raw `Content-Encoding` header is observable.

```kotlin
package to.wetransform.nginxmetrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import to.wetransform.nginxmetrics.support.NginxMetricsContainer

class CompressionTest {

  @Test
  fun `responses are compressed according to Accept-Encoding`() {
    NginxMetricsContainer().use { container ->
      container.start()

      val brotli = container.get("/", headers = mapOf("Accept-Encoding" to "br"))
      assertEquals(200, brotli.statusCode())
      assertEquals("br", brotli.headers().firstValue("Content-Encoding").orElse("<none>"))

      val gzip = container.get("/", headers = mapOf("Accept-Encoding" to "gzip"))
      assertEquals(200, gzip.statusCode())
      assertEquals("gzip", gzip.headers().firstValue("Content-Encoding").orElse("<none>"))

      val plain = container.get("/")
      assertEquals(200, plain.statusCode())
      assertEquals("<none>", plain.headers().firstValue("Content-Encoding").orElse("<none>"))
    }
  }
}
```

- [ ] **Step 2: Run the test**

```bash
./gradlew test --tests 'to.wetransform.nginxmetrics.CompressionTest'
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/to/wetransform/nginxmetrics/CompressionTest.kt
git commit -m "test: verify brotli and gzip compression"
```

---

### Task 5: Reverse proxy / upstream metrics test

**Files:**
- Create: `src/test/resources/proxy/proxy.conf`
- Create: `src/test/resources/proxy/exporter-proxy.yml`
- Create: `src/test/kotlin/to/wetransform/nginxmetrics/ProxyMetricsTest.kt`

**Interfaces:**
- Consumes: `NginxMetricsContainer`, `Metrics.awaitMetric` from Task 2.
- Produces: nothing used by later tasks.

- [ ] **Step 1: Write the nginx site config `src/test/resources/proxy/proxy.conf`**

Served in addition to the base image's default site (which stays on port 80 for the wait strategy):

```nginx
server {
    listen 8081;

    location / {
        proxy_pass http://upstream;
    }
}
```

- [ ] **Step 2: Write the exporter config `src/test/resources/proxy/exporter-proxy.yml`**

The default config plus a relabel rule turning the parsed `$proxy_host` field (logged as `uhost="..."`) into an `upstream` label. The `format` line must match `nginx.conf`'s `log_format main` exactly.

```yaml
listen:
  port: 6387

namespaces:
  - name: nginx
    format: "$remote_addr - $remote_user [$time_local] \"$request\" $status $body_bytes_sent \"$http_referer\" \"$http_user_agent\" uhost=\"$proxy_host\" uaddr=\"$upstream_addr\" rt=$request_time uct=\"$upstream_connect_time\" uht=\"$upstream_header_time\" urt=\"$upstream_response_time\""
    source:
      files:
        - /var/log/nginx/access.log

    print_log: true

    predate_initialization: 30s

    relabel_configs:
      - target_label: upstream
        from: proxy_host
```

- [ ] **Step 3: Write `ProxyMetricsTest.kt`**

```kotlin
package to.wetransform.nginxmetrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import to.wetransform.nginxmetrics.support.Metrics
import to.wetransform.nginxmetrics.support.NginxMetricsContainer

class ProxyMetricsTest {

  companion object {
    const val PROXY_PORT = 8081
  }

  @Test
  fun `proxied requests record upstream time metrics with the upstream label`() {
    Network.newNetwork().use { network ->
      GenericContainer(DockerImageName.parse("nginx:alpine"))
        .withNetwork(network)
        .withNetworkAliases("upstream")
        .waitingFor(Wait.forListeningPort())
        .use { upstream ->
          upstream.start()

          NginxMetricsContainer()
            .withNetwork(network)
            .withCopyFileToContainer(
              MountableFile.forClasspathResource("proxy/proxy.conf"),
              "/etc/nginx/conf.d/proxy.conf",
            )
            .withCopyFileToContainer(
              MountableFile.forClasspathResource("proxy/exporter-proxy.yml"),
              "/exporter.yml",
            )
            .use { container ->
              container.addExposedPort(PROXY_PORT)
              container.start()

              repeat(3) {
                assertEquals(200, container.get("/", port = PROXY_PORT).statusCode())
              }

              val labels = mapOf(
                "method" to "GET",
                "status" to "200",
                "upstream" to "upstream",
              )
              Metrics.awaitMetric(
                container.metricsEndpoint(),
                "nginx_http_response_count_total",
                labels,
              ) { it != null && it >= 3.0 }
              Metrics.awaitMetric(
                container.metricsEndpoint(),
                "nginx_http_upstream_time_seconds_count",
                labels,
              ) { it != null && it >= 3.0 }
            }
        }
    }
  }
}
```

- [ ] **Step 4: Run the test**

```bash
./gradlew test --tests 'to.wetransform.nginxmetrics.ProxyMetricsTest'
```

Expected: PASS. If the `upstream` label assertion fails, check the failure snapshot for the actual label value — `$proxy_host` is `upstream` for `proxy_pass http://upstream;` but would be `upstream:8080` style if the proxy_pass URL carried a non-default port. If no `upstream`-labeled series exists at all, the relabel rule didn't apply — check exporter startup output in the container log for config errors.

- [ ] **Step 5: Commit**

```bash
git add src/test/resources/proxy src/test/kotlin/to/wetransform/nginxmetrics/ProxyMetricsTest.kt
git commit -m "test: verify upstream metrics in reverse proxy scenario"
```

---

### Task 6: Custom exporter config test

**Files:**
- Create: `src/test/resources/custom/exporter-custom.yml`
- Create: `src/test/kotlin/to/wetransform/nginxmetrics/CustomExporterConfigTest.kt`

**Interfaces:**
- Consumes: `NginxMetricsContainer`, `Metrics.awaitMetric` from Task 2.
- Produces: nothing used by later tasks.

- [ ] **Step 1: Write `src/test/resources/custom/exporter-custom.yml`**

Static label plus a relabel rule extracting the request URI (2nd space-separated token of the `request` field, per the exporter's `split` semantics shown in `default.yml`'s commented examples):

```yaml
listen:
  port: 6387

namespaces:
  - name: nginx
    format: "$remote_addr - $remote_user [$time_local] \"$request\" $status $body_bytes_sent \"$http_referer\" \"$http_user_agent\" uhost=\"$proxy_host\" uaddr=\"$upstream_addr\" rt=$request_time uct=\"$upstream_connect_time\" uht=\"$upstream_header_time\" urt=\"$upstream_response_time\""
    source:
      files:
        - /var/log/nginx/access.log

    labels:
      app: "test-app"

    print_log: true

    predate_initialization: 30s

    relabel_configs:
      - target_label: request_uri
        from: request
        split: 2
```

- [ ] **Step 2: Write `CustomExporterConfigTest.kt`**

```kotlin
package to.wetransform.nginxmetrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.testcontainers.utility.MountableFile
import to.wetransform.nginxmetrics.support.Metrics
import to.wetransform.nginxmetrics.support.NginxMetricsContainer

class CustomExporterConfigTest {

  @Test
  fun `static labels and relabel rules from a custom exporter config are applied`() {
    NginxMetricsContainer()
      .withCopyFileToContainer(
        MountableFile.forClasspathResource("custom/exporter-custom.yml"),
        "/exporter.yml",
      )
      .use { container ->
        container.start()

        assertEquals(404, container.get("/custom-path").statusCode())

        Metrics.awaitMetric(
          container.metricsEndpoint(),
          "nginx_http_response_count_total",
          mapOf(
            "app" to "test-app",
            "method" to "GET",
            "status" to "404",
            "request_uri" to "/custom-path",
          ),
        ) { it != null && it >= 1.0 }
      }
  }
}
```

- [ ] **Step 3: Run the test**

```bash
./gradlew test --tests 'to.wetransform.nginxmetrics.CustomExporterConfigTest'
```

Expected: PASS. On failure, the snapshot in the error message shows which of the expected labels (`app`, `request_uri`) is missing — distinguish "static label not applied" from "relabel rule not applied".

- [ ] **Step 4: Commit**

```bash
git add src/test/resources/custom src/test/kotlin/to/wetransform/nginxmetrics/CustomExporterConfigTest.kt
git commit -m "test: verify custom exporter config with labels and relabeling"
```

---

### Task 7: Service failure behavior test

**Files:**
- Create: `src/test/kotlin/to/wetransform/nginxmetrics/ServiceFailureTest.kt`

**Interfaces:**
- Consumes: `NginxMetricsContainer` from Task 2.
- Produces: nothing used by later tasks.

- [ ] **Step 1: Write `ServiceFailureTest.kt`**

The s6 `finish` scripts (`services.d/*/finish`) run `s6-svscanctl -t` when either service exits, deliberately shutting down the whole container so an orchestrator restarts it. The test verifies this fail-fast behavior.

```kotlin
package to.wetransform.nginxmetrics

import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import to.wetransform.nginxmetrics.support.NginxMetricsContainer

class ServiceFailureTest {

  @Test
  fun `container shuts down when the exporter dies`() {
    NginxMetricsContainer().use { container ->
      container.start()

      val kill = container.execInContainer("pkill", "-f", "prometheus-nginxlog-exporter")
      assertEquals(0, kill.exitCode, "pkill failed: ${kill.stderr}")

      val deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos()
      while (container.isRunning && System.nanoTime() < deadline) {
        Thread.sleep(250)
      }
      assertFalse(
        container.isRunning,
        "container should shut down after the exporter exits (fail-fast finish script)",
      )
    }
  }
}
```

- [ ] **Step 2: Run the test**

```bash
./gradlew test --tests 'to.wetransform.nginxmetrics.ServiceFailureTest'
```

Expected: PASS, with `nginxlog-exporter exited.` and `nginx exited.` visible in the consumed container log. If `pkill` exits non-zero, the process pattern didn't match — `execInContainer("ps", "aux")` shows the actual command line (expected: `/prometheus-nginxlog-exporter -config-file /exporter.yml`).

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/to/wetransform/nginxmetrics/ServiceFailureTest.kt
git commit -m "test: verify container fail-fast when a service dies"
```

---

### Task 8: Full suite, CI workflow, and README

**Files:**
- Create: `.github/workflows/test.yml`
- Modify: `README.md`

**Interfaces:**
- Consumes: `./gradlew test` entry point from Task 1 and all tests from Tasks 2–7.
- Produces: CI job running the whole suite on PRs.

- [ ] **Step 1: Run the complete suite**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`, 6 test classes / 6 tests passed. Fix any cross-test interference before proceeding (each test starts its own container, so there should be none).

- [ ] **Step 2: Write `.github/workflows/test.yml`**

Non-managed workflow (no `tf-` prefix), same pattern as conversion-gdal's `test.yml`:

```yaml
name: Test

on:
  pull_request:
    branches:
      - master
  workflow_dispatch: {}

jobs:
  test:
    uses: wetransform/gha-workflows/.github/workflows/gradle-library-check.yml@109404d78d84f07b3bb374a8171f8df4c13a7e88 # v4.1.4
    with:
      java-version: 21
      multi-module: false
      skip-scan: true
```

- [ ] **Step 3: Validate the workflow file**

```bash
mise exec actionlint@latest -- actionlint .github/workflows/test.yml
```

Expected: no output (no findings). If `actionlint` is unavailable in the sandbox, at minimum `python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/test.yml'))"` must succeed.

- [ ] **Step 4: Commit the workflow**

```bash
git add .github/workflows/test.yml
git commit -m "ci: run automated image tests on pull requests"
```

- [ ] **Step 5: Add a Testing section to `README.md`**

Append to the existing content:

```markdown

Testing
-------

Automated tests build the Docker image and verify it using [Testcontainers](https://java.testcontainers.org/)
(accessibility, metric recording, compression, reverse proxy metrics, custom exporter
configuration, fail-fast on service death):

    ./gradlew test

Requirements: Docker and Java 21 (provided via [mise](https://mise.jdx.dev) — `mise install`).
The same tests run in CI on pull requests (`.github/workflows/test.yml`).

For manually running the image locally, `./test.sh` builds it and starts an interactive
container with nginx on port 8080 and metrics on port 6387.
```

- [ ] **Step 6: Commit the README**

```bash
git add README.md
git commit -m "docs: document automated and manual testing"
```

- [ ] **Step 7: Final verification**

```bash
./gradlew test && git log --oneline master..HEAD
```

Expected: `BUILD SUCCESSFUL`; commit list shows the spec/plan docs plus one commit per task above.
