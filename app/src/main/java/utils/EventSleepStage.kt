package utils

import database.Reading
import kotlin.math.roundToInt

object EventSleepStage {

    fun getSleepStage(workingReadingList:  ArrayList<Reading>) : String {

        var sleepStage = ""

        if (workingReadingList.size >= 32) {
            val highActiveCnt =
                workingReadingList.map { it -> it.accelMovement }.takeLast(5).filter { it > .325 }.size
            val activeCnt =
                workingReadingList.map { it -> it.accelMovement }.takeLast(5).filter { it > .2 }.size
            val restlessCnt =
                workingReadingList.map { it -> it.accelMovement }.takeLast(4).filter { it > .15 }.size
            val deepCnt =
                workingReadingList.map { it -> it.accelMovement }.takeLast(4).filter { it > .01 }.size
            val lightCnt =
                workingReadingList.map { it -> it.accelMovement }.takeLast(4).filter { it > .02 && it <= .15}.size

            val avgHeartRate = workingReadingList.map { it -> it.heartRate }.takeLast(20).take(10).average().roundToInt()

            val recentMove =
                workingReadingList.map { it -> it.accelMovement }.takeLast(10).filter { it > .1 }.size
            val recentActive =
                workingReadingList.map { it -> it.accelMovement }.takeLast(12).filter { it > .2 }.size
            val prevHeartCnt =
                workingReadingList.map { it -> it.heartRate }.takeLast(10).take(5).filter { it <= avgHeartRate}.size
            val heartCnt =
                workingReadingList.map { it -> it.heartRate }.takeLast(5).filter { it > avgHeartRate+1}.size


            sleepStage = "LIGHT ASLEEP"

            if(highActiveCnt >= 1 && activeCnt >= 2 && heartCnt >=2) {
                sleepStage = "AWAKE"
            } else if(restlessCnt >= 1) {
                sleepStage = "RESTLESS"
            } else if(recentActive == 0 && prevHeartCnt >=2 && heartCnt >= 3) {
                sleepStage = "REM ASLEEP"
            } else if (deepCnt == 0 && lightCnt == 0) {
                sleepStage = "DEEP ASLEEP"
            } else if ((deepCnt > 0 && lightCnt == 0) || recentMove > 1) {
                sleepStage = "ASLEEP"
            }
            //Log.d("EventSleepStage", "${reading.timestamp} setting sleep stage to sleepStage"

        }

        return sleepStage;
    }

}