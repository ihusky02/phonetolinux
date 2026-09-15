package pl.stanislawtlolka.phonetolinux.utils

import android.os.Environment
import java.io.File

/**
 * Utility helper class responsible for inspecting internal storage directories,
 * filtering sensitive system folders, and mapping safe Android directories for Linux file integration.
 */
object StorageManagerUtils {

    // Whitelist of allowed root directories visible on the Linux desktop
    private val ALLOWED_ROOT_DIRECTORIES = setOf(
        "DCIM",
        "Pictures",
        "Download",
        "Documents",
        "Music",
        "Movies",
        "Video",
        "Podcasts",
        "Audiobooks"
    )

    /**
     * Returns the primary external storage directory (/sdcard or /storage/emulated/0).
     */
    fun getRootStorageDir(): File {
        return Environment.getExternalStorageDirectory()
    }

    /**
     * Returns a list of safe files and directories within the specified relative path.
     * Automatically hides hidden dotfiles, system folders (e.g. Android/data), and unapproved root paths.
     */
    fun listDirectory(relativePath: String = ""): List<FileModel> {
        val root = getRootStorageDir()
        val cleanPath = relativePath.trimStart('/')
        val target = if (cleanPath.isBlank()) root else File(root, cleanPath)

        if (!target.exists() || !target.isDirectory) return emptyList()

        val files = target.listFiles() ?: return emptyList()

        return files
            .filter { file -> isSafeFileOrDirectory(file, isRootLevel = cleanPath.isBlank()) }
            .map { file ->
                FileModel(
                    name = file.name,
                    relativePath = file.absolutePath.removePrefix(root.absolutePath),
                    isDirectory = file.isDirectory,
                    sizeBytes = if (file.isDirectory) 0L else file.length(),
                    lastModified = file.lastModified()
                )
            }
    }

    /**
     * Evaluates whether a given file or directory is safe to expose via desktop connection.
     */
    private fun isSafeFileOrDirectory(file: File, isRootLevel: Boolean): Boolean {
        // 1. Hide all dotfiles/folders (e.g., .Trash, .thumbnails)
        if (file.name.startsWith(".")) return false

        // 2. If inspecting the storage root, limit visibility exclusively to whitelisted directories
        if (isRootLevel) {
            return ALLOWED_ROOT_DIRECTORIES.contains(file.name)
        }

        // 3. Prevent access to the system Android/ directory (containing app data/obb files)
        if (file.name.equals("Android", ignoreCase = true) && file.parentFile?.equals(getRootStorageDir()) == true) {
            return false
        }

        return true
    }
}

/**
 * Data model representing a file system entity.
 */
data class FileModel(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long
)