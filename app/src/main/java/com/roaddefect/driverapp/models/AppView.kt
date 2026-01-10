package com.roaddefect.driverapp.models

enum class AppView {
    DASHBOARD,
    RECORDING,
    TRIP_SUMMARY,
    HEALTH,
    HISTORY,
    UPLOAD_QUEUE
}

enum class TripSummarySource {
    FROM_RECORDING,
    FROM_QUEUE
}
