package presentation

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.lucidtrainer.databinding.ActivityMainBinding
import database.Reading
import database.ReadingDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import network.Status
import viewmodel.DocumentViewModel
import viewmodel.DocumentViewModelFactory
import java.time.LocalDate
import java.time.LocalDateTime

class MainActivity : AppCompatActivity() {

    // variable to initialize it later
    private lateinit var viewModel: DocumentViewModel

    // create a view binding variable
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // instantiate view binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // initialize viewModel
        val dao = ReadingDatabase.getInstance(application).readingDao
        val viewModelFactory = DocumentViewModelFactory(dao)
        viewModel = ViewModelProvider(
            this, viewModelFactory)[DocumentViewModel::class.java]

        // clear any old data
        purgeOldRecords(viewModel.startingDateTime)

        // Create the observer which updates the latest reading from the database
        val lastTimestampObserver = Observer<String> { _ -> }
        val lastReadingStringObserver = Observer<String> { _ -> }
        val sleepStageObserver = Observer<String> { _ -> }

        // Observe the LiveData, passing in this activity as the LifecycleOwner and the observer.
        viewModel.lastTimestamp.observe(this, lastTimestampObserver)
        viewModel.lastReadingString.observe(this, lastReadingStringObserver)
        viewModel.sleepStage.observe(this, sleepStageObserver)

        binding.switchcompat.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                viewModel.setFlowEnabled(true)
                viewModel.getNewReadings()
            } else {
                viewModel.setFlowEnabled(false)
            }
        }

        // Since flow runs asynchronously,
        // start listening on background thread
        lifecycleScope.launch {

            viewModel.documentState.collect {

                // When state to check the
                // state of received data
                when (it.status) {
                    // If its init state then
                    // hide the progress bar
                    Status.INIT -> {
                        binding.progressBar.isVisible = false
                    }

                    // If its loading state then
                    // show the progress bar
                    Status.LOADING -> {
                        binding.progressBar.isVisible = true
                    }
                    // If api call was a success , Update the Ui with
                    // data and make progress bar invisible
                    Status.SUCCESS -> {
                        binding.progressBar.isVisible = false

                        val sleepStage = viewModel.sleepStage.value ?: ""

                        binding.timestampTextview.text = viewModel.lastTimestamp.value
                        binding.readingTextview.text = viewModel.lastReadingString.value
                        binding.sleepStageTexview.text = sleepStage

                        if(sleepStage.contains("AWAKE")) {
                            binding.sleepStageTexview.setTextColor(Color.RED)
                        } else {
                            binding.sleepStageTexview.setTextColor(Color.GREEN)
                        }
                    }
                    // In case of error, show some data to user
                    else -> {
                        Log.e("MainActivity","$it.message")
                        binding.progressBar.isVisible = false
                        Toast.makeText(this@MainActivity, "${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun purgeOldRecords(dateTime : LocalDateTime) {
        // create a scope to access the database from a thread other than the main thread
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            Log.d("MainActivity", "dateTimeOfQuery=$dateTime")

            val dao = ReadingDatabase.getInstance(application).readingDao
            val cnt = dao.deleteOlder(dateTime)
            Log.d("MainActivity", "recordsDeleted=$cnt")
        }
    }
}