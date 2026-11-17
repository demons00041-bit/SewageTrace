package com.sewagetrace.app

import android.app.Application
import android.content.Context
import com.sewagetrace.app.data.AppContextHolder
import com.sewagetrace.app.data.DemoRepo
import org.osmdroid.config.Configuration

/** 演示版 Application：初始化全局 Context 与本地仓储（数据只落在本机 SharedPreferences） */
class SewageTraceApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // osmdroid 必须在任何 MapView 创建之前初始化。
        // userAgentValue 不设会被 OSM 瓦片服务器直接 403，表现为地图全空白且无异常日志。
        Configuration.getInstance().apply {
            userAgentValue = packageName
            load(this@SewageTraceApp, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        }

        AppContextHolder.init(this)
        DemoRepo.load(this)
        DemoRepo.restoreSession()
    }
}
