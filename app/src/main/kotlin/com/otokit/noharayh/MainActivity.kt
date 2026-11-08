package com.otokit.noharayh

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import com.otokit.noharayh.databinding.ActivityMainBinding
import com.otokit.noharayh.ui.constants.typography.AppFonts
import com.otokit.noharayh.ui.shell.MainShell
import com.otokit.noharayh.ui.shell.navigation.NavigationStateCodec
import com.otokit.noharayh.ui.window.WindowEnvironmentObserver

/**
 * 平台层：创建 binding、恢复纯状态、挂载主壳，并转发返回事件。
 *
 * 不编排页面与业务，不持有第二份导航状态。
 */
class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    private var shell: MainShell? = null
    private var backCallback: OnBackPressedCallback? = null
    private var windowObserver: WindowEnvironmentObserver? = null

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
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val shell = MainShell(
            backgroundHost = binding.backgroundHost,
            contentHost = binding.contentHost,
            chromeHost = binding.chromeHost,
            overlayHost = binding.overlayHost,
            initialState = NavigationStateCodec.read(savedInstanceState),
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
    }

    override fun onStart() {
        super.onStart()
        windowObserver?.start()
    }

    override fun onStop() {
        windowObserver?.stop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        shell?.let { NavigationStateCodec.write(outState, it.state) }
    }

    override fun onDestroy() {
        windowObserver?.dispose()
        windowObserver = null
        backCallback?.remove()
        backCallback = null
        shell?.detach()
        shell = null
        super.onDestroy()
    }
}
