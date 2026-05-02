package com.example.todolists.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.lifecycleScope
import com.example.todolists.data.TaskRepository
import com.example.todolists.ui.theme.ToDoListsAppTheme
import kotlinx.coroutines.launch

class QuickAddActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val simple = intent.getBooleanExtra(EXTRA_SIMPLE, true)

        setContent {
            ToDoListsAppTheme {
                QuickAddDialog(
                    title = if (simple) "簡易リストに追加" else "新しいタスク",
                    placeholder = if (simple) "買い物・やる事などを入力" else "タイトルを入力",
                    onCancel = { finish() },
                    onSubmit = { text ->
                        lifecycleScope.launch {
                            val repository = TaskRepository.get(applicationContext)
                            if (simple) repository.addSimple(text) else repository.add(text)
                            finish()
                        }
                    },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_SIMPLE = "simple"

        fun intent(context: Context, simple: Boolean): Intent =
            Intent(context, QuickAddActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(EXTRA_SIMPLE, simple)
            }
    }
}

@androidx.compose.runtime.Composable
private fun QuickAddDialog(
    title: String,
    placeholder: String,
    onCancel: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    fun submit() {
        if (text.isNotBlank()) onSubmit(text.trim())
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
            }
        },
        confirmButton = {
            Button(onClick = ::submit, enabled = text.isNotBlank()) { Text("追加") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("キャンセル") }
        },
    )
}
