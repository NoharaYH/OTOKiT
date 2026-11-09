package com.otokit.noharayh

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.otokit.noharayh.databinding.ActivityMainBinding
import com.otokit.noharayh.platform.WindowOrientationController
import com.otokit.noharayh.ui.constants.typography.AppFonts
import com.otokit.noharayh.ui.shell.MainShell
import com.otokit.noharayh.ui.shell.navigation.NavigationStateCodec
import com.otokit.noharayh.ui.shell.selection.GameSelectionStateCodec
import com.otokit.noharayh.ui.window.WindowEnvironmentObserver

/**
 * 平台层：创建 binding、恢复纯状态、挂载主壳，并转发返回事件。
 *
 * 不编排页面与业务，不持有第二份导航状态；固定浅色、系统栏图标与方向策略也在这里执行。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var shell: MainShell? = null
    private var backCallback: OnBackPressedCallback? = null
    private var windowObserver: WindowEnvironmentObserver? = null
    private var orientationController: WindowOrientationController? = null

    /**
     * 固定字体缩放：只覆盖 fontScale，保留 density、语言、uiMode 等原配置。
     *
     * 不调用全局 Resources.updateConfiguration，也不在 onCreate 后重新缩放布局。
     */
    override fun attachBaseContext(newBase: Context) {
        val configuration = Configuration(newBase.resources.configuration).apply {
            fontScale = AppFonts.FIXED_FONT_SCALE
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

        val shell = MainShell(
            backgroundHost = binding.backgroundHost,
            contentHost = binding.contentHost,
            chromeHost = binding.chromeHost,
            overlayHost = binding.overlayHost,
            initialState = NavigationStateCodec.read(savedInstanceState),
            savedSelection = GameSelectionStateCodec.read(savedInstanceState),
        )
        this.shell = shell

        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                shell.consumeBack()
            }
        }.also { onBackPressedDispatcher.addCallback(this, it) }

        shell.setOnCanHandleBackChanged { enabled -> backCallback?.isEnabled = enabled }
        shell.attach()

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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        shell?.let {
            NavigationStateCodec.write(outState, it.state)
            GameSelectionStateCodec.write(outState, it.selectionState)
        }
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
