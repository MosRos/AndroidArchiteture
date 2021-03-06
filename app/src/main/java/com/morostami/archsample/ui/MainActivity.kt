/*
 * *
 *  * Created by Moslem Rostami on 3/23/20 10:50 PM
 *  * Copyright (c) 2020 . All rights reserved.
 *  * Last modified 3/7/20 1:31 AM
 *
 */

package com.morostami.archsample.ui

import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.ui.NavigationUI
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.morostami.archsample.MainApp
import com.morostami.archsample.R
import com.morostami.archsample.data.prefs.PreferencesHelper
import com.morostami.archsample.databinding.ActivityMainBinding
import com.morostami.archsample.di.CoinsComponent
import com.morostami.archsample.ui.workers.SyncCoinsWorker
import com.morostami.archsample.utils.AppBarUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject


class MainActivity : AppCompatActivity() {

    @Inject lateinit var mainViewModel: MainViewModel
    @Inject lateinit var sPreferences: SharedPreferences
    @Inject lateinit var preferencesHelper: PreferencesHelper
    lateinit var coinsComponent: CoinsComponent

    private lateinit var databinding: ActivityMainBinding
    private var bottomNav: BottomNavigationView? = null
    private var navController: NavController? = null
    private var themeDialogBuilder: AlertDialog.Builder? = null
    private var themeDialog: AlertDialog? = null
    private var selectedTheme: Int = AppCompatDelegate.MODE_NIGHT_NO

    override fun onCreate(savedInstanceState: Bundle?) {
        coinsComponent = (application as MainApp).appComponent.coinsComponent().create()
        coinsComponent.injectMainActivity(this)
        selectedTheme = preferencesHelper.selectedThemeMode
        AppCompatDelegate.setDefaultNightMode(selectedTheme)
        super.onCreate(savedInstanceState)
        databinding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        applyThemingConfig()
        databinding.lifecycleOwner = this
        databinding.viewmodel = mainViewModel

        initBottomNavAndController()
        setObservers()
        setListeners()
    }

    override fun onStart() {
        super.onStart()
        initSyncWorker()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

    }

    private fun initBottomNavAndController() {
        bottomNav = databinding.bottomNavView
        navController = Navigation.findNavController(this, R.id.nav_host_container).also { controller ->
            NavigationUI.setupWithNavController(databinding.bottomNavView, controller)
        }

        navController?.addOnDestinationChangedListener { controller, destination, arguments ->
            if (destination.label != null) {
                mainViewModel.fragName.set(destination.label.toString())
            }

            if (destination.id == R.id.navigation_search) {
                databinding.appBarLayout.visibility = View.GONE
            } else {
                databinding.appBarLayout.visibility = View.VISIBLE
            }
        }
    }

    private fun initSyncWorker() {
        val syncWorkConstraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiredNetworkType(NetworkType.NOT_ROAMING)
                .setRequiresBatteryNotLow(true)
                .build()
        val syncRequest = OneTimeWorkRequestBuilder<SyncCoinsWorker>()
                .setConstraints(syncWorkConstraints)
                .build()
        WorkManager.getInstance(this.applicationContext).beginWith(syncRequest).enqueue()
    }

    private fun setObservers() {
        when (selectedTheme) {
            AppCompatDelegate.MODE_NIGHT_NO -> setThemeIcon(R.drawable.ic_sun)

            AppCompatDelegate.MODE_NIGHT_YES -> setThemeIcon(R.drawable.ic_moon)

            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> setThemeIcon(R.drawable.ic_brightness_auto)

            else -> setThemeIcon(R.drawable.ic_brightness_auto)
        }
    }

    private fun setThemeIcon(@DrawableRes iconId: Int) {
        databinding.themeSelectIcon.setImageDrawable(ContextCompat.getDrawable(this, iconId))
    }

    private fun setListeners() {
        databinding.themeSelectIcon.setOnClickListener {
            if (selectedTheme < 0 || selectedTheme > 3) {
                selectedTheme = 3
            }
            showThemeDialog()
        }
    }

    private fun initThemeSelectDialog() {
        themeDialogBuilder = AlertDialog.Builder(this)
                .setTitle("Please Select Theme")
                .setCancelable(true)
                .setSingleChoiceItems(
                        arrayOf("Light", "Dark", "Auto"),
                        selectedTheme - 1
                ) { dialogInterface: DialogInterface?, i: Int ->
                    changeTheme(i)
                }

        themeDialog = themeDialogBuilder?.create()
    }

    private fun showThemeDialog() {
        if (themeDialog == null) {
            initThemeSelectDialog()
        }
        themeDialog?.show()
    }

    private fun changeTheme(mode: Int) {
        themeDialog?.dismiss()
        lifecycleScope.launch {
            delay(100)
            when (mode) {
                0 -> preferencesHelper.selectedThemeMode = 1
                1 -> preferencesHelper.selectedThemeMode = 2
                2 -> preferencesHelper.selectedThemeMode = -1
                else -> preferencesHelper.selectedThemeMode = -1
            }
            applyTheme()
        }
    }

    private fun applyTheme() {
        this.recreate()
    }

    fun applyThemingConfig() {
        val mode: Int = preferencesHelper.selectedThemeMode
        if (mode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
            when(mode) {
                AppCompatDelegate.MODE_NIGHT_YES -> {
                    AppBarUtils.setStatusLightDark(this, false)
                    AppBarUtils.setNavBarLightDark(this, false)
                }

                AppCompatDelegate.MODE_NIGHT_NO -> {
                    AppBarUtils.setStatusLightDark(this, true)
                    AppBarUtils.setNavBarLightDark(this, true)
                }
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                    Configuration.UI_MODE_NIGHT_YES -> {
                        AppBarUtils.setStatusLightDark(this, false)
                        AppBarUtils.setNavBarLightDark(this, false)
                    }

                    Configuration.UI_MODE_NIGHT_NO -> {
                        AppBarUtils.setStatusLightDark(this, true)
                        AppBarUtils.setNavBarLightDark(this, true)
                    }
                }
            } else {
                when (mode) {
                    AppCompatDelegate.MODE_NIGHT_YES -> {
                        AppBarUtils.setStatusLightDark(this, false)
                        AppBarUtils.setNavBarLightDark(this, false)
                    }

                    AppCompatDelegate.MODE_NIGHT_NO -> {
                        AppBarUtils.setStatusLightDark(this, true)
                        AppBarUtils.setNavBarLightDark(this, true)
                    }
                }
            }
        }
    }

}
