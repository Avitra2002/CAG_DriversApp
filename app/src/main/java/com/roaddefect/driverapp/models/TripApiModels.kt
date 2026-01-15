package com.roaddefect.driverapp.models

data class StartTripRequest(
    val user_id: String,
    val device_info: String
)

data class StartTripResponse(
    val trip_id: Int,
    val expected_keys: ExpectedKeys
)

data class ExpectedKeys(
    val video_key: String,
    val gps_key: String,
    val imu_key: String
)


data class CompleteTripRequest(
    val video_key: String,
    val gps_key: String,
    val imu_key: String,
    val video_size: Long,
    val video_duration: Int,
    val gps_point_count: Int,
    val imu_sample_count: Int
)


data class CompleteTripResponse(
    val message: String,
    val execution_arn: String? = null
)

data class ApiErrorResponse(
    val error: String,
    val message: String? = null
)
