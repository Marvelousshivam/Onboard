package com.boardsprep.onboard.core.pdf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.regex.Pattern

/**
 * Identity & cache-file tests for [PdfDocumentRepository].
 *
 * These exercise the *real* repository (no mocks) against a Robolectric
 * [Context] so that [Context.filesDir] returns a writable temp dir. We focus
 * on the deterministic identity & file-naming contract: the rest of the
 * repository (download / validation) needs network or real PDFs and is
 * intentionally out of scope here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PdfDocumentRepositoryIdentityTest {

    private lateinit var repo: PdfDocumentRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repo = PdfDocumentRepository(context)
    }

    // -----------------------------------------------------------------------
    // documentIdFor
    // -----------------------------------------------------------------------

    @Test
    fun documentIdFor_isDeterministic() {
        val source = "https://boardsprep.com/ncert/class12/physics/ch01.pdf"
        val a = repo.documentIdFor(source)
        val b = repo.documentIdFor(source)
        assertEquals("same source must always yield the same id", a, b)
    }

    @Test
    fun documentIdFor_is64HexChars() {
        val id = repo.documentIdFor("https://example.com/x.pdf")
        assertEquals("SHA-256 hex digest must be 64 chars", 64, id.length)
        assertTrue(
            "id must be lowercase hex",
            Pattern.matches("^[0-9a-f]{64}$", id)
        )
    }

    @Test
    fun documentIdFor_differentSourcesProduceDifferentIds() {
        val idA = repo.documentIdFor("https://a.com/x.pdf")
        val idB = repo.documentIdFor("https://b.com/x.pdf")
        assertNotEquals("distinct sources must yield distinct ids", idA, idB)
    }

    @Test
    fun documentIdFor_urlsDifferingOnlyInFragmentProduceDifferentIds() {
        // This is the regression that would happen if the old
        // Math.abs(url.hashCode()) scheme were still in use: hashCode collisions
        // across URLs that share a long suffix were catastrophic. SHA-256 must
        // not collide on these.
        val idA = repo.documentIdFor("https://boardsprep.com/sample-paper.pdf#page=1")
        val idB = repo.documentIdFor("https://boardsprep.com/sample-paper.pdf#page=2")
        assertNotEquals(
            "URLs differing only in fragment must yield distinct SHA-256 ids",
            idA,
            idB
        )
    }

    @Test
    fun documentIdFor_localFilePathsAreStableIds() {
        val idA = repo.documentIdFor("/storage/emulated/0/Documents/foo.pdf")
        val idB = repo.documentIdFor("/storage/emulated/0/Documents/foo.pdf")
        assertEquals(idA, idB)
    }

    // -----------------------------------------------------------------------
    // cacheFileFor
    // -----------------------------------------------------------------------

    @Test
    fun cacheFileFor_pdfSourceEndsWithPdfExtension() {
        val source = "https://boardsprep.com/ncert/physics.pdf"
        val id = repo.documentIdFor(source)
        val file: File = repo.cacheFileFor(source)
        assertEquals("$id.pdf", file.name)
        assertTrue(file.name.endsWith(".pdf"))
        assertTrue(file.name.startsWith(id))
    }

    @Test
    fun cacheFileFor_nonPdfSourceGetsBinExtension() {
        val source = "https://boardsprep.com/ncert/physics"
        val id = repo.documentIdFor(source)
        val file = repo.cacheFileFor(source)
        assertEquals("$id.bin", file.name)
        assertTrue(file.name.endsWith(".bin"))
        assertTrue(file.name.startsWith(id))
    }

    @Test
    fun cacheFileFor_parentDirIsCachedPdfsUnderFilesDir() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expectedParent = File(context.filesDir, "cached_pdfs")

        val pdf = repo.cacheFileFor("https://boardsprep.com/a.pdf")
        val bin = repo.cacheFileFor("https://boardsprep.com/b")

        assertEquals(expectedParent.absolutePath, pdf.parentFile?.absolutePath)
        assertEquals(expectedParent.absolutePath, bin.parentFile?.absolutePath)
    }

    @Test
    fun cacheFileFor_caseInsensitivePdfExtension() {
        // The implementation lowercases the extension before comparing to "pdf".
        val source = "https://boardsprep.com/Notes.PDF"
        val id = repo.documentIdFor(source)
        val file = repo.cacheFileFor(source)
        assertEquals("$id.pdf", file.name)
    }

    @Test
    fun cacheFileFor_pdfExtensionWithTrailingQueryResolvesToBin() {
        // .pdf?page=1 — substringAfterLast('.') = "pdf?page=1", which lowercased
        // is NOT "pdf" — so this falls into the .bin branch. This test pins the
        // documented behaviour so future refactors don't silently change it.
        val source = "https://boardsprep.com/a.pdf?page=1"
        val id = repo.documentIdFor(source)
        val file = repo.cacheFileFor(source)
        assertEquals("$id.bin", file.name)
    }
}
