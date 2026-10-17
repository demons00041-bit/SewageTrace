package com.sewagetrace.app

import android.app.Application
import com.sewagetrace.app.data.AppContextHolder
import com.sewagetrace.app.data.DemoRepo

/** 演示版 Application：初始化全局 Context 与本地仓储（数据只落在本机 SharedPreferences） */
class SewageTraceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContextHolder.init(this)
        DemoRepo.load(this)
        DemoRepo.restoreSession()
    }
}
