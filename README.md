docker-nginx-metrics
====================

Simple attempt at creating a Docker image that includes support for exporting metrics based on the nginx logs.

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
