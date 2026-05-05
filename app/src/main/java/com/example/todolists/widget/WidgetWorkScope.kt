package com.example.todolists.widget

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-scoped coroutine scope for fire-and-forget work that needs to
 * outlive a Glance ActionCallback. Glance gives the action a short
 * coroutine which it cancels when the callback returns; anything we want
 * to keep running after the action (DB writes, calendar sync, downstream
 * widget refreshes) goes through this scope instead.
 */
internal object WidgetWorkScope : CoroutineScope by CoroutineScope(
    SupervisorJob() + Dispatchers.IO,
)
