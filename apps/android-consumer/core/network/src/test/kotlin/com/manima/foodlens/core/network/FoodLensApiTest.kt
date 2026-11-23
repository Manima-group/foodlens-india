package com.manima.foodlens.core.network

import com.manima.foodlens.core.testing.Fixtures
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FoodLensApiTest {
    private val server = MockWebServer()
    private lateinit var client: FoodLensClient

    @BeforeEach
    fun setUp() {
        server.start()
        client = FoodLensClient.create(
            baseUrl = server.url("/").toString(),
            headers = ClientHeaders(appVersionCode = 7, languageTag = { "hi-IN" })
        )
    }

    @AfterEach
    fun tearDown() = server.close()

    @Test
    fun `by-barcode keeps leading zeros in path and sends required headers`() = runTest {
        server.enqueue(MockResponse(code = 200, body = Fixtures.read("api-examples/product-by-barcode-200.json")))
        val result = client.productByBarcode("08901234567890")
        assertTrue(result is ApiResult.Success)
        val request = server.takeRequest()
        assertEquals("/api/v1/products/by-barcode/08901234567890", request.url.encodedPath)
        assertEquals("7", request.headers["X-App-Version"])
        assertEquals("hi-IN", request.headers["Accept-Language"])
        assertNotNull(request.headers["X-Request-ID"])
    }

    @Test
    fun `404 maps to PRODUCT_NOT_FOUND with details by code`() = runTest {
        val body = """
            {"error": {"code": "PRODUCT_NOT_FOUND", "message": "x",
             "details": {"can_contribute": true}, "request_id": "r1"}}
        """.trimIndent()
        server.enqueue(MockResponse(code = 404, body = body))
        val result = client.productByBarcode("08901234567890") as ApiResult.Failure
        assertEquals(ApiErrorCode.PRODUCT_NOT_FOUND, result.error.code)
        assertEquals("r1", result.error.requestId)
        assertEquals(true, result.error.canContribute)
    }

    @Test
    fun `non-envelope error body falls back to status mapping`() = runTest {
        server.enqueue(MockResponse(code = 503, body = "<html>bad gateway</html>"))
        val result = client.metaConfig() as ApiResult.Failure
        assertEquals(ApiErrorCode.UPSTREAM_UNAVAILABLE, result.error.code)
    }

    @Test
    fun `connection failure is reported as offline`() = runTest {
        server.close()
        val result = client.metaConfig()
        assertTrue(result is ApiResult.Offline, "got $result")
    }
}
