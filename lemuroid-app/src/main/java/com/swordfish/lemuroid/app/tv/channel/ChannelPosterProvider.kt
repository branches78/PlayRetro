package com.swordfish.lemuroid.app.tv.channel

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Serves the 16:9 posters of the channel of the home screen of the TV ([ChannelHandler]) to the launcher, which
 * can't read the files of the app. Only the images of [postersDirectory], read only.
 */
class ChannelPosterProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor {
        val name = uri.lastPathSegment ?: throw FileNotFoundException(uri.toString())
        // Only plain names of the posters folder: nothing else of the app can be read.
        if (mode != "r" || !POSTER_NAME.matches(name)) throw FileNotFoundException(uri.toString())
        val file = File(postersDirectory(context!!), name)
        if (!file.exists()) throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/png"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        private val POSTER_NAME = Regex("[A-Za-z0-9_-]+\\.png")

        fun postersDirectory(context: Context): File = File(context.filesDir, "channel-posters").apply { mkdirs() }

        fun uriFor(
            context: Context,
            name: String,
        ): Uri = Uri.parse("content://${context.packageName}.channelposters/$name")
    }
}
