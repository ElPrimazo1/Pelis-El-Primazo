package com.example.peliselprimazo.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = Firebase.auth

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = Firebase.firestore

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage = Firebase.storage

    @Provides
    @Singleton
    fun provideFirebaseRemoteConfig(): FirebaseRemoteConfig {
        val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig
        val configSettings = remoteConfigSettings {
            // Sincronización cada hora, pero en tiempo real si la app está abierta
            minimumFetchIntervalInSeconds = 3600 
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        
        // VALORES POR DEFECTO (Lo que la app usa si no cambias nada en Firebase)
        remoteConfig.setDefaultsAsync(mapOf(
            "ui_app_title" to "CFilm",
            "ui_primary_color" to "#E50914",
            "ui_secondary_color" to "#FFD700",
            "ui_loading_text" to "Preparando el cine...",
            "ui_home_announcement" to "",
            "ui_app_logo_url" to "", // Vacío usa el logo local
            "ui_player_ad_vast_url" to "https://v.mybid.io/api/vast?t=1&id=113063",
            "latest_version_code" to 1,
            "latest_version_name" to "2.1.3"
        ))
        return remoteConfig
    }
}
