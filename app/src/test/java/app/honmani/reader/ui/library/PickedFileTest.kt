package app.honmani.reader.ui.library

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PickedFileTest {
    private val resolver = ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver

    @Test
    fun aFileIsOpenedWithItsDisplayName() {
        Robolectric.buildContentProvider(BooksProvider::class.java).create("books")

        val picked = LibraryViewModel.openPickedFile(resolver, Uri.parse("content://books/42"))!!

        assertEquals("猫.epub", picked.name)
        assertEquals("book", picked.input.use { String(it.readBytes()) })
    }

    @Test
    fun anotherAppsErrorsMeanTheFileCannotBeRead() {
        // Providers throw what they like; this closed the app.
        Robolectric.buildContentProvider(BrokenProvider::class.java).create("broken")

        assertNull(LibraryViewModel.openPickedFile(resolver, Uri.parse("content://broken/42")))
    }

    class BooksProvider : ContentProvider() {
        override fun onCreate() = true

        override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, sort: String?): Cursor =
            MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf("猫.epub")) }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val file = File.createTempFile("picked", ".epub").apply {
                writeText("book")
                deleteOnExit()
            }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, args: Array<String>?) = 0

        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?) = 0
    }

    class BrokenProvider : ContentProvider() {
        override fun onCreate() = true

        override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, sort: String?): Cursor =
            throw IllegalArgumentException("Unknown URI $uri")

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, args: Array<String>?) = 0

        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?) = 0
    }
}
