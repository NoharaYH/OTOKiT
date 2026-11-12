package com.otokit.noharayh.ui.animation.shell

import android.view.View
import com.otokit.noharayh.ui.constants.motion.BackgroundSwitchMotion as Spec

/** 固定黑幕时序的唯一执行者；回调不决定主题选择或输入许可。 */
internal class BackgroundSwitchRunner(private val scrim: View) {
    private var generation = 0
    private val callbacks = mutableListOf<Runnable>()

    fun play(onCovered: () -> Unit, onFinished: () -> Unit) {
        cancel()
        val token = generation
        scrim.visibility = View.VISIBLE
        scrim.alpha = 0f
        scrim.animate().alpha(1f).setDuration(Spec.COVER_MS).start()
        post(Spec.COVER_MS, token) {
            onCovered()
            if (generation != token) return@post
            post(Spec.HOLD_MS, token) {
                scrim.animate().alpha(0f).setDuration(Spec.REVEAL_MS).start()
                post(Spec.FINISH_DELAY_MS, token) {
                    scrim.visibility = View.GONE
                    onFinished()
                }
            }
        }
    }

    fun cancel() {
        generation++
        callbacks.forEach(scrim::removeCallbacks)
        callbacks.clear()
        scrim.animate().cancel()
        scrim.visibility = View.GONE
    }

    private fun post(delay: Long, token: Int, action: () -> Unit) {
        val callback = object : Runnable {
            override fun run() {
                callbacks.remove(this)
                if (generation == token) action()
            }
        }
        callbacks += callback
        scrim.postDelayed(callback, delay)
    }
}
