package com.example.peliselprimazo.data

import android.app.Activity
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdsManager @Inject constructor() {
    private val tag = "AdsManager"

    private val _spots = MutableStateFlow<List<Nothing>>(emptyList())
    val spots = _spots.asStateFlow()

    suspend fun loadAds() {
        Log.i(tag, "AdsManager: No ads provider configured")
    }

    fun showInterstitialIfReady(activity: Activity, onAdDismissed: () -> Unit) {
        onAdDismissed()
    }

    fun getPlayerAdUrl(): String? = null
}
