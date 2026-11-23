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
    fun `guest token is sent as bearer and 304 maps to NotModified`() = runTest {
        val authed = FoodLensClient.create(
            baseUrl = server.url("/").toString(),
            headers = ClientHeaders(appVersionCode = 7, languageTag = { "en" }),
            tokens = { "tok123" }
        )
        server.enqueue(MockResponse(code = 304))
        assertEquals(ApiResult.NotModified, authed.productDetail("p1", etag = "\"abc\""))
        val request = server.takeRequest()
        assertEquals("Bearer tok123", request.headers["Authorization"])
        assertEquals("\"abc\"", request.headers["If-None-Match"])
    }

    @Test
    fun `base url override and upload use only the issued path`() = runTest {
        val rebased = FoodLensClient.create(
            baseUrl = FoodLensClient.PLACEHOLDER_BASE_URL,
            headers = ClientHeaders(appVersionCode = 7, languageTag = { "en" }),
            baseUrlProvider = { server.url("/").toString() }
        )
        server.enqueue(MockResponse(code = 204))
        val upload = com.manima.foodlens.core.network.model.PhotoUploadDto(
            photoId = "ph1",
            uploadUrl = "http://10.0.2.2:8000/api/v1/uploads/tok",
            uploadMethod = "PUT",
            uploadHeaders = mapOf("Content-Type" to "image/jpeg"),
            expiresAt = "2026-11-23T00:00:00Z"
        )
        assertTrue(rebased.upload(upload, byteArrayOf(1, 2, 3)) is ApiResult.Success)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/uploads/tok", request.url.encodedPath)
        assertEquals("image/jpeg", request.headers["Content-Type"])
    }

    @Test
    fun `connection failure is reported as offline`() = runTest {
        server.close()
        val result = client.metaConfig()
        assertTrue(result is ApiResult.Offline, "got $result")
    }
}
