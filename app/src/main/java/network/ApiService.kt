package network

import network.request.APIRequest
import network.response.APIResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    @Headers(
        "apiKey: [set api key here]",
        "Content-Type: application/json"
    )
    @POST("action/find")
    suspend fun getDocuments(@Body params: APIRequest): APIResponse

}


