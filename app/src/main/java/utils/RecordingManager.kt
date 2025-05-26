package utils

import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sound.SoundPoolManager
import java.io.IOException

class RecordingManager {
    private lateinit var soundPoolManager: SoundPoolManager

    companion object {

        @Volatile
        private var INSTANCE: RecordingManager? = null

        fun getInstance(soundPoolManager: SoundPoolManager): RecordingManager {
            synchronized(this) {
                var instance = INSTANCE
                if (instance == null) {
                    instance = RecordingManager()
                    instance.initSoundPoolManager(soundPoolManager)
                    INSTANCE = instance
                }
                return instance
            }
        }

        fun getInstance() : RecordingManager? {
            return INSTANCE
        }
    }

    private fun initSoundPoolManager(soundPoolManager: SoundPoolManager) {
        this.soundPoolManager = soundPoolManager
    }

    private var rcJob: Job? = null
    private var recorder: MediaRecorder? = null

    fun startRecordingTimer(fileName: String) {

        val scope = CoroutineScope(Dispatchers.Default)

        if (rcJob == null || rcJob!!.isCompleted) {
            rcJob = scope.launch {
                delay(timeMillis = 80_000)

                soundPoolManager.playSound("lt_sounds/record/record_soon.ogg", .45F)

                delay(timeMillis = 10_000)

                soundPoolManager.playSound("lt_sounds/record/record.ogg", .45F)

                startRecording(fileName)

                delay(timeMillis = 30_000)

                soundPoolManager.playSound("lt_sounds/record/record_stop.ogg", .45F)

                delay(timeMillis = 5_000)

                stopRecording()
            }

            rcJob = null
        }
    }

    private fun startRecording(fileName: String) {
        try {
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)

                setOutputFile(fileName)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)

                try {
                    prepare()
                } catch (e: IOException) {
                    Log.e("MainActivity", "MediaRecorder prepare() failed")
                    Log.e("MainActivity", e.stackTraceToString())
                }

                Log.d("MainActivity", "MediaRecorder starting recording")
                start()
            }

        } catch (e: Exception) {
            Log.e("MainActivity", "MediaRecorder apply failed")
            Log.e("MainActivity", e.stackTraceToString())
        }
    }

    private fun stopRecording() {
        recorder?.apply {
            Log.d("MainActivity", "MediaRecorder stopping recording")
            stop()
            release()

            Log.d("MainActivity", "MediaRecorder finished recording")
        }
        recorder = null
    }

}