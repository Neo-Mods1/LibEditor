package com.neomods.libeditor

import android.app.Application
import com.neomods.libeditor.crash.CrashHandler

class LibEditorApplication : Application() {

    private lateinit var crashHandler: CrashHandler

    override fun onCreate() {
        super.onCreate()

        crashHandler = CrashHandler(this)
        crashHandler.init()
    }
}
