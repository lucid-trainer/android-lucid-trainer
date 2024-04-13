package viewmodel

import android.util.Log
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
                                //Log.d("DocumentViewModel", "lastReadingTimestamp=$lastTimestamp")

                                val lastInsertId = dao.insert(reading)
                                //Log.d("DocumentViewModel", "insertId=$lastInsertId")

                                setSleepStage(reading)
                            }
                        }

                        //set the documents in  the response data
                        documentState.value = DocumentApiState.success(it.data)
                    }
                delay(15000L) //DEBUG value change to 3000L
            }

            //the flow is disabled
            documentState.value = DocumentApiState.init()
        }
    }

    private fun getAPIRequest(lastTimestamp: String): APIRequest {
        //Log.d("DocumentViewModel", "lastTimestamp=$lastTimestamp")

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
        //Log.d("DocumentViewModel", "requestJson=$requestJson")
        return request
    }


    // 3 "period" moving average


    private fun getStartDateTime() : LocalDateTime {
        return LocalDateTime.now();

        //for DEBUG, set a specific starting time
        //return LocalDate.parse("2024-04-12").atTime(0,0)
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
        str += '\n' + "Event: ${reading.event}"
        str += '\n' + "Sleep Status: ${reading.isSleep}" + '\n'
        return str
    }

    private fun setSleepStage(reading: Reading) {
        workingReadingList.add(reading)

        val listSize = workingReadingList.size;

        if (workingReadingList.size >= 32) {
            val activeCnt =
                workingReadingList.map { it -> it.accelMovement }.takeLast(4).filter { it > .2 }.size
            val unknownCnt =
                workingReadingList.map { it -> it.accelMovement }.takeLast(4).filter { it > .15 }.size
            val deepCnt =
                workingReadingList.map { it -> it.accelMovement }.takeLast(4).filter { it > .01 }.size
            val lightCnt =
                workingReadingList.map { it -> it.accelMovement }.takeLast(4).filter { it > .02 && it <= .15}.size

            val recentMove =
                workingReadingList.map { it -> it.accelMovement }.takeLast(10).filter { it > .02 }.size
            val prevMove =
                workingReadingList.map { it -> it.accelMovement }.slice(listSize-32..listSize-9).filter { it >= .02 }.size
            val prevHeartCnt =
                workingReadingList.map { it -> it.heartRate }.slice(listSize-8..listSize-5).filter { it <= 59}.size
            val heartCnt =
                workingReadingList.map { it -> it.heartRate }.takeLast(4).filter { it >= 60}.size

            //if (reading.isSleep == "awake" || reading.isSleep == "unknown" || moveCnt >= 2) {
            if(activeCnt >= 2) {
                sleepStage.value = "AWAKE"
            } else if(unknownCnt == 1) {
                sleepStage.value = "UNKNOWN"
            } else if (deepCnt == 0 && lightCnt == 0) {
                if(recentMove == 0 && prevMove > 0 && prevHeartCnt >=2 && heartCnt >= 2) {
                    sleepStage.value = "REM ASLEEP"
                } else {
                    sleepStage.value = "DEEP ASLEEP"
                }
            } else if (deepCnt > 0 && lightCnt == 0) {
                sleepStage.value = "ASLEEP"
            } else {
                sleepStage.value = "LIGHT"
            }

            //Log.d("DocumentViewModel", "${reading.timestamp} setting sleep stage to ${sleepStage.value}")
        }
    }
}