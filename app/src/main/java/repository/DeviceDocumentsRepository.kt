package repository

import android.util.Log
import com.google.gson.Gson
import network.ApiService
import network.request.APIDeviceRequest
import network.request.DeviceDocument
import utils.AppConfig
import java.time.LocalDateTime

class DeviceDocumentsRepository(private val apiService: ApiService) {
    suspend fun postDevicePrompt(collection: String, deviceDocument: DeviceDocument) {

        val request = APIDeviceRequest(
            collection,
            "Cluster0",
            "lucid-trainer",
            deviceDocument
        )

        //Log.d("MainActivity", "before call")
        val gson = Gson()
        val requestJson: String? = gson.toJson(request, APIDeviceRequest::class.java)
        //Log.d("MainActivity", "requestJson=$requestJson")

        val response = AppConfig.ApiService().postDeviceRequest(request)
        //Log.d("MainActivity", "response=" + response.insertedId)

    }

}
