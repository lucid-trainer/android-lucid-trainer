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
        "apiKey: [MongoDB Atlas API key or Other service key]",
        "Content-Type: application/json"
    )
    @POST("action/find")
    suspend fun getDocuments(@Body params: APIRequest): APIResponse

    @Headers(
        "apiKey: [MongoDB Atlas API key or Other service key]",
        "Content-Type: application/json"
    )
    @POST("action/insertOne")
    suspend fun postDeviceRequest(@Body params: APIDeviceRequest): APIInsertResponse

}


