package viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import database.Reading
import database.ReadingDao
import kotlinx.coroutines.delay
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Collections.list


class DocumentViewModel(val dao : ReadingDao) : ViewModel() {

    // Create a Repository and pass the api
    // service we created in AppConfig file
    private val repository = DocumentsRepository(
        AppConfig.ApiService()
    )

    val documentState = MutableStateFlow(
        DocumentApiState(
            Status.INIT,
            APIResponse(listOf()), ""
        )
    )

    //get the starting timestamp
    var startingDateTime = getStartDateTime()

    //the last document stored in the database
    private val lastReading = dao.getLatest()

    var lastTimestamp = Transformations.map(lastReading) { lastReading ->
        lastReading?.timestamp ?: getStartingTimestamp()
    }

    val lastReadingString = Transformations.map(lastReading) { lastReading ->
        if (lastReading == null) "" else  formatReading(lastReading)
    }

    val eventMap = Transformations.map(lastReading) { lastReading ->
        lastReading?.eventMap ?: emptyMap()
    }

    val sleepStage :  MutableLiveData<String> by lazy {
        MutableLiveData<String>("")
    }

    val workingReadingList = ArrayList<Reading>()

    private var isFlowEnabled = false

    fun setFlowEnabled(value: Boolean) {
        isFlowEnabled = value
    }

    // Function to get new Comments
    fun getNewReadings() {

        // ApiCalls takes some time, So it has to be
        // run and background thread. Using viewModelScope
        // to call the api
        viewModelScope.launch {

            // Collecting the data emitted
            // by the function in repository
            while (isFlowEnabled) {

                // Since Network Calls takes time,Set the
                // initial value to loading state
                documentState.value = DocumentApiState.loading()

                val timestampFilter = lastTimestamp.value ?: "N/A"

                val request = getAPIRequest(timestampFilter)

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
                                //lastTimestamp = reading.timestamp
                                Log.d("DocumentViewModel", "lastReadingTimestamp=$lastTimestamp")

                                val lastInsertId = dao.insert(reading)
                                Log.d("DocumentViewModel", "insertId=$lastInsertId")

                                setSleepStage(reading)
                            }
                        }

                        //set the documents in  the response data
                        documentState.value = DocumentApiState.success(it.data)
                    }
                delay(15000L)
            }

            //the flow is disabled
            documentState.value = DocumentApiState.init()
        }
    }

    private fun getAPIRequest(lastTimestamp: String): APIRequest {
        Log.d("DocumentViewModel", "lastTimestamp=$lastTimestamp")

        val request = APIRequest(
            "[Mongodb Atlas collection]",
            "[Mongodb Atlas data source]",
            "[Mongodb Atlas database]",
            1,
            1,
            lastTimestamp
        )

        val gson = Gson()
        val requestJson: String? = gson.toJson(request, APIRequest::class.java)
        Log.d("DocumentViewModel", "requestJson=$requestJson")
        return request
    }


    // 3 "period" moving average


    private fun getStartDateTime() : LocalDateTime {
        val currDateTime = LocalDateTime.now()

        //start with today at 10pm as starting point
        //var dateTime = LocalDate.now().atTime(20, 0);
        var dateTime = LocalDateTime.now();

        //for debugging, set a specific starting time
        //var dateTime = LocalDate.parse("2023-09-04").atTime(12, 0)

//        if (currDateTime.hour in 0..10) {
//            //but if we're in the morning hours set it to yesterday
//            dateTime = LocalDate.now().minusDays(1).atTime(22, 0)
//        }

        return dateTime
    }

    private fun getStartingTimestamp() : String {
        startingDateTime = getStartDateTime()
        return startingDateTime.format(DateTimeFormatter.ofPattern("yyy-MM-dd'T'HH:mm:ss.SSS"))
    }

    private fun formatReading(reading: Reading): String {

        var str = "Date/Time: ${reading.dateTimeFormatted}"
        str += '\n' + "Heart Rate: ${reading.heartRate}"
        str += '\n' + "Heart Rate Var: ${reading.heartRateVar}"
        str += '\n' + "Accel Movement: ${reading.accelMovement}"
        str += '\n' + "Position: ${reading.position}"
        str += '\n' + "Gyro Movement: ${reading.gyroMovement}"
        str += '\n' + "Event: ${reading.event}"
        str += '\n' + "Sleep Status: ${reading.isSleep}" + '\n'
        return str
    }

    private fun setSleepStage(reading: Reading) {
        workingReadingList.add(reading)

        if (workingReadingList.size > 6) {
            val moveCnt =
                workingReadingList.map { it -> it.accelMovement }.takeLast(6).filter { it > 1.25 }.size
            if (reading.isSleep == "awake" || reading.isSleep == "unknown" || moveCnt >= 2) {
                sleepStage.value = "AWAKE"
            } else {
                sleepStage.value = "ASLEEP"
            }
        }
    }
}