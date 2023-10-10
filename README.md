# Lucid Trainer For Android

An Android application that can integrate with a Fitbit watch using [Fitbit Lucid Trainer](https://github.com/lucid-trainer/fit-lucid-trainer).

The application provides several options for playing continuous white noise audio via speakers or bluetooth headphones, and the ability to play guided meditations to support a lucid dreaming practice. It includes several sample meditation routines using AI generated voice prompts but could be easily updated with other custom routines. It allows for control of meditation prompts and volume via the Fitbit watch application, and automatically lessons the length of the prompts as the night progresses.

Future updates will estimate the probability of the user's REM sleep status based on sensor data from the watch and targeted prompts if the threshold is met. It will also support events back to the watch to incorporate vibrations into the prompting.

# Getting Started

Download and install [Android Studio](https://developer.android.com/studio). Clone this Git repository into a local directory.  Open the project in Android Studio and [build and run](https://developer.android.com/studio/run) the application in an emulator with Studio or [setup](https://developer.android.com/studio/run/device#setting-up) your Android tablet or phone for development mode and [deploy and run](https://developer.android.com/studio/run/device) the application there. The USB option to install directly from Studio without using the ADB command line utility is a fairly easy and straightforward option. Once the application has been deployed, it will appear with a generic Android app icon in your apps list and can be pinned to a home screen for easy access.

The app can be used without integrating with data from the Fitbit app, but to take advantage of that feature a few setting updates are required. This assumes you are using [MongoDb Atlas](https://www.mongodb.com/docs/atlas/getting-started/). There is a free tier of this cloud service that should be sufficient for most users.

* The API key should be set in [ApiService](https://github.com/lucid-trainer/android-lucid-trainer/blob/main/app/src/main/java/network/ApiService.kt).
* The endpoint url in [AppConfig](https://github.com/lucid-trainer/android-lucid-trainer/blob/main/app/src/main/java/utils/AppConfig.kt).
* The collection, datasource and database settings in [DocumentViewModel](https://github.com/lucid-trainer/android-lucid-trainer/blob/main/app/src/main/java/viewmodel/DocumentViewModel.kt)

# Features

There are several offline features that the application supports. A white noise option can be selected from a list and then clicking the Noise button will start continous play of that audio. Selecting a meditation option (SSILD, MILD) and clicking the Prompt button will run the background white noise audio and meditation routine for the options selected. Clicking Stop All stops all audio. The time of night impacts the repetition count of each meditation and therefore the length. Once a meditation routine is finished, it will revert to the continous white noise option selected previously, if any.

There are several additional online features available if integrating with the Fitbit watch application. Toggling the session button enables the integration. The latest sensor data and events are displayed in the center of the screen and are refreshed on some period (15 seconds by default). The data is stored in a local Rooms database, and clicking the Reset button will clear that storage. The watch will send updates every 30 seconds, and will queue and catch up with these updates if connectivity lost with the network (if too far from the Android device and Fitbit companion app typically).

If an integration session is enabled, the application will listen for several events from the watch interface. One updates the audio volume level, which is visible in the volume slider. Another will start one or more meditations if selected on the watch. Finally, if the auto option is selected and the Dream button is clicked on the Fitbit, the application will play whichever meditations are selected. The audio events triggered by the watch are also impacted by the time of night, with shorter repetition/length as the night goes on and limits on the auto feature after a certain time (6 am by default)

https://github.com/lucid-trainer/android-lucid-trainer/assets/125609750/b1d4c872-3be5-4900-ad9d-1fdb506b221e
