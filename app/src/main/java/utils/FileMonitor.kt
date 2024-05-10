package utils

import android.os.Environment
import java.io.File

object FileMonitor {

    private var filesInSession : MutableMap<String, List<String>> = emptyMap<String, List<String>>().toMutableMap()
    private var filesUsedInSession : MutableMap<String, MutableList<String>> = emptyMap<String, MutableList<String>>().toMutableMap()

    private val ex = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)

    fun getFilesFromDirectory(dir: String) : List<String>{
        return if(filesInSession.containsKey(dir)) {
            filesInSession[dir]?.toList() ?: emptyList()
        } else {
            val files = getAllFilesFromDirectory(dir)
            filesInSession[dir] = files
            files
        }
    }

    fun clearFilesUsedInSession() {
        filesInSession =  emptyMap<String, List<String>>().toMutableMap()
        filesUsedInSession = emptyMap<String, MutableList<String>>().toMutableMap()
    }

    fun addFilesUsedInSession(dir: String, files: List<String>) {
        if(filesUsedInSession.containsKey(dir)) {
            val fileList = filesUsedInSession[dir]
            fileList?.addAll(files)
        } else {
            val fileList = mutableListOf<String>()
            fileList.addAll(files)
            filesUsedInSession[dir] = fileList
        }
    }

    fun addFileUsedInSession(dir: String, file: String) {
        val files = listOf(file)
        addFilesUsedInSession(dir, files)
    }

    fun resetFilesInSession(dir: String) {
        filesUsedInSession[dir] = mutableListOf()
    }

    fun getUnusedFilesFromDirectory(dir: String, minRequired: Int) : List<String> {
        val allFiles = getFilesFromDirectory(dir)
        val usedFiles = getUsedFilesFromDirectory(dir)
        val unusedFiles = allFiles.minus(usedFiles.toSet())

        return if(unusedFiles.size < minRequired) {
            resetFilesInSession(dir)
            allFiles
        } else {
            unusedFiles
        }

    }

    private fun getUsedFilesFromDirectory(dir: String) : List<String> {
        return filesUsedInSession[dir] ?: emptyList()
    }

    private fun getAllFilesFromDirectory(dir: String) : List<String> {

        val filesList = mutableListOf<String>()
        val directory = File(ex.path + "/$dir")
        val files = directory.listFiles()

        if(files != null && files.isNotEmpty()) {
            //Log.d("Files", "Size: " + files.size)
            for (i in files.indices) {
                filesList.add(files[i].name)
            }
        }

        return filesList
    }

    fun getFilePath(fileName : String): String? {

        val fileLocation = fileName.split("/")

        val file = File(
            File(ex.path + "/" + fileLocation[0] + "/" + fileLocation[1] + "/"),
            fileLocation[2])

        var filePath = ""
        return if (file.exists()) {
            filePath = file.path
            //Log.d("File Retrieval", "text=$filePath")

            filePath
        } else {
            null
        }
    }

    //    fun isFileUsedInSession(dir: String, file: String) : Boolean {
//        var isUsed = false
//
//        if(filesUsedInSession.containsKey(dir)) {
//            val fileList = filesUsedInSession[dir]
//            if (fileList != null && fileList.contains(file)) {
//                isUsed = true
//            }
//        }
//        return isUsed
//    }
}