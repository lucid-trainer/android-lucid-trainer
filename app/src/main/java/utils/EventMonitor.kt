package utils

import database.Reading

object EventMonitor {

    fun getSleepStage(workingReadingList:  ArrayList<Reading>) : String {

        var sleepStage = ""

        if (workingReadingList.size >= 15) {
            ///activity metrics
            val highActiveCnt =
                workingReadingList.map { it -> it.accelMovement }.takeLast(5).filter { it > .325 }.size
            val activeCnt =
                workingReadingList.map { it -> it.accelMovement }.takeLast(5).filter { it > .2 }.size
            val restlessCnt =
                workingReadingList.map { it -> it.accelMovement }.takeLast(4).filter { it > .11 }.size
            val deepCnt =
                workingReadingList.map { it -> it.accelMovement }.takeLast(4).filter { it > .01 }.size
            val lightCnt =
                workingReadingList.map { it -> it.accelMovement }.takeLast(4).filter { it > .02 && it <= .11}.size

            val recentMove =
                workingReadingList.map { it -> it.accelMovement }.takeLast(10).filter { it > .11 }.size
            val extendedDeepCnt =
                workingReadingList.map { it -> it.accelMovement }.takeLast(36).filter { it > .02 }.size

            //hr trigger
            val avgHeartRate =
                workingReadingList.map { it -> it.heartRate }.takeLast(15).take(10).average()
            val recentHeartRate =  workingReadingList.map { it -> it.heartRate }.takeLast(5)
            val stepHrIncrease = recentHeartRate.filter { it > avgHeartRate + 1 }.size >= 2 &&
                    recentHeartRate.any { it > avgHeartRate + 2 } && extendedDeepCnt > 0

            //hrVar trigger
            val avgHeartVarRate =
                workingReadingList.map { it -> it.heartRateVar }.takeLast(15).take(10).average()
            val recentHeartRateVar =  workingReadingList.map { it -> it.heartRateVar }.takeLast(5)
            val currentMoveCnt = workingReadingList.map { it -> it.accelMovement }.takeLast(4).filter { it > .05 }.size
            val stepHrVarIncrease = currentMoveCnt == 0  &&
                    ((extendedDeepCnt > 0 && recentHeartRateVar.filter { it > avgHeartVarRate + .25 }.size >= 2) ||
                            recentHeartRateVar.any {it > avgHeartVarRate + .5})

            sleepStage = "LIGHT ASLEEP"

            if(highActiveCnt >= 1 && activeCnt >= 2 && stepHrIncrease) {
                sleepStage = "AWAKE"
            } else if(restlessCnt >= 1) {
                sleepStage = "RESTLESS"
            } else if(recentMove == 0 && (stepHrIncrease || stepHrVarIncrease)) {
                sleepStage = "REM ASLEEP"
            } else if (deepCnt == 0 && lightCnt == 0) {
                sleepStage = "DEEP ASLEEP"
            } else if ((deepCnt > 0 && lightCnt == 0) || recentMove > 1) {
                sleepStage = "ASLEEP"
            }

            //Log.d("EventSleepStage", "${workingReadingList.last().timestamp} setting sleep stage to $sleepStage")

        }

        return sleepStage;
    }

    fun getActiveEvent(workingReadingList:  ArrayList<Reading>) : Boolean {
        return (workingReadingList.size >= 1 &&
                workingReadingList.map { it -> it.accelMovement }.last() > .25)
    }
}