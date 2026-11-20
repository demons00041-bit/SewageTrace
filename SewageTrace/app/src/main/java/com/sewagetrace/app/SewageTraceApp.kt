package com.sewagetrace.app

import android.app.Application
import com.sewagetrace.app.data.AppContextHolder
import com.sewagetrace.app.data.DemoRepo
import org.maplibre.android.MapLibre

/** 演示版 Application：初始化全局 Context 与本地仓储（数据只落在本机 SharedPreferences） */
class SewageTraceApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // MapLibre 必须在任何 MapView 创建之前初始化，否则 MapView 构造时直接崩溃。
        // OpenFreeMap 不需要 Key，所以不用传 apiKey。
        MapLibre.getInstance(this)

        AppContextHolder.init(this)
        DemoRepo.load(this)
        DemoRepo.restoreSession()
    }
}
