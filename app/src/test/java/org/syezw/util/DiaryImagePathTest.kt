package org.syezw.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class DiaryImagePathTest {

    @Test
    fun normalizeDiaryImageName_returnsFileNameForAbsolutePath() {
        val name = normalizeDiaryImageName("/storage/emulated/0/Download/syezw_diary_images/a.jpg")
        assertEquals("a.jpg", name)
    }

    @Test
    fun normalizeDiaryImageName_keepsFileNameInput() {
        val name = normalizeDiaryImageName("b.png")
        assertEquals("b.png", name)
    }

    @Test
    fun diaryImagesRelativePath_buildsExpectedValue() {
        assertEquals("Download/syezw_diary_images/", diaryImagesRelativePath())
    }

    @Test
    fun resolvePathFromDownloadsRelativePath_removesDownloadPrefix() {
        val downloadsDir = File("/tmp/downloads")
        val file = resolvePathFromDownloadsRelativePath(
            downloadsDir = downloadsDir,
            relativePath = "Download/syezw_diary_images/",
            fileName = "c.webp"
        )
        assertEquals(File(downloadsDir, "syezw_diary_images/c.webp").path, file.path)
    }

    @Test
    fun resolveDiaryImagePath_withRelativeName_buildsPathUnderDiaryFolder() {
        val downloadsDir = File("/tmp/downloads")
        val path = resolveDiaryImagePath("d.jpeg", downloadsDir)
        assertEquals(File(downloadsDir, "syezw_diary_images/d.jpeg").path, path)
    }

    @Test
    fun resolveDiaryImagePath_withAbsolutePath_keepsOriginalPath() {
        val downloadsDir = File("/tmp/downloads")
        val absolute = File(File(downloadsDir, "external"), "e.png").absolutePath
        val path = resolveDiaryImagePath(absolute, downloadsDir)
        assertEquals(absolute, path)
    }
}
