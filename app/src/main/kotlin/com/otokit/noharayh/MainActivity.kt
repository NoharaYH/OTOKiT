package com.otokit.noharayh

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.otokit.noharayh.databinding.ActivityMainBinding
import com.otokit.noharayh.ui.shell.MainShell
import com.otokit.noharayh.ui.shell.navigation.NavigationStateCodec

/**
 * 平台层：创建 binding、恢复纯状态、挂载主壳，并转发返回事件。
 *
 * 不编排页面与业务，不持有第二份导航状态。
 */
class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    private var shell: MainShell? = null
    private var backCallback: OnBackPressedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyRootInsets(binding.root)

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
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        shell?.let { NavigationStateCodec.write(outState, it.state) }
    }

    override fun onDestroy() {
        backCallback?.remove()
        backCallback = null
        shell?.detach()
        shell = null
        super.onDestroy()
    }

    private fun applyRootInsets(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safeInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout(),
            )
            view.setPadding(
                safeInsets.left,
                safeInsets.top,
                safeInsets.right,
                safeInsets.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
}
