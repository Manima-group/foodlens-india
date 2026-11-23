package com.manima.foodlens.core.network

import com.manima.foodlens.core.network.model.AlternativesDto
import com.manima.foodlens.core.network.model.AnalysisExplainDto
import com.manima.foodlens.core.network.model.CategoryNodeDto
import com.manima.foodlens.core.network.model.CompareDto
import com.manima.foodlens.core.network.model.ContributionDto
import com.manima.foodlens.core.network.model.ContributionRequest
import com.manima.foodlens.core.network.model.ErrorEnvelopeDto
import com.manima.foodlens.core.network.model.GuestRegisterDto
import com.manima.foodlens.core.network.model.GuestRegisterRequest
import com.manima.foodlens.core.network.model.IngredientCardDto
import com.manima.foodlens.core.network.model.MetaConfigDto
import com.manima.foodlens.core.network.model.PageDto
import com.manima.foodlens.core.network.model.PhotoRequest
import com.manima.foodlens.core.network.model.PhotoUploadDto
import com.manima.foodlens.core.network.model.ProductDetailDto
import com.manima.foodlens.core.network.model.ProductLookupDto
import com.manima.foodlens.core.network.model.ProductSummaryDto
import com.manima.foodlens.core.network.model.ScanEventDto
import com.manima.foodlens.core.network.model.ScanEventsRequest
import com.manima.foodlens.core.network.model.SourcesDto
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T, val etag: String? = null) : ApiResult<T>

    /** 304 for an If-None-Match request: the cached copy is current. */
    data object NotModified : ApiResult<Nothing>

    data class Failure(val error: ApiError) : ApiResult<Nothing>

    /** No connection; UI shows the offline banner and cached data (SPEC §10.3). */
    data class Offline(val cause: IOException) : ApiResult<Nothing>
}

data class ApiError(val code: ApiErrorCode, val httpStatus: Int, val requestId: String?, val details: JsonObject?) {
    /** SPEC §8.5: 404 carries `details.can_contribute`. */
    val canContribute: Boolean get() = details?.get("can_contribute")?.jsonPrimitive?.booleanOrNull == true

    /** SPEC §7: 409 AMBIGUOUS_BARCODE carries the candidates. */
    val candidates: List<Pair<String, String>>
        get() = (details?.get("candidates") as? JsonArray).orEmpty().map {
            val o = it.jsonObject
            o.getValue("gtin14").jsonPrimitive.content to o.getValue("symbology").jsonPrimitive.content
        }
}

/** Headers every request sends (SPEC §10.8). */
data class ClientHeaders(val appVersionCode: Int, val languageTag: () -> String)

/** Supplies the guest/user bearer token; null when none is stored yet. */
fun interface TokenProvider {
    fun token(): String?
}

/** Supplies the API origin, e.g. `http://127.0.0.1:8000`. Read per request so a debug override applies at once. */
fun interface BaseUrlProvider {
    fun baseUrl(): String
}

internal class HeadersInterceptor(private val headers: ClientHeaders, private val tokens: TokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val request = original.newBuilder()
            .header("X-App-Version", headers.appVersionCode.toString())
            .header("Accept-Language", headers.languageTag())
            .apply { if (original.header(REQUEST_ID) == null) header(REQUEST_ID, UUID.randomUUID().toString()) }
            .apply {
                val token = tokens.token()
                if (token != null && original.header("Authorization") == null) header("Authorization", "Bearer $token")
            }
            .build()
        return chain.proceed(request)
    }

    private companion object {
        const val REQUEST_ID = "X-Request-ID"
    }
}

/** Rewrites the placeholder origin to the configured one (lets debug builds point at a LAN IP). */
internal class BaseUrlInterceptor(private val provider: BaseUrlProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val target = provider.baseUrl().toHttpUrlOrNull() ?: return chain.proceed(request)
        val url = request.url.rebase(target)
        return chain.proceed(request.newBuilder().url(url).build())
    }
}

internal fun HttpUrl.rebase(target: HttpUrl): HttpUrl = newBuilder()
    .scheme(target.scheme)
    .host(target.host)
    .port(target.port)
    .build()

/** Retries idempotent GETs on I/O failure: max 2 retries with backoff (SPEC §10.8). */
internal class GetRetryInterceptor(private val sleep: (Long) -> Unit = Thread::sleep) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET") return chain.proceed(request)
        var attempt = 0
        while (true) {
            try {
                return chain.proceed(request)
            } catch (e: IOException) {
                if (attempt >= MAX_RETRIES || chain.call().isCanceled()) throw e
                sleep(BACKOFF_MS shl attempt)
                attempt++
            }
        }
    }

    private companion object {
        const val MAX_RETRIES = 2
        const val BACKOFF_MS = 300L
    }
}

@Suppress("TooManyFunctions")
class FoodLensClient(private val api: FoodLensApi, private val json: Json = NetworkJson) {
    suspend fun metaConfig(): ApiResult<MetaConfigDto> = call { api.metaConfig() }

    suspend fun productByBarcode(gtin14: String): ApiResult<ProductLookupDto> = call { api.productByBarcode(gtin14) }

    suspend fun productDetail(productId: String, etag: String? = null): ApiResult<ProductDetailDto> =
        call { api.productDetail(productId, etag) }

    suspend fun analysisExplain(productId: String): ApiResult<AnalysisExplainDto> =
        call { api.analysisExplain(productId) }

