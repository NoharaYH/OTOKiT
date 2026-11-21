package com.otokit.noharayh.composition

import android.content.Context
import com.otokit.noharayh.auth.OAuthConnections

private var connections: OAuthConnections? = null

@Synchronized
internal fun oauthConnections(context: Context): OAuthConnections =
    connections ?: OAuthConnections(context.applicationContext,
        snapshot = networkSettings(context.applicationContext)::snapshot).also { connections = it }
