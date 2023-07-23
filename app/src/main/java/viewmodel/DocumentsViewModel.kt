package viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import network.DocumentApiState
import network.Status
import network.request.APIRequest
import network.response.APIResponse
import repository.DocumentsRepository
import utils.AppConfig

class DocumentsViewModel : ViewModel() {

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

    private val _lastTimestamp = MutableLiveData<String>("2023-05-25T22:48:27.354")
    val lastTimestamp : LiveData<String>
        get() = _lastTimestamp

    init {
        // Initiate a starting
        // search with comment Id 1
        getNewDocuments()
    }

    // Function to get new Comments
    fun getNewDocuments() {

        val request = APIRequest(
            "fitdata",
            "Cluster0",
            "lucid-trainer",
            1,
            1,
            _lastTimestamp.value!!
        )

        val gson = Gson()
        val json: String? = gson.toJson(request, APIRequest::class.java)
        Log.d("DocumentsViewModel", "json=$json")


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
                    documentState.value = DocumentApiState.success(it.data)

                    var size = it.data?.documents?.size;
                    if (size != null) {
                        _lastTimestamp.value = it.data?.documents?.get(size-1)?.timestamp
                    }
                }
        }
    }
}
