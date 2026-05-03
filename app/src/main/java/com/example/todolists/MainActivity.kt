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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.todolists.notifications.NotificationChannels
import com.example.todolists.ui.OnboardingDialog
import com.example.todolists.ui.TaskScreen
import com.example.todolists.ui.TaskTab
import com.example.todolists.ui.TaskViewModel
import com.example.todolists.ui.theme.ToDoListsAppTheme

class MainActivity : ComponentActivity() {

    private val viewModel: TaskViewModel by viewModels()
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    private var showOnboarding by mutableStateOf(false)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            requestCalendarPermissions.launch(
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
            )
        }

    private val requestCalendarPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
            showOnboarding = false
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        NotificationChannels.ensureCreated(this)
        applyOpenTabExtra(intent)

        showOnboarding = !prefs.getBoolean(KEY_ONBOARDED, false)

        setContent {
            ToDoListsAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TaskScreen(viewModel = viewModel)
                }
                if (showOnboarding) {
                    OnboardingDialog(onConfirm = ::startPermissionChain)
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

    private fun startPermissionChain() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        requestCalendarPermissions.launch(
            arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
        )
    }

    companion object {
        const val EXTRA_OPEN_TAB = "open_tab"
        private const val PREFS = "app_prefs"
        private const val KEY_ONBOARDED = "onboarded_v1"

        fun intentForTab(context: Context, tab: TaskTab): Intent =
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(EXTRA_OPEN_TAB, tab.name)
            }

        fun intentForSimpleTab(context: Context): Intent = intentForTab(context, TaskTab.SIMPLE)
    }
}
