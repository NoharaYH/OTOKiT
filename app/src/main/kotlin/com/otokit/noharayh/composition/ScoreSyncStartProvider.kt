package com.otokit.noharayh.composition

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.otokit.noharayh.ui.pages.pageScoreSync.ScoreSyncStartState

/** Both the page and the Activity platform adapter use the same Activity-scoped handoff. */
internal fun scoreSyncStartState(context: Context): ScoreSyncStartState {
    val owner = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<ViewModelStoreOwner>().first()
    return ViewModelProvider(owner)[ScoreSyncStartState::class.java]
}
