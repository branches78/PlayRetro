package com.swordfish.lemuroid.app.shared.gamemenu

import android.content.Context
import android.graphics.Bitmap
import com.swordfish.lemuroid.common.graphics.GraphicsUtils
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.saves.SaveInfo
import com.swordfish.lemuroid.lib.saves.StatesPreviewManager
import java.text.SimpleDateFormat
import kotlin.math.roundToInt

object GameMenuHelper {
    fun getSaveStateDescription(saveInfo: SaveInfo): String {
        val formatter = SimpleDateFormat.getDateTimeInstance()
        return if (saveInfo.exists) {
            formatter.format(saveInfo.date)
        } else {
            ""
        }
    }

    suspend fun getSaveStateBitmap(
        context: Context,
        statesPreviewManager: StatesPreviewManager,
        saveStateInfo: SaveInfo,
        game: Game,
        coreID: CoreID,
        index: Int,
    ): Bitmap? {
        if (!saveStateInfo.exists) return null
        val imageSize = GraphicsUtils.convertDpToPixel(96f, context).roundToInt()
        return statesPreviewManager.getPreviewForSlot(game, coreID, index, imageSize)
    }
}
