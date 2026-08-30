package com.example.peliselprimazo.data

import android.util.Log
import com.example.peliselprimazo.data.remote.MyBidApi
import com.example.peliselprimazo.data.remote.MyBidSpot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdsManager @Inject constructor(
    private val myBidApi: MyBidApi
) {
    private val tag = "ADS_DEBUG"

    private val _spots = MutableStateFlow<List<MyBidSpot>>(emptyList())
    val spots = _spots.asStateFlow()

    private var currentAdIndex = 0
    private val myBidToken = "1ae820c04ca90f854489a69eb551cc30"
    
    // Lista completa de enlaces de respaldo (Waterfall)
    // IMPORTANTE: Asegúrate de que en MyBid estos spots sean de tipo "Video" o "VAST Tag".
    // Si MyBid te pide un "Selector CSS", ese spot NO es para video y dará error de parseo.
    private val vastSpots = listOf(
        "https://youradexchange.com/video/select.php?r=12076098",
        "https://youradexchange.com/video/select.php?r=12076054",
        "https://youradexchange.com/video/select.php?r=12075566",
        "https://youradexchange.com/video/select.php?r=12071558",
        "https://youradexchange.com/video/select.php?r=12066914"
    )

    suspend fun loadAds() {
        Log.i(tag, "loadAds: [INICIO] Sincronizando anuncios con MyBid...")
        try {
            val response = myBidApi.getUserSpots(authToken = myBidToken)
            if (response.success && response.data != null) {
                _spots.value = response.data
                Log.i(tag, "loadAds: API sincronizada exitosamente.")
            } else {
                val response2 = myBidApi.getUserSpots(token = myBidToken)
                if (response2.success && response2.data != null) {
                    _spots.value = response2.data
                    Log.i(tag, "loadAds: API sincronizada via Fallback.")
                } else {
                    Log.w(tag, "loadAds: La API no reconoce el token. Usando lista Waterfall completa.")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "loadAds: Error en sincronización", e)
        }
    }

    fun getAllPlayerAdUrls(): List<String> {
        val syncedSpots = _spots.value
        val urls = mutableListOf<String>()
        val cb = System.currentTimeMillis()
        val appId = "com.example.peliselprimazo"

        // 1. Prioridad: Spots que vienen de la API
        syncedSpots.forEach { spot ->
            val fmt = spot.adformat.lowercase()
            if ((fmt.contains("vast") || fmt.contains("video")) && spot.status == "active") {
                spot.tag_url?.let { urls.add(it) }
            }
        }

        // 2. Si la API no trajo nada, usamos el waterfall
        if (urls.isEmpty()) {
            urls.addAll(vastSpots)
        }

        // Limpiamos los parámetros conflictivos y dejamos solo Cache Buster y App ID
        return urls.map { baseUrl ->
            val connector = if (baseUrl.contains("?")) "&" else "?"
            "$baseUrl${connector}cb=$cb&bundle=$appId"
        }.distinct()
    }

    fun getPlayerAdUrl(): String? {
        val urls = getAllPlayerAdUrls()
        if (urls.isEmpty()) {
            Log.e(tag, "getPlayerAdUrl: Lista de anuncios vacía.")
            return null
        }
        
        // Rotación Round Robin
        val index = currentAdIndex % urls.size
        val adUrl = urls[index]
        
        Log.i(tag, "getPlayerAdUrl: [ROTACIÓN] Slot $index de ${urls.size} -> $adUrl")
        
        currentAdIndex++
        return adUrl
    }

    fun getWebViewAdUrl(): String {
        val spot = _spots.value.find { 
            val fmt = it.adformat.lowercase()
            !fmt.contains("vast") && !fmt.contains("video")
        }
        return spot?.tag_url ?: "https://www.profitablecpmgate.com/jnlMnXMn39rAbLcKaldByk_BVV04Hw39ik4X_QrMJFzqXGApOsHMV1TkqLCvx_CF_zd_JhiQn0cgs9HY3HutraCpTE1OhhO13-Jwpcl5PflSV9CrcHEeYb6s5XU6e9zH"
    }
}
