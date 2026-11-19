package com.otokit.noharayh.ui.pages.pageScoreSync

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.util.DisplayMetrics
import android.view.MotionEvent
import com.otokit.noharayh.R
import com.otokit.noharayh.auth.OAuthAuthorizationCheck
import com.otokit.noharayh.auth.OAuthConnections
import com.otokit.noharayh.auth.OAuthProvider
import com.otokit.noharayh.composition.oauthConnections
import com.otokit.noharayh.composition.capturePreferences
import com.otokit.noharayh.composition.scoreSyncStartState
import com.otokit.noharayh.preferences.CapturePreferences
import com.otokit.noharayh.score.HtmlPageKind
import com.otokit.noharayh.score.ScoreGame
import com.otokit.noharayh.transfer.CaptureRequest
import com.otokit.noharayh.transfer.UploadTarget
import com.otokit.noharayh.transfer.UploadReports
import com.otokit.noharayh.transfer.UploadReport
import com.otokit.noharayh.ui.kit.common.PageContent
import com.otokit.noharayh.ui.kit.pageScoreSync.ChuDifficulty
import com.otokit.noharayh.ui.kit.pageScoreSync.ChuDifficultyButton
import com.otokit.noharayh.ui.kit.pageScoreSync.DifficultyGrid
import com.otokit.noharayh.ui.kit.pageScoreSync.MaiDifficulty
import com.otokit.noharayh.ui.kit.pageScoreSync.MaiDifficultyButton
import com.otokit.noharayh.ui.kit.pageScoreSync.ScoreSyncPanel
import com.otokit.noharayh.ui.kit.pageScoreSync.SyncTarget
import com.otokit.noharayh.ui.pages.GamePairLayout
import com.otokit.noharayh.ui.constants.display.DisplayScale
import com.otokit.noharayh.ui.window.breakpoints.WindowBreakpointPolicy
import com.otokit.noharayh.ui.window.breakpoints.WindowLayoutClass
import kotlin.math.roundToInt
import java.util.UUID

internal object ScoreSyncPage {
    fun create(context: Context, pageSwipe: (MotionEvent) -> Boolean): PageContent {
        val oauth = oauthConnections(context)
        val start = scoreSyncStartState(context)
        val preferences = capturePreferences(context)
        val mai = GameForm(context, oauth, start, preferences, mai = true)
        val chu = GameForm(context, oauth, start, preferences, mai = false)
        val pair = GamePairLayout(mai.panel, chu.panel) { left, right ->
            mai.setInteractive(left)
            chu.setInteractive(right)
        }
        val subscription = oauth.observe {
            mai.render()
            chu.render()
        }
        val reports = UploadReports.observe {
            mai.render()
            chu.render()
        }
        val starts = start.observe { mai.render(); chu.render() }
        val root = ScoreSyncSwipeLayout(context, pageSwipe).apply { addView(pair) }
        // 字标使用标准显示档。窗口真实尺寸改变时仍可切换手机／平板字号。
        val standardDensity = DisplayScale.densityDpi(
            DisplayMetrics.DENSITY_DEVICE_STABLE, context.applicationContext.resources.displayMetrics.densityDpi,
        ).toFloat() / DisplayMetrics.DENSITY_DEFAULT
        return PageContent(root,
            frameUpdater = { frame ->
                mai.panel.setWide(frame.wideLayout)
                chu.panel.setWide(frame.wideLayout)
                val wideGlyphs = WindowBreakpointPolicy.classify(frame.windowWidthPx / standardDensity) != WindowLayoutClass.Compact
                mai.setGlyphSize(((if (wideGlyphs) 51 else 34) * standardDensity).roundToInt())
                chu.setGlyphSize(((if (wideGlyphs) 21.6f else 18f) * standardDensity).roundToInt())
                pair.render(frame)
            },
            interactiveUpdater = { allowed ->
                root.setInteractive(allowed)
                pair.setInteractive(allowed)
            },
            gameColorsUpdater = { left, right ->
                mai.setAccent(left)
                chu.setAccent(right)
            },
            disposeAction = { starts.close(); reports.close(); subscription.close() },
        )
    }
}

