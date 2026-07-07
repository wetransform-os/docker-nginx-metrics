/*
 * Copyright (c) 2026 wetransform GmbH
 * All rights reserved.
 */
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
