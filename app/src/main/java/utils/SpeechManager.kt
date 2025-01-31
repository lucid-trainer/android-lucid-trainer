package utils

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import sound.Sound
import sound.SoundPoolManager
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class SpeechManager() {
    private lateinit var textToSpeech: TextToSpeech
    private var soundRoutineEvents: ArrayDeque<Int> = ArrayDeque()

    companion object {
        const val MINUTES_BETWEEN_ROUTINE_EVENTS = 2

        @Volatile
        private var INSTANCE: SpeechManager? = null

        fun getInstance(context: Context): SpeechManager {
            synchronized(this) {
                var instance = INSTANCE
                if (instance == null) {
                    instance = SpeechManager()
                    instance.initTextToSpeechInstance(context)
                    INSTANCE = instance
                }
                return instance
            }
        }

        fun getInstance() : SpeechManager? {
            return INSTANCE
        }

    }

    private fun initTextToSpeechInstance(context: Context) {
        textToSpeech = initTextToSpeech(context)
    }

    private fun initTextToSpeech(context: Context) = TextToSpeech(context) { i ->
        // if No error is found then only it will run
        if (i != TextToSpeech.ERROR) {
            textToSpeech.language = Locale.US
            val voices: Set<Voice> = textToSpeech.voices
            val voiceList: List<Voice> = ArrayList(voices)

            for(voice in voiceList) {
                if(voice.name.equals("en-US-Standard-G")) {
                    textToSpeech.voice = voice
                }
            }

            textToSpeech.setSpeechRate(.7F)
            textToSpeech.setPitch(1.1F)
        }
    }

    fun setSoundRoutineEvents(speechEventsCount: Int) {
        soundRoutineEvents.clear()

        var eventMinute = LocalDateTime.now().minute
        for (i in 1..speechEventsCount) {
            eventMinute += MINUTES_BETWEEN_ROUTINE_EVENTS
            soundRoutineEvents.add(eventMinute)
        }
    }

    fun handleSoundRoutineEvents() {
        if(soundRoutineEvents.isNotEmpty()){
            var eventMinute = soundRoutineEvents.first()
            if(LocalDateTime.now().minute == eventMinute) {
                speakTheTime()
                soundRoutineEvents.removeFirst()
            }
        }
    }

    fun speakTheTime() {
        speakTheTimeWithMessage("", "", true)
    }

    fun speakTheTimeWithMessage(eventMessage : String, promptMessage: String = "", isShortPrompt: Boolean = false, volume: Float = 0.5F) {
        val currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH))
        val commenceMessage = if(promptMessage.isNotEmpty()) "Commencing $promptMessage soon." else ""
        val fullMessage = if(isShortPrompt) "It's $currentTime" else
            "$eventMessage detected. $commenceMessage The time is $currentTime"
        Log.d("MainActivity", "Speaking ${currentTime} tts $fullMessage")
        val params = Bundle()
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume);

        textToSpeech.speak(fullMessage, TextToSpeech.QUEUE_FLUSH, params, null)
    }

}