package com.swordfish.lemuroid.app.tv.main

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.settings.SettingsManager
import com.swordfish.lemuroid.app.mobile.feature.shortcuts.ShortcutsGenerator
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.app.shared.GameInteractor
import com.swordfish.lemuroid.app.shared.game.BaseGameActivity
import com.swordfish.lemuroid.app.shared.game.GameLauncher
import com.swordfish.lemuroid.app.shared.main.BusyActivity
import com.swordfish.lemuroid.app.shared.main.GameLaunchTaskHandler
import com.swordfish.lemuroid.app.shared.savesync.SaveSyncBanner
import com.swordfish.lemuroid.app.shared.savesync.SaveSyncChanges
import com.swordfish.lemuroid.app.shared.savesync.SaveSyncWork
import com.swordfish.lemuroid.app.shared.settings.SettingsInteractor
import com.swordfish.lemuroid.app.tv.channel.ChannelUpdateWork
import com.swordfish.lemuroid.app.tv.details.TVGameDetailsFragment
import com.swordfish.lemuroid.app.tv.favorites.TVFavoritesFragment
import com.swordfish.lemuroid.app.tv.games.TVGamesFragment
import com.swordfish.lemuroid.app.tv.home.TVHomeFragment
import com.swordfish.lemuroid.app.tv.search.TVSearchFragment
import com.swordfish.lemuroid.app.tv.settings.TVSettingsTabFragment
import com.swordfish.lemuroid.app.tv.shared.BaseTVActivity
import com.swordfish.lemuroid.app.tv.shared.TVHelper
import com.swordfish.lemuroid.app.tv.systems.TVSystemsFragment
import com.swordfish.lemuroid.common.coroutines.safeLaunch
import com.swordfish.lemuroid.lib.injection.PerActivity
import com.swordfish.lemuroid.lib.injection.PerFragment
import com.swordfish.lemuroid.lib.library.covers.CustomCoverManager
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.savesync.SaveSyncManager
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import javax.inject.Inject
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope

@OptIn(DelicateCoroutinesApi::class)
class MainTVActivity : BaseTVActivity(), BusyActivity {
    @Inject
    lateinit var gameLaunchTaskHandler: GameLaunchTaskHandler

    @Inject
    lateinit var saveSyncManager: SaveSyncManager

    @Inject
    lateinit var settingsManager: SettingsManager

    var mainViewModel: MainTVViewModel? = null

    override fun activity(): Activity = this

    override fun isBusy(): Boolean = mainViewModel?.inProgress?.value ?: false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_main)
        if (savedInstanceState == null) {
            SaveSyncWork.enqueueStartupWork(applicationContext)
            // The channel of the TV home screen with "Continue playing", e.g. after a sync while the app was closed.
            ChannelUpdateWork.enqueue(applicationContext)
        }
        SaveSyncChanges.keepChecking(this, applicationContext, saveSyncManager, settingsManager)
        findViewById<ComposeView>(R.id.tv_save_sync_banner).setContent { AppTheme { SaveSyncBanner() } }

        val factory = MainTVViewModel.Factory(applicationContext)
        mainViewModel = ViewModelProvider(this, factory).get(MainTVViewModel::class.java)

        ensureLegacyStoragePermissionsIfNeeded()
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            BaseGameActivity.REQUEST_PLAY_GAME -> {
                GlobalScope.safeLaunch {
                    gameLaunchTaskHandler.handleGameFinish(false, this@MainTVActivity, resultCode, data)
                    ChannelUpdateWork.enqueue(applicationContext)
                }
            }
        }
    }

    private fun ensureLegacyStoragePermissionsIfNeeded() {
        if (TVHelper.isSAFSupported(this) || hasLegacyPermissions()) {
            return
        }

        val requestPermission = ActivityResultContracts.RequestPermission()
        val requestPermissionLauncher =
            registerForActivityResult(requestPermission) { isGranted ->
                if (!isGranted) {
                    finish()
                }
            }
        requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun hasLegacyPermissions(): Boolean {
        val result = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        return result == PackageManager.PERMISSION_GRANTED
    }

    @dagger.Module
    abstract class Module {
        @PerFragment
        @ContributesAndroidInjector(modules = [TVHomeFragment.Module::class])
        abstract fun tvHomeFragment(): TVHomeFragment

        @PerFragment
        @ContributesAndroidInjector(modules = [TVGamesFragment.Module::class])
        abstract fun tvGamesFragment(): TVGamesFragment

        @PerFragment
        @ContributesAndroidInjector(modules = [TVSearchFragment.Module::class])
        abstract fun tvSearchFragment(): TVSearchFragment

        @PerFragment
        @ContributesAndroidInjector(modules = [TVGameDetailsFragment.Module::class])
        abstract fun tvGameDetailsFragment(): TVGameDetailsFragment

        @PerFragment
        @ContributesAndroidInjector(modules = [TVFavoritesFragment.Module::class])
        abstract fun tvFavoritesFragment(): TVFavoritesFragment

        @PerFragment
        @ContributesAndroidInjector(modules = [TVSystemsFragment.Module::class])
        abstract fun tvSystemsFragment(): TVSystemsFragment

        @PerFragment
        @ContributesAndroidInjector(modules = [TVSettingsTabFragment.Module::class])
        abstract fun tvSettingsTabFragment(): TVSettingsTabFragment

        @dagger.Module
        companion object {
            @Provides
            @PerActivity
            @JvmStatic
            fun settingsInteractor(
                activity: MainTVActivity,
                directoriesManager: DirectoriesManager,
            ) = SettingsInteractor(activity, directoriesManager)

            @Provides
            @PerActivity
            @JvmStatic
            fun gameInteractor(
                activity: MainTVActivity,
                retrogradeDb: RetrogradeDatabase,
                shortcutsGenerator: ShortcutsGenerator,
                gameLauncher: GameLauncher,
                customCoverManager: CustomCoverManager,
            ) = GameInteractor(activity, retrogradeDb, true, shortcutsGenerator, gameLauncher, customCoverManager)
        }
    }
}
