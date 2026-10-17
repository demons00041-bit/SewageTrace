package com.sewagetrace.app.data

import android.content.Context

/** 全局 Application Context 持有者（避免在每个调用点传递 Context） */
object AppContextHolder {
    lateinit var ctx: Context

    fun init(context: Context) {
        if (!this::ctx.isInitialized) {
            ctx = context.applicationContext
        }
    }
}
