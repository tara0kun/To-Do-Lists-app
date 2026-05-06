package com.example.todolists.widget

import android.widget.RemoteViewsService
import com.example.todolists.ui.TaskTab

class SimpleListWidgetReceiver : BaseTaskWidgetReceiver() {
    override val title = "簡易リスト"
    override val tab = TaskTab.SIMPLE
    override val emptyMessage = "簡易リストは空です"
    override val addKind = AddKind.SIMPLE
    override val serviceClass: Class<out RemoteViewsService> = SimpleListService::class.java
    override val tag = "SimpleListV2"
}
