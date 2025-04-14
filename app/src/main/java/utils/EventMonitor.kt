package utils

import android.util.Log
import database.Reading

object EventMonitor {

    private var triggerAvgHeartRate = 0.0
    private var triggerAvgHeartRateCnt = 0
    private var stepHrIncrease: Boolean = false

    fun getSleepStage(workingReadingList:  ArrayList<Reading>) : String {

        var sleepStage = ""
        var hour = workingReadingList.last().dateTime?.hour

        val stepHrVal = 2.75
        val stepHrVarLow = .45
        val stepHrVarHigh = .75

        if (workingReadingList.size >= 15) {
            ///activity metrics
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

            val recentMove =
                workingReadingList.map { it -> it.accelMovement }.takeLast(10).filter { it > .15 }.size
            val extendedDeepCnt =
                workingReadingList.map { it -> it.accelMovement }.takeLast(36).filter { it > .02 }.size

            //hr trigger
            val avgHeartRate =
                workingReadingList.map { it -> it.heartRate }.takeLast(15).take(10).filter{it > 0.0}.average()
            val recentHeartRate =  workingReadingList.map { it -> it.heartRate }.takeLast(5)

            if(triggerAvgHeartRateCnt == 0) {
                val hrList = recentHeartRate.joinToString(",")
                //Log.d("MainActivity", "${workingReadingList.last().timestamp} trigger REM avgHR=$avgHeartRate list = $hrList:")
                stepHrIncrease = recentHeartRate.filter { it > avgHeartRate + 1 }.size >= 2 &&
                        recentHeartRate.any { it > avgHeartRate + stepHrVal } && extendedDeepCnt > 0
            } else {
                //Log.d("MainActivity", "${workingReadingList.last().timestamp} " +
                //        "follow-up REM avgHR=$triggerAvgHeartRate list = ${recentHeartRate.last()}")
                stepHrIncrease = recentHeartRate.last() > triggerAvgHeartRate + 1.25
                triggerAvgHeartRateCnt -= 1
            }

            //hrVar trigger
            val avgHeartVarRate =
                workingReadingList.map { it -> it.heartRateVar }.takeLast(15).take(10).average()
            val recentHeartRateVar =  workingReadingList.map { it -> it.heartRateVar }.takeLast(5)
            val currentMoveCnt = workingReadingList.map { it -> it.accelMovement }.takeLast(4).filter { it > .05 }.size
            val stepHrVarIncrease = currentMoveCnt == 0  &&
                    ((extendedDeepCnt > 0 && recentHeartRateVar.filter { it > avgHeartVarRate + stepHrVarLow }.size >= 2) ||
                            recentHeartRateVar.any {it > avgHeartVarRate + stepHrVarHigh})

            sleepStage = "LIGHT ASLEEP"

            if(highActiveCnt >= 1 && activeCnt >= 2 && stepHrIncrease) {
                sleepStage = "AWAKE"
            } else if(restlessCnt >= 1) {
                sleepStage = "RESTLESS"
            } else if(recentMove == 0 && (stepHrIncrease || stepHrVarIncrease)) {
                sleepStage = "REM ASLEEP"
                if(stepHrIncrease && triggerAvgHeartRateCnt == 0) {
                    //Log.d("MainActivity", "${workingReadingList.last().timestamp} setting triggerAvgHeartRateCnt")
                    triggerAvgHeartRate = avgHeartRate
                    triggerAvgHeartRateCnt = 20
                }
            } else if (deepCnt == 0 && lightCnt == 0) {
                sleepStage = "DEEP ASLEEP"
            } else if ((deepCnt > 0 && lightCnt == 0) || recentMove > 1) {
                sleepStage = "ASLEEP"
            }

            //Log.d("MainActivity", "${workingReadingList.last().timestamp} setting sleep stage to $sleepStage")

        }

        return sleepStage;
    }

    fun getLastActivity(workingReadingList:  ArrayList<Reading>) : String {
        var lastActivity = "NONE"

        if(workingReadingList.size >= 1) {
            val lastActivityReading = workingReadingList.map { it -> it.accelMovement }.last()
            if(lastActivityReading >= .325) {
                lastActivity = "HIGH"
            } else if (lastActivityReading >= .225) {
                lastActivity = "MEDIUM"
            } else if (lastActivityReading >= .1) {
                lastActivity = "LIGHT"
            } else if (lastActivityReading >= .02) {
                lastActivity = "TRACE"
            }
        }

        return lastActivity
    }
}