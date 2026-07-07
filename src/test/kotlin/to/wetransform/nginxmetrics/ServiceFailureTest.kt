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
