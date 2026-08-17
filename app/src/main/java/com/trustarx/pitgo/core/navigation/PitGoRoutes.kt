package com.trustarx.pitgo.core.navigation

import com.trustarx.pitgo.features.home.model.ServiceCategoryId

object PitGoRoutes {
    const val Home = "home"
    const val CategoryIdArgument = "categoryId"
    const val ProviderSearch = "provider-search/{$CategoryIdArgument}"

    fun providerSearch(categoryId: ServiceCategoryId): String =
        "provider-search/${categoryId.routeValue}"

    fun parseCategoryId(value: String?): ServiceCategoryId? =
        ServiceCategoryId.fromRouteValue(value)
}
