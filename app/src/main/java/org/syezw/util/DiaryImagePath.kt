package org.syezw.util

import android.os.Environment
import java.io.File

const val DIARY_IMAGES_FOLDER = "syezw_diary_images"
const val DOWNLOAD_RELATIVE_PREFIX = "Download/"

fun diaryImagesRelativePath(): String = "$DOWNLOAD_RELATIVE_PREFIX$DIARY_IMAGES_FOLDER/"

fun resolvePathFromDownloadsRelativePath(downloadsDir: File, relativePath: String, fileName: String): File {
    return File(downloadsDir, relativePath.removePrefix(DOWNLOAD_RELATIVE_PREFIX) + fileName)
}

fun resolveDiaryImagePath(nameOrPath: String): String {
    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    return resolveDiaryImagePath(nameOrPath, downloadsDir)
}

fun resolveDiaryImagePath(nameOrPath: String, downloadsDir: File): String {
    val file = File(nameOrPath)
    if (file.isAbsolute) {
        return file.path
    }
    return File(downloadsDir, "$DIARY_IMAGES_FOLDER/$nameOrPath").path
}

fun resolveDiaryImageFile(nameOrPath: String): File = File(resolveDiaryImagePath(nameOrPath))

fun normalizeDiaryImageName(nameOrPath: String): String = File(nameOrPath).name
