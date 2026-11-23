package com.manima.foodlens.core.network

import com.manima.foodlens.core.network.model.AlternativesDto
import com.manima.foodlens.core.network.model.AnalysisExplainDto
import com.manima.foodlens.core.network.model.CategoryNodeDto
import com.manima.foodlens.core.network.model.CompareDto
import com.manima.foodlens.core.network.model.ContributionDto
import com.manima.foodlens.core.network.model.ContributionRequest
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
import com.manima.foodlens.core.network.model.ScanEventsRequest
import com.manima.foodlens.core.network.model.SourcesDto
import kotlinx.serialization.json.JsonObject
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

/** Mirrors packages/openapi/openapi.json (SPEC §8). Paths are relative to the configured base URL. */
@Suppress("TooManyFunctions")
interface FoodLensApi {
    @GET("api/v1/meta/config")
    suspend fun metaConfig(): Response<MetaConfigDto>

    @GET("api/v1/products/by-barcode/{code}")
    suspend fun productByBarcode(@Path("code") gtin14: String): Response<ProductLookupDto>

    @GET("api/v1/products/{id}")
    suspend fun productDetail(
        @Path("id") productId: String,
        @Header("If-None-Match") etag: String? = null
    ): Response<ProductDetailDto>

    @GET("api/v1/products/{id}/analysis/explain")
    suspend fun analysisExplain(@Path("id") productId: String): Response<AnalysisExplainDto>

    @GET("api/v1/products/{id}/alternatives")
    suspend fun alternatives(@Path("id") productId: String): Response<AlternativesDto>

    @GET("api/v1/products/{id}/sources")
    suspend fun sources(@Path("id") productId: String): Response<SourcesDto>

    @GET("api/v1/products/compare")
    suspend fun compare(@Query("ids") ids: String): Response<CompareDto>

    @GET("api/v1/search")
    suspend fun search(
        @Query("q") query: String?,
        @Query("category") category: String?,
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?
    ): Response<PageDto<ProductSummaryDto>>

    @GET("api/v1/categories")
    suspend fun categories(): Response<PageDto<CategoryNodeDto>>

    @GET("api/v1/ingredients/{id}")
    suspend fun ingredient(@Path("id") ingredientId: String): Response<IngredientCardDto>

    @POST("api/v1/guest/register")
    suspend fun registerGuest(@Body body: GuestRegisterRequest): Response<GuestRegisterDto>

    @POST("api/v1/scan-events")
    suspend fun scanEvents(@Body body: ScanEventsRequest): Response<JsonObject>

    @POST("api/v1/contributions")
    suspend fun createContribution(@Body body: ContributionRequest): Response<ContributionDto>

    @POST("api/v1/contributions/{id}/photos")
    suspend fun requestPhotoUpload(
        @Path("id") contributionId: String,
        @Body body: PhotoRequest
    ): Response<PhotoUploadDto>

    @PUT
    suspend fun upload(@Url url: String, @Body body: RequestBody): Response<Unit>

    @POST("api/v1/contributions/{id}/photos/{photoId}/complete")
    suspend fun completePhoto(
        @Path("id") contributionId: String,
        @Path("photoId") photoId: String
    ): Response<ContributionDto>

    @POST("api/v1/contributions/{id}/confirm")
    suspend fun confirmContribution(
        @Path("id") contributionId: String,
        @Header("Idempotency-Key") idempotencyKey: String
    ): Response<ContributionDto>

    @GET("api/v1/me/contributions")
    suspend fun myContributions(): Response<List<ContributionDto>>
}
