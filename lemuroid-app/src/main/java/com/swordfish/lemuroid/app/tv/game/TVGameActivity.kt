package com.swordfish.lemuroid.app.tv.game

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.GameMenuActivity
import com.swordfish.lemuroid.app.shared.game.BaseGameActivity
import com.swordfish.lemuroid.app.shared.game.BaseGameScreenViewModel
import com.swordfish.lemuroid.app.tv.shared.TVHelper
import com.swordfish.lemuroid.common.coroutines.launchOnState
import com.swordfish.lemuroid.common.coroutines.safeCollect
import com.swordfish.lemuroid.common.displayToast
import kotlinx.coroutines.flow.filter

class TVGameActivity : BaseGameActivity() {
    // The same in-game menu as on mobile, a modal window centered over the game.
    override fun getDialogClass() = GameMenuActivity::class.java

    override fun onCreate(savedInstanceState: Bundle?) {
        TVHelper.requestLowLatencyMode(this)
        super.onCreate(savedInstanceState)
        initializeFlows()
    }

    @Composable
    override fun GameScreen(viewModel: BaseGameScreenViewModel) {
        TVGameScreen(viewModel)
    }

    private fun initializeFlows() {
        launchOnState(Lifecycle.State.CREATED) {
            initializeShortcutToastFlow()
        }
    }

    private suspend fun initializeShortcutToastFlow() {
        inputDeviceManager
            .getEnabledInputsObservable()
            .filter { it.isEmpty() }
            .safeCollect {
                displayToast(R.string.tv_game_message_missing_gamepad)
            }
    }
}
