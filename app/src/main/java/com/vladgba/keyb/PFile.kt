package com.vladgba.keyb

import android.content.Context
import java.io.*

/**
 * Class extends the built-in File class and provides additional convenience methods for reading and writing
 * text files in the application's private storage area.
 *
 * @param ctx the Context object to use for accessing the application's private storage area
 * @param fileName the name of the file to read from or write to (without the file extension)
 */
class PFile(ctx: Context, fileName: String, ext: String = LAYOUT_EXT) : File(ctx.filesDir, "$fileName.$ext") {

    /**
     * Reads the contents of the file as a single String.
     *
     * @return the contents of the file as a String, or an empty string if an error occurred
     */
    fun read() = try {
        BufferedReader(FileReader(this)).use { it.readText() }
    } catch (e: Exception) {
        e.printStackTrace()
        ""
    }

    /**
     * Writes the specified String to the file.
     *
     * @param s the String to write to the file
     * @return true if the write operation succeeded, or false if an error occurred
     */
    fun write(s: String): Boolean {
        return try {
            BufferedWriter(FileWriter(this)).use { writer -> writer.write(s) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}