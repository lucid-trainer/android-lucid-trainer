package viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import database.ReadingDao

class DocumentViewModelFactory(private val dao: ReadingDao)
    : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DocumentViewModel::class.java)) {
            return DocumentViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel")
    }
}