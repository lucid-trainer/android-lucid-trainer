package presentation

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.lucidtrainer.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import network.Status
import viewmodel.DocumentsViewModel

class MainActivity : AppCompatActivity() {

    // create a CommentsViewModel
    // variable to initialize it later
    private lateinit var viewModel: DocumentsViewModel

    // create a view binding variable
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // instantiate view binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // initialize viewModel
        viewModel = ViewModelProvider(this)[DocumentsViewModel::class.java]


        // Listen for the button click event to search
        binding.button.setOnClickListener {

            // check to prevent api call with no parameters
            //TODO: use this text box to query based on session date
//            if (binding.searchEditText.text.isNullOrEmpty()) {
//                Toast.makeText(this, "Query Can't be empty", Toast.LENGTH_SHORT).show()
//            } else {
                // if Query isn't empty, make the api call
                viewModel.getNewDocuments()
//            }
        }
        // Since flow run asynchronously,
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
                            Log.d("MainActivity", "commentStr=$response")
                            //val gson = Gson()
                            //val comment: CommentModel = gson.fromJson(commentStr, CommentModel::class.java)

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