/*
 * Copyright (c) 2026 wetransform GmbH
 * All rights reserved.
 */
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
