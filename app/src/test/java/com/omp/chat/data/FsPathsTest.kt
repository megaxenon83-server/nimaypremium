package com.omp.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FsPathsTest {

    @Test
    fun sepDetectsWindowsAndUnix() {
        assertEquals("\\", FsPaths.sep("C:\\Users\\meg"))
        assertEquals("\\", FsPaths.sep("C:"))
        assertEquals("/", FsPaths.sep("/home/meg"))
    }

    @Test
    fun joinHandlesTrailingSeparators() {
        assertEquals("C:\\Users\\meg\\Documents", FsPaths.join("C:\\Users\\meg", "Documents"))
        assertEquals("C:\\Users\\meg\\Documents", FsPaths.join("C:\\Users\\meg\\", "Documents"))
        assertEquals("/home/meg/docs", FsPaths.join("/home/meg", "docs"))
        assertEquals("/home/meg/docs/a.txt", FsPaths.join("/home/meg", "docs", "a.txt"))
        assertEquals("C:\\Users", FsPaths.join("C:\\", "Users"))
        assertEquals("C:\\Users", FsPaths.join("C:", "Users"))
        assertEquals("a", FsPaths.join("", "a"))
        assertEquals("a", FsPaths.join("a", ""))
    }

    @Test
    fun fileNameReturnsLastSegment() {
        assertEquals("a.txt", FsPaths.fileName("C:\\Users\\meg\\a.txt"))
        assertEquals("a.txt", FsPaths.fileName("C:\\Users\\meg\\a.txt\\"))
        assertEquals("meg", FsPaths.fileName("/home/meg"))
        assertEquals("C:", FsPaths.fileName("C:"))
    }

    @Test
    fun parentWalksUpAndStopsAtRoot() {
        assertEquals("C:\\Users", FsPaths.parent("C:\\Users\\meg"))
        assertEquals("C:\\", FsPaths.parent("C:\\Users"))
        assertEquals("", FsPaths.parent("C:\\"))
        assertEquals("", FsPaths.parent("C:"))
        assertEquals("/home", FsPaths.parent("/home/meg"))
        assertEquals("/", FsPaths.parent("/home"))
        assertEquals("", FsPaths.parent("/"))
        assertEquals("", FsPaths.parent(""))
        assertEquals("C:\\Users", FsPaths.parent("C:\\Users\\meg\\"))
    }

    @Test
    fun isInsideComparesCaseInsensitivelyAndByBoundary() {
        assertTrue(FsPaths.isInside("C:\\Users\\meg\\a", "C:\\Users\\meg"))
        assertTrue(FsPaths.isInside("/home/meg/a", "/home/meg"))
        assertFalse(FsPaths.isInside("C:\\Users\\meg", "C:\\Users\\meg"))
        // "meg2" skal ikke regnes som inni "meg"
        assertFalse(FsPaths.isInside("C:\\Users\\meg2", "C:\\Users\\meg"))
        assertFalse(FsPaths.isInside("C:\\Users\\meg", ""))
    }

    @Test
    fun uniqueNameAddsCounterBeforeExtension() {
        assertEquals("a.txt", FsPaths.uniqueName("a.txt", setOf("b.txt")))
        assertEquals("a (1).txt", FsPaths.uniqueName("a.txt", setOf("a.txt")))
        assertEquals("a (2).txt", FsPaths.uniqueName("a.txt", setOf("a.txt", "a (1).txt")))
        assertEquals("A (1).TXT", FsPaths.uniqueName("A.TXT", setOf("a.txt")))
        assertEquals("mappe (1)", FsPaths.uniqueName("mappe", setOf("mappe")))
        assertEquals(".gitignore (1)", ".gitignore (1)".let { FsPaths.uniqueName(".gitignore", setOf(".gitignore")) })
    }

    @Test
    fun tempNameKeepsExtensionAfterMarker() {
        assertEquals("bilde.ompdownload.png", FsSaf.tempName("bilde.png"))
        assertEquals("notat.ompdownload.txt", FsSaf.tempName("notat.txt"))
        assertEquals("uten-endelse.ompdownload", FsSaf.tempName("uten-endelse"))
        assertEquals(".skjult.ompdownload", FsSaf.tempName(".skjult"))
    }

    @Test
    fun breadcrumbsBuildGrowingPaths() {
        assertEquals(
            listOf("C:\\Users", "C:\\Users\\meg", "C:\\Users\\meg\\Documents"),
            FsPaths.breadcrumbs("C:\\Users\\meg\\Documents")
        )
        assertEquals(
            listOf("/home", "/home/meg", "/home/meg/docs"),
            FsPaths.breadcrumbs("/home/meg/docs")
        )
        assertEquals(emptyList<String>(), FsPaths.breadcrumbs(""))
    }
}

class FsFormatTest {

    @Test
    fun sizeUsesDecimalUnits() {
        assertTrue(FsFormat.size(0) == "0 B")
        assertTrue(FsFormat.size(999) == "999 B")
        assertTrue(FsFormat.size(1_000).startsWith("1") && FsFormat.size(1_000).endsWith(" kB"))
        assertTrue(FsFormat.size(1_500_000).endsWith(" MB"))
        assertTrue(FsFormat.size(2_000_000_000).endsWith(" GB"))
        assertTrue(FsFormat.size(3_000_000_000_000).endsWith(" TB"))
    }

    @Test
    fun speedEmptyWhenIdle() {
        assertEquals("", FsFormat.speed(0))
        assertEquals("", FsFormat.speed(-5))
        assertTrue(FsFormat.speed(1_500_000).endsWith("/s"))
    }

    @Test
    fun percentClampsAndHandlesZeroTotal() {
        assertEquals(0, FsFormat.percent(0, 0))
        assertEquals(0, FsFormat.percent(0, 100))
        assertEquals(50, FsFormat.percent(50, 100))
        assertEquals(100, FsFormat.percent(120, 100))
        assertEquals(0, FsFormat.percent(-10, 100))
    }

    @Test
    fun dateEmptyForUnknownTimestamp() {
        assertEquals("", FsFormat.date(0))
        assertTrue(FsFormat.date(1_700_000_000_000L).contains("."))
    }

    @Test
    fun mimeCoversCommonTypes() {
        assertEquals("image/jpeg", FsMime.of("a.JPG"))
        assertEquals("video/mp4", FsMime.of("a.mp4"))
        assertEquals("application/pdf", FsMime.of("a.pdf"))
        assertEquals("application/octet-stream", FsMime.of("a.unknown"))
        assertTrue(FsMime.isImage("x.png"))
        assertFalse(FsMime.isImage("x.txt"))
        assertTrue(FsMime.isVideo("x.mkv"))
    }
}
