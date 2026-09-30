package com.example.peliselprimazo.data.repository

import com.example.peliselprimazo.BuildConfig
import com.example.peliselprimazo.domain.model.UpdateConfig
import com.example.peliselprimazo.domain.repository.ConfigRepository
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigRepositoryImpl @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig
) : ConfigRepository {

    override fun getUpdateConfig(): Flow<UpdateConfig> = callbackFlow {
        val emitUpdate = {
            val latestVersion = remoteConfig.getLong("latest_version_code").toInt()
            val latestName = remoteConfig.getString("latest_version_name")
            val updateUrl = remoteConfig.getString("update_url")
            val isForce = remoteConfig.getBoolean("is_force_update")
            val currentVersion = BuildConfig.VERSION_CODE
            
            val visualFlags = mutableMapOf<String, String>()
            remoteConfig.all.forEach { (key, value) ->
                visualFlags[key] = value.asString()
            }
            
            trySend(UpdateConfig(
                latestVersionCode = latestVersion,
                latestVersionName = latestName.ifBlank { "2.1.5" },
                updateUrl = updateUrl,
                isUpdateAvailable = latestVersion > currentVersion,
                isForceUpdate = isForce,
                visualFlags = visualFlags
            ))
        }

        emitUpdate()

        val registration = remoteConfig.addOnConfigUpdateListener(object : ConfigUpdateListener {
            override fun onUpdate(configUpdate: ConfigUpdate) {
                remoteConfig.activate().addOnCompleteListener {
                    emitUpdate()
                }
            }

            override fun onError(error: FirebaseRemoteConfigException) {}
        })

        awaitClose { registration.remove() }
    }

    override suspend fun fetchAndActivate(): Boolean {
        return try {
            remoteConfig.fetchAndActivate().await()
        } catch (e: Exception) {
            false
        }
    }
}
