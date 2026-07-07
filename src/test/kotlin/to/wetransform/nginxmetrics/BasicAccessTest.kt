/*
 * Copyright (c) 2026 wetransform GmbH
 * All rights reserved.
 */
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