/** 单个音游的表单草稿。授权只读，任务生命周期仍归 transfer。 */
private class GameForm(private val context: Context, private val oauth: OAuthConnections,
                       private val start: ScoreSyncStartState, private val preferences: CapturePreferences, private val mai: Boolean) {
    private var target = SyncTarget.Fish
    private var interactive = false
    private var accent = Color.DKGRAY
    private val game = if (mai) ScoreGame.MAIMAI else ScoreGame.CHUNITHM
    private var displayedReport: UploadReport? = null
    private var logText: CharSequence = ""
    private val buttons = if (mai) MaiDifficulty.entries.map { MaiDifficultyButton(context, it) }
        else ChuDifficulty.entries.map { ChuDifficultyButton(context, it) }
    private val selected = buttons.indices.toMutableSet()
    private val grid = DifficultyGrid(context, mai, buttons) { index ->
        if (!selected.remove(index)) selected.add(index)
        render()
    }
    val panel = ScoreSyncPanel(context, grid, onMode = { requested ->
        if (requested != SyncTarget.Both || (issue(OAuthProvider.DIVING_FISH) == null && issue(OAuthProvider.LXNS) == null)) {
            target = requested
        }
        render()
    }, onStart = {
        val providers = when (target) {
            SyncTarget.Fish -> listOf(OAuthProvider.DIVING_FISH)
            SyncTarget.Snow -> listOf(OAuthProvider.LXNS)
            SyncTarget.Both -> OAuthProvider.entries.toList()
        }
        if (selected.isNotEmpty() && providers.all { issue(it) == null }) {
            val kinds = if (mai) listOf(HtmlPageKind.BASIC, HtmlPageKind.ADVANCED, HtmlPageKind.EXPERT,
                HtmlPageKind.MASTER, HtmlPageKind.REMASTER, HtmlPageKind.UTAGE)
            else listOf(HtmlPageKind.BASIC, HtmlPageKind.ADVANCED, HtmlPageKind.EXPERT,
                HtmlPageKind.MASTER, HtmlPageKind.ULTIMA, HtmlPageKind.WORLDS_END)
            start.start(CaptureRequest(game, kinds.filterIndexed { index, _ -> index in selected }, UUID.randomUUID().toString(),
                preferences.keepCallbackCaptureUntilLogin(), providers.map { UploadTarget(it, oauth.account(it)!!.subject) }))
        }
    }, onCopy = { text ->
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
            ClipData.newPlainText(context.getString(R.string.sync_log), text),
        )
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, R.string.sync_log_copied, Toast.LENGTH_SHORT).show()
    })

    fun setAccent(value: Int) {
        accent = value
        render()
    }

    fun setGlyphSize(px: Int) = grid.setGlyphSize(px)

    fun setInteractive(value: Boolean) {
        if (interactive == value) return
        interactive = value
        render()
    }

    private fun issue(provider: OAuthProvider): Int? {
        val account = oauth.account(provider) ?: return R.string.sync_log_auth
        if ((account.refreshExpiresAt ?: 0L) <= System.currentTimeMillis()) return R.string.sync_log_auth
        val check = oauth.authorizationCheck(provider)
        if (check != OAuthAuthorizationCheck.VALID && check != OAuthAuthorizationCheck.UNCHECKED) {
            return R.string.sync_log_auth
        }
        return if (provider.scoreWriteScope(mai) in account.grantedScopes) null else R.string.sync_log_scope
    }

    fun render() {
        val fish = issue(OAuthProvider.DIVING_FISH)
        val snow = issue(OAuthProvider.LXNS)
        val both = fish == null && snow == null
        if (!both && target == SyncTarget.Both) target = if (snow == null) SyncTarget.Snow else SyncTarget.Fish
        val issue = when (target) {
            SyncTarget.Fish -> fish
            SyncTarget.Snow -> snow
            SyncTarget.Both -> null
        }
        val report = UploadReports.latest(game)
        if (displayedReport !== report) {
            displayedReport = report
            logText = report?.let(::styledUploadReport) ?: ""
        }
        val running = report?.terminal == false
        val otherRunning = UploadReports.latest(if (mai) ScoreGame.CHUNITHM else ScoreGame.MAIMAI)?.terminal == false
        val hint = when {
            running -> ""
            start.pending?.request?.game == game -> context.getString(if (start.pending?.step in
                listOf(SyncStartStep.PERMISSION, SyncStartStep.PERMISSION_RESULT)) R.string.sync_log_vpn else R.string.sync_log_starting)
            start.problem(game) != null -> context.getString(start.problem(game)!!.message)
            issue != null -> context.getString(issue, context.getString(target.label))
            selected.isEmpty() -> context.getString(R.string.sync_log_select)
            else -> ""
        }
        val status = when {
            logText.isNotEmpty() && hint.isNotEmpty() -> SpannableStringBuilder(logText).append("\n\n").append(hint)
            logText.isNotEmpty() -> logText
            hint.isNotEmpty() -> hint
            else -> context.getString(R.string.sync_log_empty)
        }
        panel.render(target, both, selected, start.pending == null && !running && !otherRunning && issue == null && selected.isNotEmpty(),
            interactive, accent, status)
    }
}

private val SyncStartProblem.message: Int get() = when (this) {
    SyncStartProblem.VPN_DENIED -> R.string.sync_log_vpn_denied
    SyncStartProblem.WECHAT_UNAVAILABLE -> R.string.sync_log_wechat_missing
    SyncStartProblem.UNSUPPORTED_ANDROID -> R.string.sync_log_android
    SyncStartProblem.ALREADY_RUNNING -> R.string.sync_log_busy
    SyncStartProblem.INVALID_REQUEST -> R.string.sync_log_invalid
    SyncStartProblem.UNAVAILABLE -> R.string.sync_log_unavailable
}
