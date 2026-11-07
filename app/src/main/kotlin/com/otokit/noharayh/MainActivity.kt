package com.otokit.noharayh

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val root = findViewById<android.view.View>(R.id.main)
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

