package com.example.todolists

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.todolists.notifications.NotificationChannels
import com.example.todolists.ui.TaskScreen
import com.example.todolists.ui.TaskTab
import com.example.todolists.ui.TaskViewModel
import com.example.todolists.ui.theme.ToDoListsAppTheme

class MainActivity : ComponentActivity() {

    private val viewModel: TaskViewModel by viewModels()

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* informational */ }

    private val requestCalendarPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* informational */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        NotificationChannels.ensureCreated(this)
        ensureNotificationPermission()
        ensureCalendarPermissions()
        applyOpenTabExtra(intent)

        setContent {
            ToDoListsAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TaskScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyOpenTabExtra(intent)
    }

    private fun applyOpenTabExtra(intent: Intent?) {
        val name = intent?.getStringExtra(EXTRA_OPEN_TAB) ?: return
        val tab = runCatching { TaskTab.valueOf(name) }.getOrNull() ?: return
        viewModel.selectTab(tab)
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun ensureCalendarPermissions() {
        val toRequest = buildList {
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.READ_CALENDAR)
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.WRITE_CALENDAR)
                != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.WRITE_CALENDAR)
        }
        if (toRequest.isNotEmpty()) {
            requestCalendarPermissions.launch(toRequest.toTypedArray())
        }
    }

    companion object {
        const val EXTRA_OPEN_TAB = "open_tab"

        fun intentForTab(context: Context, tab: TaskTab): Intent =
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(EXTRA_OPEN_TAB, tab.name)
            }

        fun intentForSimpleTab(context: Context): Intent = intentForTab(context, TaskTab.SIMPLE)
    }
}
