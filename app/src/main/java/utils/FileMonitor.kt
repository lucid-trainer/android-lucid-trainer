package utils

import android.os.Environment
import android.util.Log
import java.io.File

object FileMonitor {

    private val ex = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)

    fun getFilePath(fileName : String): String? {

        val fileLocation = fileName.split("/")

        val file = File(
            File(ex.path + "/" + fileLocation[0] + "/" + fileLocation[1] + "/"),
            fileLocation[2])

        var filePath = ""
        return if (file.exists()) {
            filePath = file.path
            Log.d("File Retrieval", "text=$filePath")

            filePath
        } else {
            null
        }
    }

    fun getFilesFromDirectory(dir : String) : List<String> {

        val directory: File = File(ex.path + "/wild/" + dir)
        val files = directory.listFiles()
        val filesList = mutableListOf<String>()

        Log.d("Files", "Size: " + files.size)
        for (i in files.indices) {
            filesList.add(files[i].name)
        }

        return filesList
    }
}