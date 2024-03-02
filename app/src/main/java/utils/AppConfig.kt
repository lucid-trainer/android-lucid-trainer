package utils

//import android.util.Log
import com.google.gson.GsonBuilder
import network.ApiService
//import okhttp3.Interceptor
//import okhttp3.Interceptor.Chain
//import okhttp3.OkHttpClient
//import okhttp3.Request
//import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
//import java.io.IOException


object AppConfig {
    // Base url of the api - replace with the endpoint for your environment
    private const val BASE_URL = "https://us-east-1.aws.data.mongodb-api.com/app/data-zxclb/endpoint/data/v1/"

    // create retrofit service
    fun ApiService(): ApiService {
//        val httpClient = OkHttpClient.Builder()
//            .addInterceptor(SimpleLoggingInterceptor())
//            .build()

        return Retrofit.Builder().baseUrl(BASE_URL)
 //           .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()
            .create(ApiService::class.java)
    }

//    class SimpleLoggingInterceptor : Interceptor {
//        @Throws(IOException::class)
//        override fun intercept(chain: Chain): Response {
//            val request: Request = chain.request()
//            Log.d("MainActivity", "Intercepted headers: ${request.headers()}, ${request.url()}")
//            return chain.proceed(request)
//        }
//    }
}