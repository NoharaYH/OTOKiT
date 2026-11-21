package com.otokit.noharayh.composition

import android.content.Context
import com.otokit.noharayh.network.NetworkSettings
import com.otokit.noharayh.storage.secure.EncryptedStringPreferences

private var networkConfiguration: NetworkSettings? = null

@Synchronized
fun networkSettings(context: Context): NetworkSettings = networkConfiguration ?: run {
    val storage = EncryptedStringPreferences(context.applicationContext, "network_settings", "OTOKiT.NetworkSettings")
    NetworkSettings({ storage.read("overrides") }, { storage.write("overrides", it) })
}.also { networkConfiguration = it }
