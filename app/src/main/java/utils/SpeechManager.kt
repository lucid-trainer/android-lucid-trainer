package utils

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import presentation.MainActivity
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class SpeechManager() {
    private lateinit var textToSpeech: TextToSpeech

    companion object {

        @Volatile
        private var INSTANCE: SpeechManager? = null

        fun getInstance(context: Context): SpeechManager {
            synchronized(this) {
                var instance = INSTANCE
                if (instance == null) {
                    instance = SpeechManager()
                    instance.initTextToSpeech(context)
                    INSTANCE = instance
                }
                return instance
            }
        }

        fun getInstance() : SpeechManager? {
            return INSTANCE
        }

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

            textToSpeech.setSpeechRate(.8F)
        }
    }

    public fun speakTheTime(eventMessage : String, promptMessage: String = "", isShortPrompt: Boolean = false, volume: Float = 0.5F) {
        val currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH))
        val commenceMessage = if(promptMessage.isNotEmpty()) "Commencing $promptMessage soon." else ""
        val fullMessage = if(isShortPrompt) "It's $currentTime" else
            "$eventMessage detected. $commenceMessage The time is $currentTime"
        Log.d("SpeechManager", "${currentTime} tts $fullMessage")
        val params = Bundle()
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume);

        textToSpeech.speak(fullMessage, TextToSpeech.QUEUE_FLUSH, params, null)
    }

}