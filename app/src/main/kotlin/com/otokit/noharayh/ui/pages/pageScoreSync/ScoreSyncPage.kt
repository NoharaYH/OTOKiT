package com.otokit.noharayh.ui.pages.pageScoreSync

import android.content.Context
import android.graphics.Color
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import com.otokit.noharayh.R
import com.otokit.noharayh.auth.OAuthConnections
import com.otokit.noharayh.auth.OAuthFeedback
import com.otokit.noharayh.auth.OAuthProvider
import com.otokit.noharayh.ui.kit.common.PageContent
import com.otokit.noharayh.ui.kit.common.ViewPrimitives.dp
import com.otokit.noharayh.ui.kit.common.ViewPrimitives.text
import com.otokit.noharayh.ui.kit.settings.SettingsPageViews
import java.io.Closeable

object ScoreSyncPage {
    fun create(context: Context, oauth: OAuthConnections, openBrowser: (String) -> Unit): PageContent {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val inset = dp(context, 24)
            setPadding(inset, inset, inset, inset)
        }
        val summaries = OAuthProvider.entries.associateWith { provider ->
            val panel = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val summary = text(context, "", 14f, Color.DKGRAY).apply {
                setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 12))
            }
            val action = SettingsPageViews.action(
                context, context.getString(provider.authorizeLabel),
                deferClick = true,
            ) {
                oauth.authorize(provider, { url ->
                    if (content.isAttachedToWindow && content.hasWindowFocus()) openBrowser(url)
                }, { result ->
                    if (content.isAttachedToWindow) showOAuthFeedback(context, provider, result)
                })
            }
            panel.addView(summary, LinearLayout.LayoutParams(-1, -2))
            panel.addView(action, LinearLayout.LayoutParams(-1, -2))
            content.addView(
                SettingsPageViews.card(context, R.drawable.ic_sync_24, provider.label, panel),
                SettingsPageViews.gap(context),
            )
            summary
        }

        val root = object : ScrollView(context) {
            private var subscription: Closeable? = null

            override fun onAttachedToWindow() {
                super.onAttachedToWindow()
                subscription = oauth.observe {
                    for ((provider, view) in summaries) {
                        val account = oauth.account(provider)
                        view.text = when {
                            account == null -> context.getString(R.string.oauth_unconnected)
                            account.expired -> context.getString(R.string.oauth_expired)
                            account.grantedScopes.containsAll(provider.writes) ->
                                context.getString(R.string.oauth_connected, account.name.orEmpty())
                            else -> context.getString(R.string.oauth_connected_read_only, account.name.orEmpty())
                        }.trim()
                    }
                }
            }

            override fun onDetachedFromWindow() {
                subscription?.close()
                subscription = null
                super.onDetachedFromWindow()
            }
        }.apply {
            addView(content, FrameLayout.LayoutParams(-1, -2))
        }
        return PageContent(root)
    }
}

internal fun showOAuthFeedback(context: Context, provider: OAuthProvider, feedback: OAuthFeedback) {
    val message = when (feedback) {
        OAuthFeedback.AUTHORIZED -> R.string.oauth_success
        OAuthFeedback.CANCELLED -> R.string.oauth_cancelled
        OAuthFeedback.INVALID_CALLBACK -> R.string.oauth_invalid_callback
        OAuthFeedback.NETWORK_ERROR -> R.string.oauth_network_error
        OAuthFeedback.REJECTED -> R.string.oauth_rejected
        OAuthFeedback.LOCAL_ERROR -> R.string.oauth_local_error
    }
    Toast.makeText(context, context.getString(message, context.getString(provider.label)), Toast.LENGTH_SHORT).show()
}

private val OAuthProvider.label
    get() = when (this) {
        OAuthProvider.DIVING_FISH -> R.string.oauth_provider_diving_fish
        OAuthProvider.LXNS -> R.string.oauth_provider_lxns
    }

private val OAuthProvider.authorizeLabel
    get() = when (this) {
        OAuthProvider.DIVING_FISH -> R.string.oauth_authorize_diving_fish
        OAuthProvider.LXNS -> R.string.score_sync_lxns_authorize
    }
