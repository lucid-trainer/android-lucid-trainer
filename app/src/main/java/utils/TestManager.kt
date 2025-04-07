package utils

import android.util.Log
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import presentation.MainActivity
import sound.PromptSoundRoutine
import sound.SoundPoolManager


class TestManager {
    private lateinit var soundPoolManager: SoundPoolManager
    private lateinit var promptMonitor: PromptMonitor
    private var testJob: Job? = null

    companion object {

        @Volatile
        private var INSTANCE: TestManager? = null

        fun getInstance(soundPoolManager: SoundPoolManager, promptMonitor: PromptMonitor): TestManager {
            synchronized(this) {
                var instance = INSTANCE
                if (instance == null) {
                    instance = TestManager()
                    instance.initSoundPoolManager(soundPoolManager)
                    instance.initPromptMonitor(promptMonitor)
                    INSTANCE = instance
                }
                return instance
            }
        }

        fun getInstance() : TestManager? {
            return INSTANCE
        }

    }

    private fun initSoundPoolManager(soundPoolManager: SoundPoolManager) {
        this.soundPoolManager = soundPoolManager
    }

    private fun initPromptMonitor(promptMonitor: PromptMonitor) {
        this.promptMonitor = promptMonitor
    }

    fun testPrompting(promptType : String, mBgRawId: Int, mBgLabel: String, playStatus: TextView) {

        val scope = CoroutineScope(Dispatchers.Default)

        promptMonitor.adjPromptVolumeCnt = 0

        if (testJob == null || testJob!!.isCompleted) {
            testJob = scope.launch {
                for (i in 1..10) {
                    val soundList = mutableListOf<String>()
                    soundList.add(promptType + "p")

                    playStatus.text = "Test prompt $i: "

                    Log.d("MainActivity", "playing test prompt $i");

                    soundPoolManager.playSoundList(soundList, mBgRawId, mBgLabel,
                        MainActivity.EVENT_LABEL_PROMPT, playStatus, 1, i)

                    val sound = promptMonitor.getVolAdjustSound()
                    soundPoolManager.playSound(sound, .6F)
                    promptMonitor.adjPromptVolumeCnt += 1

                    Log.d("MainActivity", "waiting 60 seconds to start next test prompt");
                    delay(timeMillis = 30000)
                    yield()

                }
            }
        }
    }

    fun stopPlayingAll(playStatus: TextView) {
        if (testJob != null && testJob!!.isActive) {
            testJob!!.cancel()
            testJob == null

            playStatus.text = ""

            Log.d("MainActivity", "test prompts canceled");
        }
    }



}