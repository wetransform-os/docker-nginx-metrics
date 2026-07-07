/*
 * Copyright (c) 2026 wetransform GmbH
 * All rights reserved.
 */
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
        .withExposedPorts(80)
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
