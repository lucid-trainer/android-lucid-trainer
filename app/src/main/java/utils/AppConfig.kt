package utils

import com.google.gson.GsonBuilder
import network.ApiService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object AppConfig {
    // Base url of the api - replace with the endpoint for your environment
    private const val BASE_URL = "https://us-east-1.aws.data.mongodb-api.com/app/data-zxclb/endpoint/data/v1/"

    // create retrofit service
    fun ApiService(): ApiService =
        Retrofit.Builder().baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()
            .create(ApiService::class.java)
}