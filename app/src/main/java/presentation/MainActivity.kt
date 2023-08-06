package presentation

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
import kotlinx.coroutines.launch
import network.Status
import viewmodel.DocumentViewModel
import viewmodel.DocumentViewModelFactory

class MainActivity : AppCompatActivity() {

    // variable to initialize it later
    private lateinit var viewModel: DocumentViewModel

    // create a view binding variable
    private lateinit var binding: ActivityMainBinding

    var lastTimestamp = ""

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

        // Create the observer which updates the latest reading from the database
        val lastReadingObserver = Observer<Reading> { reading ->
            // Update the UI, in this case, a TextView.
            lastTimestamp = reading?.timestamp ?: ""
        }

        // Observe the LiveData, passing in this activity as the LifecycleOwner and the observer.
        viewModel.lastReading.observe(this, lastReadingObserver)

        // Listen for the button click event to search
        binding.button.setOnClickListener {
            viewModel.getNewReadings(lastTimestamp)
        }

        // Since flow runs asynchronously,
        // start listening on background thread
        lifecycleScope.launch {

            viewModel.documentState.collect {

                // When state to check the
                // state of received data
                when (it.status) {

                    // If its loading state then
                    // show the progress bar
                    Status.LOADING -> {
                        binding.progressBar.isVisible = true
                    }
                    // If api call was a success , Update the Ui with
                    // data and make progress bar invisible
                    Status.SUCCESS -> {
                        binding.progressBar.isVisible = false

                        // Received data can be null, put a check to prevent
                        // null pointer exception
                        it.data?.let { response ->
                            Log.d("MainActivity", "response=$response")
                            binding.timestampTextview.text = response.documents[0].timestamp
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
}