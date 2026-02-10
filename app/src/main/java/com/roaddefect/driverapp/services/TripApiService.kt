package com.roaddefect.driverapp.services

import android.util.Log
import com.roaddefect.driverapp.config.ApiConfig
import com.roaddefect.driverapp.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class TripApiService {

    private val client: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Log.d(TAG, message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()


    suspend fun startTrip(userId: String, deviceInfo: String): Result<StartTripResponse> {
        return withContext(Dispatchers.IO) {
            try {
                // Check if API is configured
                if (!ApiConfig.isConfigured()) {
                    Log.e(TAG, "API not configured. Please update ApiConfig.kt with CDK outputs")
                    return@withContext Result.failure(
                        ApiException("API not configured. Please update ApiConfig.kt")
                    )
                }

                val requestJson = JSONObject().apply {
                    put("user_id", userId)
                    put("device_info", deviceInfo)
                }

                val requestBody = requestJson.toString().toRequestBody(jsonMediaType)

                val request = Request.Builder()
                    .url(ApiConfig.Endpoints.START_TRIP)
                    .post(requestBody)
                    .build()

                Log.d(TAG, "Starting trip: POST ${ApiConfig.Endpoints.START_TRIP}")

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()

                    if (!response.isSuccessful) {
                        Log.e(TAG, "Start trip failed: ${response.code} - $responseBody")
                        return@withContext Result.failure(
                            ApiException("HTTP ${response.code}: ${response.message}")
                        )
                    }

                    if (responseBody == null) {
                        Log.e(TAG, "Start trip response body is null")
                        return@withContext Result.failure(
                            ApiException("Empty response from server")
                        )
                    }

                    Log.d(TAG, "Start trip success: $responseBody")

                    // Parse response
                    val json = JSONObject(responseBody)
                    val tripId = json.getInt("trip_id")
                    val expectedKeysJson = json.getJSONObject("expected_keys")

                    val response = StartTripResponse(
                        trip_id = tripId,
                        expected_keys = ExpectedKeys(
                            video_key = expectedKeysJson.getString("video_key"),
                            gps_key = expectedKeysJson.getString("gps_key"),
                            imu_key = expectedKeysJson.getString("imu_key")
                        )
                    )

                    Result.success(response)
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error starting trip", e)
                Result.failure(ApiException("Network error: ${e.message}", e))
            } catch (e: Exception) {
                Log.e(TAG, "Error starting trip", e)
                Result.failure(ApiException("Error starting trip: ${e.message}", e))
            }
        }
    }


    suspend fun completeTrip(
        tripId: Int,
        videoKey: String,
        gpsKey: String,
        imuKey: String,
        videoSize: Long,
        videoDuration: Int,
        gpsPointCount: Int,
        imuSampleCount: Int
    ): Result<CompleteTripResponse> {
        return withContext(Dispatchers.IO) {
            try {
                // Check if API is configured
                if (!ApiConfig.isConfigured()) {
                    Log.e(TAG, "API not configured. Please update ApiConfig.kt with CDK outputs")
                    return@withContext Result.failure(
                        ApiException("API not configured. Please update ApiConfig.kt")
                    )
                }

                val requestJson = JSONObject().apply {
                    put("video_key", videoKey)
                    put("gps_key", gpsKey)
                    put("imu_key", imuKey)
                    put("video_size", videoSize)
                    put("video_duration", videoDuration)
                    put("gps_point_count", gpsPointCount)
                    put("imu_sample_count", imuSampleCount)
                }

                val requestBody = requestJson.toString().toRequestBody(jsonMediaType)

                val request = Request.Builder()
                    .url(ApiConfig.Endpoints.completeTrip(tripId))
                    .post(requestBody)
                    .build()

                Log.d(TAG, "Completing trip: POST ${ApiConfig.Endpoints.completeTrip(tripId)}")

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()

                    if (!response.isSuccessful) {
                        Log.e(TAG, "Complete trip failed: ${response.code} - $responseBody")
                        return@withContext Result.failure(
                            ApiException("HTTP ${response.code}: ${response.message}")
                        )
                    }

                    if (responseBody == null) {
                        Log.e(TAG, "Complete trip response body is null")
                        return@withContext Result.failure(
                            ApiException("Empty response from server")
                        )
                    }

                    Log.d(TAG, "Complete trip success: $responseBody")

                    // Parse response
                    val json = JSONObject(responseBody)
                    val message = json.getString("message")
                    val executionArn = json.optString("execution_arn", null)

                    val response = CompleteTripResponse(
                        message = message,
                        execution_arn = executionArn
                    )

                    Result.success(response)
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error completing trip", e)
                Result.failure(ApiException("Network error: ${e.message}", e))
            } catch (e: Exception) {
                Log.e(TAG, "Error completing trip", e)
                Result.failure(ApiException("Error completing trip: ${e.message}", e))
            }
        }
    }

    companion object {
        private const val TAG = "TripApiService"
    }
}

class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause)
