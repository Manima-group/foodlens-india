package com.manima.foodlens.core.network

import com.manima.foodlens.core.network.model.AnalysisExplainDto
import com.manima.foodlens.core.network.model.ErrorEnvelopeDto
import com.manima.foodlens.core.network.model.MetaConfigDto
import com.manima.foodlens.core.network.model.ProductLookupDto
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>

    data class Failure(val error: ApiError) : ApiResult<Nothing>

    /** No connection; UI shows the offline banner and cached data (SPEC §10.3). */
    data class Offline(val cause: IOException) : ApiResult<Nothing>
}

data class ApiError(val code: ApiErrorCode, val httpStatus: Int, val requestId: String?, val details: JsonObject?) {
    /** SPEC §8.5: 404 carries `details.can_contribute`. */
    val canContribute: Boolean get() = details?.get("can_contribute")?.jsonPrimitive?.booleanOrNull == true
}

/** Headers every request sends (SPEC §10.8). */
data class ClientHeaders(val appVersionCode: Int, val languageTag: () -> String)

internal class HeadersInterceptor(private val headers: ClientHeaders) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val request = original.newBuilder()
            .header("X-App-Version", headers.appVersionCode.toString())
            .header("Accept-Language", headers.languageTag())
            .apply { if (original.header(REQUEST_ID) == null) header(REQUEST_ID, UUID.randomUUID().toString()) }
            .build()
        return chain.proceed(request)
    }

    private companion object {
        const val REQUEST_ID = "X-Request-ID"
    }
}

class FoodLensClient(private val api: FoodLensApi, private val json: Json = NetworkJson) {
    suspend fun metaConfig(): ApiResult<MetaConfigDto> = call { api.metaConfig() }

    suspend fun productByBarcode(gtin14: String): ApiResult<ProductLookupDto> = call { api.productByBarcode(gtin14) }

    suspend fun analysisExplain(productId: String): ApiResult<AnalysisExplainDto> =
        call { api.analysisExplain(productId) }

    private suspend fun <T : Any> call(block: suspend () -> retrofit2.Response<T>): ApiResult<T> {
        val response = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return ApiResult.Offline(e)
        }
        val body = response.body()
        return if (response.isSuccessful && body != null) {
            ApiResult.Success(body)
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
        private const val CONNECT_TIMEOUT_S = 10L
        private const val READ_TIMEOUT_S = 20L

        fun okHttp(headers: ClientHeaders, builder: OkHttpClient.Builder = OkHttpClient.Builder()): OkHttpClient =
            builder
                .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
                .addInterceptor(HeadersInterceptor(headers))
                .build()

        fun create(baseUrl: String, headers: ClientHeaders): FoodLensClient {
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttp(headers))
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
