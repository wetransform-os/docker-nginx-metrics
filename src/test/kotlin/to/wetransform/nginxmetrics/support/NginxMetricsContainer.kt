/*
 * Copyright (c) 2026 wetransform GmbH
 * All rights reserved.
 */
package to.wetransform.nginxmetrics.support

import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.containers.wait.strategy.WaitAllStrategy
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * The nginx-metrics image under test: nginx on port 80, nginxlog-exporter on port 6387.
 */
class NginxMetricsContainer : GenericContainer<NginxMetricsContainer>(DockerImageName.parse(IMAGE)) {

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
        .withStartupTimeout(Duration.ofSeconds(60)),
    )
  }

  fun metricsEndpoint(): URI = URI.create("http://$host:${getMappedPort(METRICS_PORT)}/metrics")

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
