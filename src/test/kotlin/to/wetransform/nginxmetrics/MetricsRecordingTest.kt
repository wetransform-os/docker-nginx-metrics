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
