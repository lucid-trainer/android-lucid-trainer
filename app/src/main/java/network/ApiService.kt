package network

import network.request.APIDeviceRequest
import network.request.APIRequest
import network.response.APIInsertResponse
import network.response.APIResponse
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface ApiService {
    @Headers(
        "apiKey: INOIOznQaNVkee3IzFJrMtosokmUF3rDXCA8m0hSsNSUzefm0axpGJ67OuEWWDZC",
        "Content-Type: application/json"
    )
    @POST("action/find")
    suspend fun getDocuments(@Body params: APIRequest): APIResponse

    @Headers(
        "apiKey: INOIOznQaNVkee3IzFJrMtosokmUF3rDXCA8m0hSsNSUzefm0axpGJ67OuEWWDZC",
        "Content-Type: application/json"
    )
    @POST("action/insertOne")
    suspend fun postDeviceRequest(@Body params: APIDeviceRequest): APIInsertResponse

}


