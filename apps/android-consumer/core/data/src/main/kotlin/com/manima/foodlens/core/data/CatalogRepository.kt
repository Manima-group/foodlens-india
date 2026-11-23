package com.manima.foodlens.core.data

import com.manima.foodlens.core.network.FoodLensClient
import com.manima.foodlens.core.network.model.AlternativesDto
import com.manima.foodlens.core.network.model.AnalysisExplainDto
import com.manima.foodlens.core.network.model.CategoryNodeDto
import com.manima.foodlens.core.network.model.CompareDto
import com.manima.foodlens.core.network.model.IngredientCardDto
import com.manima.foodlens.core.network.model.PageDto
import com.manima.foodlens.core.network.model.ProductSummaryDto
import com.manima.foodlens.core.network.model.SourcesDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Read-only catalog calls that need no offline copy beyond the product cache. */
@Singleton
class CatalogRepository @Inject constructor(private val client: FoodLensClient) {
    private val categoryLock = Mutex()
    private var categoryCache: List<CategoryNodeDto>? = null

    suspend fun search(query: String?, category: String?, cursor: String?): Outcome<PageDto<ProductSummaryDto>> =
        client.search(query, category, cursor).toOutcome()

    suspend fun categories(): Outcome<List<CategoryNodeDto>> = categoryLock.withLock {
        categoryCache?.let { return@withLock Outcome.Ok(it) }
        when (val result = client.categories().toOutcome()) {
            is Outcome.Ok -> Outcome.Ok(result.value.items).also { categoryCache = it.value }
            is Outcome.Err -> result
        }
    }

    suspend fun alternatives(productId: String): Outcome<AlternativesDto> = client.alternatives(productId).toOutcome()

    suspend fun compare(productIds: List<String>): Outcome<CompareDto> = client.compare(productIds).toOutcome()

    suspend fun sources(productId: String): Outcome<SourcesDto> = client.sources(productId).toOutcome()

    suspend fun ingredient(id: String): Outcome<IngredientCardDto> = client.ingredient(id).toOutcome()

    suspend fun explain(productId: String): Outcome<AnalysisExplainDto> = client.analysisExplain(productId).toOutcome()

    companion object {
        /** SPEC §8.3: compare takes 2–4 products. */
        const val COMPARE_MIN = 2
        const val COMPARE_MAX = 4
    }
}
