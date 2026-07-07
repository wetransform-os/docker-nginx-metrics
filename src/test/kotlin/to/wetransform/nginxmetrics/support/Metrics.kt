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
