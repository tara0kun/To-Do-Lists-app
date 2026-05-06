package com.example.todolists.widget

import android.widget.RemoteViewsService
import com.example.todolists.ui.TaskTab

class AllTasksWidgetReceiver : BaseTaskWidgetReceiver() {
    override val title = "すべてのタスク"
    override val tab = TaskTab.ALL
    override val emptyMessage = "未完了のタスクはありません"
    override val addKind = AddKind.DETAILED
    override val serviceClass: Class<out RemoteViewsService> = AllTasksService::class.java
    override val tag = "AllTasksV2"
}
