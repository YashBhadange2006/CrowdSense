package com.example.ble.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface Screen {
    @Serializable
    object Home : Screen

    @Serializable
    object Search : Screen

    @Serializable
    object InsightsDashboard : Screen

    @Serializable
    data class StationInsights(val geohash: String) : Screen

    @Serializable
    object Dev : Screen
}