    suspend fun alternatives(productId: String): ApiResult<AlternativesDto> = call { api.alternatives(productId) }

    suspend fun sources(productId: String): ApiResult<SourcesDto> = call { api.sources(productId) }

    suspend fun compare(productIds: List<String>): ApiResult<CompareDto> =
        call { api.compare(productIds.joinToString(",")) }

    suspend fun search(
        query: String?,
        category: String?,
        cursor: String?,
        limit: Int = PAGE_SIZE
    ): ApiResult<PageDto<ProductSummaryDto>> = call {
        api.search(query?.takeIf { it.isNotBlank() }, category, limit, cursor)
    }

    suspend fun categories(): ApiResult<PageDto<CategoryNodeDto>> = call { api.categories() }

    suspend fun ingredient(id: String): ApiResult<IngredientCardDto> = call { api.ingredient(id) }

    suspend fun registerGuest(installId: String): ApiResult<GuestRegisterDto> =
        call { api.registerGuest(GuestRegisterRequest(installId)) }

    suspend fun sendScanEvents(events: List<ScanEventDto>): ApiResult<JsonObject> =
        call { api.scanEvents(ScanEventsRequest(events)) }

    suspend fun createContribution(request: ContributionRequest): ApiResult<ContributionDto> =
        call { api.createContribution(request) }

    suspend fun requestPhotoUpload(contributionId: String, request: PhotoRequest): ApiResult<PhotoUploadDto> =
        call { api.requestPhotoUpload(contributionId, request) }

    /** PUTs to the server-issued upload URL; only its path is used so a debug origin override still applies. */
    suspend fun upload(upload: PhotoUploadDto, bytes: ByteArray): ApiResult<Unit> {
        val contentType = upload.uploadHeaders["Content-Type"] ?: "application/octet-stream"
        val issued = upload.uploadUrl.toHttpUrl()
        val relative = issued.encodedPath.removePrefix("/") + (issued.encodedQuery?.let { "?$it" } ?: "")
        return callUnit { api.upload(relative, bytes.toRequestBody(contentType.toMediaType())) }
    }

    suspend fun completePhoto(contributionId: String, photoId: String): ApiResult<ContributionDto> =
        call { api.completePhoto(contributionId, photoId) }

    suspend fun confirmContribution(contributionId: String, idempotencyKey: String): ApiResult<ContributionDto> =
        call { api.confirmContribution(contributionId, idempotencyKey) }

    suspend fun myContributions(): ApiResult<List<ContributionDto>> = call { api.myContributions() }

    private suspend fun callUnit(block: suspend () -> retrofit2.Response<Unit>): ApiResult<Unit> {
        val response = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return ApiResult.Offline(e)
        }
        return if (response.isSuccessful) {
            ApiResult.Success(Unit)
        } else {
            ApiResult.Failure(parseError(response.code(), response.errorBody()?.string()))
        }
    }

    private suspend fun <T : Any> call(block: suspend () -> retrofit2.Response<T>): ApiResult<T> {
        val response = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return ApiResult.Offline(e)
        }
        if (response.code() == HTTP_NOT_MODIFIED) return ApiResult.NotModified
        val body = response.body()
        return if (response.isSuccessful && body != null) {
            ApiResult.Success(body, response.headers()["ETag"])
        } else {
            ApiResult.Failure(parseError(response.code(), response.errorBody()?.string()))
        }
    }

    private fun parseError(status: Int, raw: String?): ApiError {
        val envelope = raw?.let {
            try {
                json.decodeFromString(ErrorEnvelopeDto.serializer(), it)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        return if (envelope != null) {
            ApiError(
                ApiErrorCode.fromWire(envelope.error.code),
                status,
                envelope.error.requestId,
                envelope.error.details
            )
        } else {
            ApiError(ApiErrorCode.fromStatus(status), status, requestId = null, details = null)
        }
    }

    companion object {
        const val PAGE_SIZE = 20
        private const val HTTP_NOT_MODIFIED = 304
        private const val CONNECT_TIMEOUT_S = 10L
        private const val READ_TIMEOUT_S = 20L

        /** Placeholder origin; [BaseUrlInterceptor] swaps in the configured one per request. */
        const val PLACEHOLDER_BASE_URL = "http://aahar.invalid/"

        fun okHttp(
            headers: ClientHeaders,
            tokens: TokenProvider = TokenProvider { null },
            baseUrl: BaseUrlProvider? = null,
            builder: OkHttpClient.Builder = OkHttpClient.Builder()
        ): OkHttpClient = builder
            .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .apply { if (baseUrl != null) addInterceptor(BaseUrlInterceptor(baseUrl)) }
            .addInterceptor(HeadersInterceptor(headers, tokens))
            .addInterceptor(GetRetryInterceptor())
            .build()

        fun create(
            baseUrl: String,
            headers: ClientHeaders,
            tokens: TokenProvider = TokenProvider { null },
            baseUrlProvider: BaseUrlProvider? = null,
            builder: OkHttpClient.Builder = OkHttpClient.Builder()
        ): FoodLensClient {
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttp(headers, tokens, baseUrlProvider, builder))
                .addConverterFactory(NetworkJson.asConverterFactory("application/json".toMediaType()))
                .build()
            return FoodLensClient(retrofit.create(FoodLensApi::class.java))
        }
    }
}

/** Lenient for forward compatibility; ApiContractTest uses strict decoding to catch drift. */
val NetworkJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
