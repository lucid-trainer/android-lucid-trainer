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

    fun testPrompting(promptType : String, mBgRawId: Int, playStatus: TextView) {

        val scope = CoroutineScope(Dispatchers.Default)
        val playTier = 3

        promptMonitor.adjPromptVolumeCnt = 0

        if (testJob == null || testJob!!.isCompleted) {
            testJob = scope.launch {
                for (i in 1..10) {
                    val soundList = mutableListOf<String>()
                    soundList.add(promptType + "p")

                    playStatus.text = "Test prompt $i: "

                    Log.d("MainActivity", "playing test prompt $i")

                    //simulate the next prompt in a chain
                    soundPoolManager.playSoundList(soundList, mBgRawId,
                        MainActivity.EVENT_LABEL_PROMPT, playStatus, playTier, i)

                    //simulate activity trigger adjustment down in volume
                    val activityList = listOf(2, 4, 7, 9)
                    if(i in activityList) {
                        promptMonitor.adjPromptVolumeCnt += 1
                        val sound = promptMonitor.getVolAdjustSound()
                        soundPoolManager.playSound(sound, .45F)

                        val adjustVal = .15F * promptMonitor.adjPromptVolumeCnt
                        soundPoolManager.activeFgVolAdj = 1F - adjustVal
                        Log.d("MainActivity", "next prompt should be ${soundPoolManager.activeFgVolAdj} of starting volume");
                    }

                    Log.d("MainActivity", "waiting 90 seconds to start next test prompt");
                    delay(timeMillis = 90000)
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