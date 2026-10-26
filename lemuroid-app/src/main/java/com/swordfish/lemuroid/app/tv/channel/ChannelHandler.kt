package com.swordfish.lemuroid.app.tv.channel

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.VectorDrawable
import android.media.tv.TvContract
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.ChannelLogoUtils
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.covers.CoverUtils
import com.swordfish.lemuroid.app.shared.deeplink.DeepLink
import com.swordfish.lemuroid.app.tv.home.TVHomeViewModel
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

/**
 * Channel of the app on the home screen of the TV: the games of "Continue playing", in the same order, which open
 * right away. Each one has a 16:9 poster made by the app with the whole cover centered on it, since the launcher
 * crops the images to the shape of its cards.
 */
class ChannelHandler(
    private val appContext: Context,
    private val retrogradeDatabase: RetrogradeDatabase,
) {
    private val appName = appContext.getString(R.string.lemuroid_name)

    private val channelsProjection =
        arrayOf(
            TvContractCompat.Channels._ID,
            TvContract.Channels.COLUMN_DISPLAY_NAME,
            TvContractCompat.Channels.COLUMN_BROWSABLE,
        )

    private fun getOrCreateChannelId(): Long? {
        var channelId = findChannel()

        if (channelId != null) {
            return channelId
        }

        val builder =
            Channel.Builder()
                .setType(TvContractCompat.Channels.TYPE_PREVIEW)
                .setDisplayName(appName)
                .setAppLinkIntentUri(DeepLink.openLeanbackUri(appContext))

        val channelUri =
            appContext.contentResolver.insert(
                TvContractCompat.Channels.CONTENT_URI,
                builder.build().toContentValues(),
            ) ?: return null

        channelId = ContentUris.parseId(channelUri)

        ChannelLogoUtils.storeChannelLogo(
            appContext,
            channelId,
            convertToBitmap(appContext, R.mipmap.lemuroid_tv_channel)!!,
        )

        TvContractCompat.requestChannelBrowsable(appContext, channelId)
        return channelId
    }

    private fun convertToBitmap(
        context: Context,
        resourceId: Int,
    ): Bitmap? {
        val drawable = context.getDrawable(resourceId)
        if (drawable is VectorDrawable) {
            val bitmap: Bitmap =
                Bitmap.createBitmap(
                    drawable.getIntrinsicWidth(),
                    drawable.getIntrinsicHeight(),
                    Bitmap.Config.ARGB_8888,
                )
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight())
            drawable.draw(canvas)
            return bitmap
        }
        return BitmapFactory.decodeResource(context.resources, resourceId)
    }

    suspend fun update() {
        val recentGames =
            retrogradeDatabase.gameDao().selectFirstUnfavoriteRecents(TVHomeViewModel.CAROUSEL_MAX_ITEMS).first()

        val posters = recentGames.map { it to createPoster(it) }
        deleteOldPosters(posters.map { it.second })

        val channelId = getOrCreateChannelId() ?: return

        val channel = Channel.Builder()
        channel.setDisplayName(appName)
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setAppLinkIntentUri(DeepLink.openLeanbackUri(appContext))
            .build()

        deletePrograms()
        // The channel kept from older versions may have lost its logo.
        convertToBitmap(appContext, R.mipmap.lemuroid_tv_channel)?.let {
            ChannelLogoUtils.storeChannelLogo(appContext, channelId, it)
        }

        appContext.contentResolver.update(
            TvContractCompat.buildChannelUri(channelId),
            channel.build().toContentValues(),
            null,
            null,
        )

        val contentValues =
            posters.map { (game, poster) ->
                getGameProgram(channelId, game, poster).toContentValues()
            }

        if (contentValues.isNotEmpty()) {
            appContext.contentResolver.bulkInsert(
                Uri.parse("content://android.media.tv/preview_program"),
                contentValues.toTypedArray(),
            )
        }
    }

    /** 16:9 image with the whole cover centered on the dark background of the app; its file name. */
    private suspend fun createPoster(game: Game): String {
        val coverUrl = game.displayCoverUrl() ?: CoverUtils.getFallbackRemoteUrl(game)
        // A new name when the cover changes, so the launcher doesn't keep showing the old one.
        val name = "game_${game.id}_${Integer.toHexString(coverUrl.hashCode())}.png"
        val file = File(ChannelPosterProvider.postersDirectory(appContext), name)
        if (file.exists()) return name

        val request =
            ImageRequest.Builder(appContext)
                .data(coverUrl)
                .allowHardware(false)
                .build()
        val cover = (appContext.imageLoader.execute(request) as? SuccessResult)?.drawable
            ?: CoverUtils.getFallbackDrawable(game)

        val poster = Bitmap.createBitmap(POSTER_WIDTH, POSTER_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(poster)
        canvas.drawColor(ContextCompat.getColor(appContext, R.color.md_theme_dark_surfaceContainerLow))

        val coverWidth = cover.intrinsicWidth.takeIf { it > 0 } ?: POSTER_HEIGHT
        val coverHeight = cover.intrinsicHeight.takeIf { it > 0 } ?: POSTER_HEIGHT
        val scale = minOf(POSTER_WIDTH.toFloat() / coverWidth, POSTER_HEIGHT.toFloat() / coverHeight)
        val width = (coverWidth * scale).roundToInt()
        val height = (coverHeight * scale).roundToInt()
        val left = (POSTER_WIDTH - width) / 2
        val top = (POSTER_HEIGHT - height) / 2
        cover.setBounds(left, top, left + width, top + height)
        cover.draw(canvas)

        val partFile = File(file.parentFile, "$name.part")
        partFile.outputStream().use { poster.compress(Bitmap.CompressFormat.PNG, 100, it) }
        partFile.renameTo(file)
        poster.recycle()
        return name
    }

    private fun deleteOldPosters(names: List<String>) {
        ChannelPosterProvider.postersDirectory(appContext)
            .listFiles()
            ?.filter { it.name !in names }
            ?.forEach { it.delete() }
    }

    @SuppressLint("RestrictedApi")
    private fun getGameProgram(
        channelId: Long,
        game: Game,
        posterName: String,
    ): PreviewProgram {
        val intent = DeepLink.launchIntentForGame(appContext, game)

        return PreviewProgram.Builder()
            .setChannelId(channelId)
            .setTitle(game.displayName())
            .setDescription(game.developer)
            .setIntent(intent)
            .setStartTimeUtcMillis(game.lastPlayedAt ?: 0)
            .setType(TvContractCompat.PreviewPrograms.TYPE_GAME)
            .setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9)
            .setPosterArtUri(ChannelPosterProvider.uriFor(appContext, posterName))
            .build()
    }

    /**
     * Every game of the channels of the app, also the ones of older versions. One by one: the TV provider doesn't
     * accept a selection to delete them.
     */
    private fun deletePrograms() {
        val ids =
            appContext.contentResolver.query(
                TvContractCompat.PreviewPrograms.CONTENT_URI,
                arrayOf(TvContractCompat.PreviewPrograms._ID),
                null,
                null,
                null,
            )?.use { cursor ->
                generateSequence { if (cursor.moveToNext()) cursor.getLong(0) else null }.toList()
            }.orEmpty()
        ids.forEach { appContext.contentResolver.delete(TvContractCompat.buildPreviewProgramUri(it), null, null) }
    }

    /**
     * The channel of the app, whatever its name: the app was called GameStation before, and the launcher keeps showing
     * the channel created then (a new one has to be accepted by the user). The one the launcher shows is kept (it is
     * renamed in [update]); others left by older versions are deleted.
     */
    private fun findChannel(): Long? {
        // Only the channels of this app are returned.
        val channels =
            appContext.contentResolver.query(
                TvContractCompat.Channels.CONTENT_URI,
                channelsProjection,
                null,
                null,
                null,
            )?.use { cursor ->
                generateSequence { if (cursor.moveToNext()) Channel.fromCursor(cursor) else null }.toList()
            } ?: return null

        val kept = channels.sortedWith(compareBy({ !it.isBrowsable }, { it.id })).firstOrNull() ?: return null
        channels.filter { it.id != kept.id }.forEach {
            appContext.contentResolver.delete(TvContractCompat.buildChannelUri(it.id), null, null)
        }
        return kept.id
    }

    companion object {
        private const val POSTER_WIDTH = 640
        private const val POSTER_HEIGHT = 360
    }
}
