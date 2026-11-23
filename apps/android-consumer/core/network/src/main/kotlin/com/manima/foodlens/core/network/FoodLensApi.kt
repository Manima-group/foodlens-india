package com.manima.foodlens.core.network

import com.manima.foodlens.core.network.model.AnalysisExplainDto
import com.manima.foodlens.core.network.model.MetaConfigDto
import com.manima.foodlens.core.network.model.ProductLookupDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/** Hand-mirrored from SPEC §8 until packages/openapi exists; ApiContractTest guards the shapes. */
interface FoodLensApi {
    @GET("api/v1/meta/config")
    suspend fun metaConfig(): Response<MetaConfigDto>

    @GET("api/v1/products/by-barcode/{code}")
    suspend fun productByBarcode(@Path("code") gtin14: String): Response<ProductLookupDto>

    @GET("api/v1/products/{id}/analysis/explain")
    suspend fun analysisExplain(@Path("id") productId: String): Response<AnalysisExplainDto>
}
