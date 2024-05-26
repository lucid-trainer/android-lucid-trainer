package utils

import database.Reading
import kotlin.math.roundToInt

object EventSleepStage {

    fun getSleepStage(workingReadingList:  ArrayList<Reading>) : String {

        var sleepStage = ""

        if (workingReadingList.size >= 15) {
            val highActiveCnt =
                workingReadingList.map { it -> it.accelMovement }.takeLast(5).filter { it > .325 }.size
            val activeCnt =
                workingReadingList.map { it -> it.accelMovement }.takeLast(5).filter { it > .2 }.size
            val restlessCnt =
                workingReadingList.map { it -> it.accelMovement }.takeLast(4).filter { it > .09 }.size
            val deepCnt =
                workingReadingList.map { it -> it.accelMovement }.takeLast(4).filter { it > .01 }.size
            val lightCnt =
                workingReadingList.map { it -> it.accelMovement }.takeLast(4).filter { it > .02 && it <= .09}.size

            val avgHeartRate =
                workingReadingList.map { it -> it.heartRate }.takeLast(15).take(10).average()

            val recentMove =
                workingReadingList.map { it -> it.accelMovement }.takeLast(10).filter { it > .09 }.size
            val recentHeartRate =  workingReadingList.map { it -> it.heartRate }.takeLast(5)


            //val prevHrAvg  = prevHeartRate.filter { it <= avgHeartRate }.size >= 4
            val stepHrIncrease = recentHeartRate.filter { it > avgHeartRate + 1.25 }.size >= 2 &&
                    recentHeartRate.any { it > avgHeartRate + 2.25 }

            sleepStage = "LIGHT ASLEEP"

            if(highActiveCnt >= 1 && activeCnt >= 2 && stepHrIncrease) {
                sleepStage = "AWAKE"
            } else if(restlessCnt >= 1) {
                sleepStage = "RESTLESS"
            } else if(recentMove == 0 &&  stepHrIncrease) {
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