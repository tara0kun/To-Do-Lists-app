package com.example.todolists.widget

import android.widget.RemoteViewsService
import com.example.todolists.ui.TaskTab

class CompletedWidgetReceiver : BaseTaskWidgetReceiver() {
    override val title = "完了済み"
    override val tab = TaskTab.COMPLETED
    override val emptyMessage = "完了済みのタスクはありません"
    override val addKind = AddKind.NONE
    override val serviceClass: Class<out RemoteViewsService> = CompletedService::class.java
    override val tag = "CompletedV2"
}
