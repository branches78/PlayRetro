package com.swordfish.lemuroid.app.tv

import com.swordfish.lemuroid.app.tv.folderpicker.TVFolderPickerActivity
import com.swordfish.lemuroid.app.tv.folderpicker.TVFolderPickerLauncher
import com.swordfish.lemuroid.app.tv.game.TVGameActivity
import com.swordfish.lemuroid.app.tv.main.MainTVActivity
import com.swordfish.lemuroid.lib.injection.PerActivity
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class LemuroidTVApplicationModule {
    @PerActivity
    @ContributesAndroidInjector(modules = [MainTVActivity.Module::class])
    abstract fun tvMainActivity(): MainTVActivity

    @PerActivity
    @ContributesAndroidInjector
    abstract fun tvGameActivity(): TVGameActivity

    @PerActivity
    @ContributesAndroidInjector
    abstract fun tvFolderPickerLauncher(): TVFolderPickerLauncher

    @PerActivity
    @ContributesAndroidInjector
    abstract fun tvFolderPickerActivity(): TVFolderPickerActivity
}
