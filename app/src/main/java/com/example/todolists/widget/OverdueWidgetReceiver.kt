package com.example.todolists.widget

import android.widget.RemoteViewsService
import com.example.todolists.ui.TaskTab

class OverdueWidgetReceiver : BaseTaskWidgetReceiver() {
    override val title = "期限切れ"
    override val tab = TaskTab.OVERDUE
    override val emptyMessage = "期限切れのタスクはありません"
    override val addKind = AddKind.NONE
    override val serviceClass: Class<out RemoteViewsService> = OverdueService::class.java
    override val tag = "OverdueV2"
}
