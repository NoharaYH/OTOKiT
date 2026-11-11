package com.otokit.noharayh

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.util.DisplayMetrics
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.otokit.noharayh.databinding.ActivityMainBinding
import com.otokit.noharayh.platform.DisplayScaleStore
import com.otokit.noharayh.platform.WindowOrientationController
import com.otokit.noharayh.platform.restoreColorOsPredictiveBackProgress
import com.otokit.noharayh.ui.constants.display.DisplayScale
import com.otokit.noharayh.ui.constants.display.DisplayScaleOption
import com.otokit.noharayh.ui.constants.typography.AppFonts
import com.otokit.noharayh.ui.shell.MainShell
import com.otokit.noharayh.ui.shell.navigation.NavigationStateCodec
import com.otokit.noharayh.ui.shell.navigation.SettingsSection
import com.otokit.noharayh.ui.shell.selection.GameSelectionStateCodec
import com.otokit.noharayh.ui.window.WindowEnvironmentObserver

/**
 * 平台层：创建 binding、恢复纯状态、挂载主壳，并转发返回事件。
 *
 * 不编排页面与业务，不持有第二份导航状态；固定浅色、系统栏图标与方向策略也在这里执行。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var displayScaleStore: DisplayScaleStore
    private var shell: MainShell? = null
    private var backCallback: OnBackPressedCallback? = null
    private var windowObserver: WindowEnvironmentObserver? = null
    private var orientationController: WindowOrientationController? = null
    private var resetTransientOnResume = false
    private var reopenSettingsAfterRecreate = false
    private var restoreSettingsAfterRecreate = false

    /**
     * 固定字体缩放与应用内显示比例。
     *
     * 显示比例以设备稳定密度为基准，不跟随系统“显示大小”的 densityDpi；
     * 不调用全局 Resources.updateConfiguration，也不在 onCreate 后重新缩放布局。
     */
    override fun attachBaseContext(newBase: Context) {
        displayScaleStore = DisplayScaleStore(newBase)
        val scale = displayScaleStore.read()
        // 只覆盖 fontScale 与 densityDpi，保留窗口宽高、方向、语言、uiMode 的继承。
        val metrics = newBase.resources.displayMetrics
        val densityDpi = DisplayScale.densityDpi(
            stableDensityDpi = DisplayMetrics.DENSITY_DEVICE_STABLE,
            fallbackDensityDpi = metrics.densityDpi,
            scale = scale.scale,
        )
        val configuration = Configuration().apply {
            fontScale = AppFonts.FIXED_FONT_SCALE
            this.densityDpi = densityDpi
        }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 在 inflate 之前固定浅色，避免首帧跟随系统夜间模式闪烁。
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        // 显式浅底深色内容，不使用按模式自动判断的另一套回调。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        restoreColorOsPredictiveBackProgress(this, binding.root)

        val navigation = NavigationStateCodec.read(savedInstanceState)
        restoreSettingsAfterRecreate =
            savedInstanceState?.getBoolean(KEY_REOPEN_SETTINGS) == true
        val reopenedSection = if (restoreSettingsAfterRecreate) {
            savedInstanceState?.getString(KEY_REOPEN_SECTION)
                ?.let { name -> SettingsSection.entries.firstOrNull { it.name == name } }
                ?: SettingsSection.App
        } else {
            null
        }
        val initialState = if (reopenedSection != null) {
            navigation.copy(settingsOpen = true, settingsSection = reopenedSection)
        } else {
            navigation.copy(settingsOpen = false)
        }

        val shell = MainShell(
            backgroundHost = binding.backgroundHost,
            contentHost = binding.contentHost,
            chromeHost = binding.chromeHost,
            overlayHost = binding.overlayHost,
            sidebarHost = binding.sideBarHost,
            // 冷启动/重建只恢复功能页；设置是模态瞬态，不能跨进程持久化。
            // 显示比例切换会主动 recreate，并通过一次性 saved state 恢复设置页。
            initialState = initialState,
            savedSelection = GameSelectionStateCodec.read(savedInstanceState),
            displayScale = displayScaleStore.read(),
            onDisplayScaleSelected = ::onDisplayScaleSelected,
            onOpenExternalLink = { url ->
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                try { startActivity(intent) } catch (_: android.content.ActivityNotFoundException) {
                    android.widget.Toast.makeText(this, "没有可打开链接的应用", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
        )
        this.shell = shell
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                shell.beginBackPreview()
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                shell.updateBackPreview(backEvent.progress)
            }

            override fun handleOnBackCancelled() {
                shell.cancelBackPreview()
            }

            override fun handleOnBackPressed() {
                shell.consumeBack()
            }
        }.also { onBackPressedDispatcher.addCallback(this, it) }

        shell.setOnCanHandleBackChanged { enabled -> backCallback?.isEnabled = enabled }
        shell.setOnStatusBarHiddenChanged { hidden -> applyStatusBarHidden(hidden) }
        shell.attach()
        resetTransientOnResume = !restoreSettingsAfterRecreate

        windowObserver = WindowEnvironmentObserver(this, binding.root) { snapshot ->
            shell.updateWindow(snapshot)
        }
        orientationController = WindowOrientationController(this).also {
            it.applyForCurrentWindow()
        }
    }

    override fun onStart() {
        super.onStart()
        windowObserver?.start()
    }

    override fun onResume() {
        super.onResume()
        if (resetTransientOnResume) {
            resetTransientOnResume = false
            shell?.resetTransientSettingsAfterRestore()
        }
        shell?.setBackgroundResumed(true)
    }

    override fun onPause() {
        shell?.setBackgroundResumed(false)
        super.onPause()
    }

    override fun onStop() {
        windowObserver?.stop()
        super.onStop()
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        // 进入/退出多窗口会改变方向能力，但不一定触发重建。
        orientationController?.applyForCurrentWindow()
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // 显示比例切换的定向 recreate 需要保留设置页；其他恢复仍只允许功能页。
        if (!restoreSettingsAfterRecreate) {
            shell?.resetTransientSettingsAfterRestore()
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        shell?.let {
            NavigationStateCodec.write(outState, it.state.copy(settingsOpen = false))
            GameSelectionStateCodec.write(outState, it.selectionState)
        }
        if (reopenSettingsAfterRecreate) {
            outState.putBoolean(KEY_REOPEN_SETTINGS, true)
            outState.putString(KEY_REOPEN_SECTION, shell?.state?.settingsSection?.name)
        }
    }

    /** 切换应用内显示比例：持久化后重建 Activity，让资源密度重新生效。 */
    private fun onDisplayScaleSelected(option: DisplayScaleOption) {
        if (displayScaleStore.read() == option) return
        displayScaleStore.write(option)
        reopenSettingsAfterRecreate = true
        recreate()
    }

    private companion object {
        const val KEY_REOPEN_SETTINGS = "display_scale_reopen_settings"
        const val KEY_REOPEN_SECTION = "display_scale_reopen_section"
    }

    /** 平台层执行系统栏意图；样式仍保持浅底深色图标。 */
    private fun applyStatusBarHidden(hidden: Boolean) {
        val controller = WindowInsetsControllerCompat(window, binding.root)
        if (hidden) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
        controller.isAppearanceLightStatusBars = true
    }

    override fun onDestroy() {
        windowObserver?.dispose()
        windowObserver = null
        backCallback?.remove()
        backCallback = null
        orientationController = null
        shell?.detach()
        shell = null
        super.onDestroy()
    }
}
