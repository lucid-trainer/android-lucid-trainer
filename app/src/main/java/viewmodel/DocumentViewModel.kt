package viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import database.ReadingDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import network.DocumentApiState
import network.Status
import network.request.APIRequest
import network.response.APIResponse
import network.response.transform
import repository.DocumentsRepository
import utils.AppConfig


class DocumentViewModel(val dao : ReadingDao) : ViewModel() {

    // Create a Repository and pass the api
    // service we created in AppConfig file
    private val repository = DocumentsRepository(
        AppConfig.ApiService()
    )

    val documentState = MutableStateFlow(
        DocumentApiState(
            Status.LOADING,
            APIResponse(listOf()), ""
        )
    )

    //the last document stored in the database
    val lastReading = dao.getLatest()

    init {
        // Initiate a starting search with an initial timestamp
        getNewReadings("2023-05-25T22:48:20.354")
    }
    // Function to get new Comments
    fun getNewReadings(lastTimestamp : String) {

        Log.d("DocumentViewModel", "lastTimestamp=$lastTimestamp")

        val request = APIRequest(
            "fitdata",
            "Cluster0",
            "lucid-trainer",
            1,
            1,
            lastTimestamp
        )

        val gson = Gson()
        val requestJson: String? = gson.toJson(request, APIRequest::class.java)
        Log.d("DocumentViewModel", "requestJson=$requestJson")


        // Since Network Calls takes time,Set the
        // initial value to loading state
        documentState.value = DocumentApiState.loading()

        // ApiCalls takes some time, So it has to be
        // run and background thread. Using viewModelScope
        // to call the api
        viewModelScope.launch {

            // Collecting the data emitted
            // by the function in repository
            repository.getDocuments(request)
                // If any errors occurs like 404 not found
                // or invalid query, set the state to error
                // State to show some info
                // on screen
                .catch {
                    documentState.value =
                        DocumentApiState.error(it.message.toString())
                }
                // If Api call is succeeded, set the State to Success
                // and set the response data to data received from api
                .collect {
                    //first convert any documents returned  into a reading and store in the db
                    var size = it.data?.documents?.size;
                    if (size != null) {
                        it.data?.documents?.transform()?.forEach { reading ->
                            val readingTimestamp = reading.timestamp
                            Log.d("DocumentViewModel", "readingTimestamp=$readingTimestamp")

                            val lastInsertId= dao.insert(reading)
                            Log.d("DocumentViewModel", "insertId=$lastInsertId")
                        }
                    }

                    //set the documents in  the response data
                    documentState.value = DocumentApiState.success(it.data)
                }
        }
    }
}
