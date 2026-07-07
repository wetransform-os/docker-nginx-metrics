# Automated image tests — design

Date: 2026-07-07
Status: approved

## Goal

Add automated tests for the `wetransform/nginx-metrics` Docker image that can be run
manually (single local command) and in CI on pull requests. The tests build the image,
run it, and verify that it is accessible and that metrics are recorded properly.

## Approach

Gradle + Kotlin + JUnit 5 + Testcontainers, mirroring the setup in
[conversion-gdal](https://github.com/wetransform-os/conversion-gdal/):

- Gradle build (Groovy DSL) with the `to.wetransform.conventions` plugin (latest version),
  as in conversion-gdal
- Test code written in Kotlin (kotlin-jvm Gradle plugin added on top)
- Test dependencies from the `testLibs` version catalog provided by the conventions
  plugin: JUnit 5 (Jupiter), Testcontainers JUnit integration, JUnit platform launcher,
  slf4j-simple
- Latest stable versions throughout (Gradle wrapper, Kotlin plugin, conventions plugin)

Alternatives considered and rejected: shell-based tests (bats/scripts — weak assertions
for metric values, no reusable CI workflow) and Python/pytest with testcontainers-python
(new toolchain in the org, hand-rolled CI).

## Project layout

```
build.gradle                # Groovy DSL: to.wetransform.conventions + kotlin-jvm plugin
settings.gradle
gradle/wrapper/             # latest Gradle
mise.toml                   # Java (Temurin 21 LTS) for local development
.gitignore                  # build/, .gradle/
src/test/kotlin/...         # tests (Kotlin)
src/test/resources/         # custom nginx/exporter configs for test scenarios
.github/workflows/test.yml  # non-managed CI workflow running the tests
```

## Build wiring

- Task `buildDockerImage` executes `docker build -t wetransform/nginx-metrics:test .`
- The `test` task depends on `buildDockerImage` and declares the Docker context files
  (`Dockerfile`, `nginx.conf`, `default.yml`, `services.d/`) as task inputs, so changes
  to the image definition re-trigger the tests instead of Gradle reporting "up to date"
- Java toolchain: Java 21 (newest LTS known to be handled by the reusable CI workflow;
  bumping later is easy)

## Test infrastructure

Shared support code in a small package under `src/test/kotlin`:

- **`NginxMetricsContainer`** — `GenericContainer` subclass for
  `wetransform/nginx-metrics:test`, exposing ports 80 (nginx) and 6387 (exporter), with
  a wait strategy requiring HTTP 200 on `/` (port 80) and on `/metrics` (port 6387)
- **Metrics helper** — fetches `/metrics` and parses the Prometheus text format into
  `(name, labels, value)` triples; provides a polling assertion (`awaitMetric(...)`,
  timeout ~10 s) because log tailing → metric update is asynchronous
- Container logs are attached to test output via a Testcontainers log consumer;
  polling assertions include the last-seen `/metrics` snapshot in their failure message

## Test scenarios

1. **Basic access** — container starts; nginx answers on port 80; `/metrics` on port
   6387 responds and contains `nginx_` metrics for the configured namespace.
2. **Metric value recording** — issue a known mix of requests (e.g. 5× a 200 URL,
   3× a missing URL → 404); assert `nginx_http_response_count_total` increments by
   exactly those amounts per `status` label and that response-time metrics
   (`nginx_http_response_time_seconds` etc.) appear.
3. **Reverse proxy / upstream metrics** — Testcontainers `Network` with a trivial
   upstream container (echo/httpbin-style image); a site config mounted into
   `/etc/nginx/conf.d/` proxies to it; assert requests succeed through the proxy and
   upstream-time metrics are recorded with the expected `uhost`-derived labels.
4. **Custom exporter config** — mount a custom `exporter.yml` (static labels plus a
   relabel rule on `request`); assert those labels appear on recorded metrics.
5. **Compression** — requests with `Accept-Encoding: br` and `gzip` receive the matching
   `Content-Encoding` (verifies the brotli/gzip modules work in the assembled image).
6. **Service failure behavior** — kill the exporter process inside the container
   (`execInContainer`); assert the whole container shuts down. The s6 `finish` scripts
   deliberately terminate all services when either nginx or the exporter exits
   (fail-fast, so an orchestrator restarts the container) — the test verifies this
   designed behavior.

## CI

New, non-managed `.github/workflows/test.yml` (same pattern as conversion-gdal):

- Triggers: `pull_request` targeting `master`, plus `workflow_dispatch` for manual runs
- Single job calling the reusable
  `wetransform/gha-workflows/.github/workflows/gradle-library-check.yml`, pinned by SHA
  with a version comment (latest release), with inputs `java-version: 21`,
  `multi-module: false`, `skip-scan: true`
- Docker is available on GitHub-hosted runners, so `buildDockerImage` and Testcontainers
  work as-is
- The managed `tf-check.yml` (plain Dockerfile build) stays untouched and runs in
  parallel

## Local execution

- `./gradlew test` is the single entry point — builds the image and runs all tests
- `mise.toml` provides Java; the Gradle wrapper provides Gradle
- The existing `test.sh` stays as-is for interactively running the image; the README
  gets a short "Testing" section documenting both

## Out of scope

- Changes to the publish/release workflows
- Test execution on pushes to master (the publish workflow already rebuilds the image)
- Coverage or test-reporting tooling
