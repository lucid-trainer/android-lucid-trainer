package utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.util.Log
import com.olekdia.androidcommon.extensions.defaultSharedPreferences
import java.io.File

class FileManager(val sharedPreferences : SharedPreferences) {

    private var filesInSession : MutableMap<String, List<String>> = emptyMap<String, List<String>>().toMutableMap()

    private val ex = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)


    companion object {

        private val FILE_PREFIX: String  = "FILE_"

        @Volatile
        private var INSTANCE: FileManager? = null

        fun getInstance(context: Context): FileManager {
            synchronized(this) {
                var instance = INSTANCE
                if (instance == null) {
                    val preferences = context.applicationContext.defaultSharedPreferences
                    instance = FileManager(preferences)
                    INSTANCE = instance
                }
                return instance
            }
        }

        fun getInstance() : FileManager? {
            return INSTANCE
        }

    }

    fun getFilesFromDirectory(dir: String) : List<String>{

        return if(filesInSession.containsKey(dir)) {
            filesInSession[dir]?.toList() ?: emptyList()
        } else {
            val files = getAllFilesFromDirectory(dir)
            filesInSession[dir] = files
            files
        }

    }

    fun clearFilesInSession() {
        filesInSession =  emptyMap<String, List<String>>().toMutableMap()
    }

    fun addFilesUsed(dir: String, files: List<String>) {
        val usedFiles = getUsedFilesFromDirectory(dir).toMutableSet()
        usedFiles.addAll(files)

        with (sharedPreferences.edit()) {
            putStringSet(FILE_PREFIX+dir, usedFiles)
            apply()
        }
    }

    fun addFileUsed(dir: String, file: String) {
        val files = listOf(file)
        addFilesUsed(dir, files)
    }

    fun resetFilesUsed(vararg dirs: String) {
        for (dir in dirs) {
            with(sharedPreferences.edit()) {
                putStringSet(FILE_PREFIX + dir, emptySet())
                apply()
            }
            Log.d("FileManager", "resetting shared pref for $dir")
        }
    }

    fun getUnusedFilesFromDirectory(dir: String, minRequired: Int) : List<String> {
        val allFiles = getFilesFromDirectory(dir)
        val usedFiles = getUsedFilesFromDirectory(dir)
        val unusedFiles = allFiles.minus(usedFiles.toSet())

        return if(unusedFiles.size < minRequired) {
            resetFilesUsed(dir)
            allFiles
        } else {
            unusedFiles
        }
    }

    fun getUsedFilesFromDirectory(dir: String) : List<String> {
        val usedFiles = sharedPreferences.getStringSet(FILE_PREFIX+dir, emptySet())!!.toList()
        Log.d("FileManager", "shared pref used files for $dir: $usedFiles")
        return sharedPreferences.getStringSet(FILE_PREFIX+dir, emptySet())!!.toList()
    }

    private fun getAllFilesFromDirectory(dir: String) : List<String> {

        val filesList = mutableListOf<String>()
        val directory = File(ex.path + "/$dir")
        val files = directory.listFiles()

        if(files != null && files.isNotEmpty()) {
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

        return if (file.exists()) {
            file.path
        } else {
            null
        }
    }

}