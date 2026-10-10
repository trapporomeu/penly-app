package com.trapporomeu.penlyapp

import android.app.Application
import com.trapporomeu.penlyapp.data.NotebookRepository
import com.trapporomeu.penlyapp.data.SettingsStore
import com.trapporomeu.penlyapp.data.ToolStore
import com.trapporomeu.penlyapp.render.BitmapCache
import com.trapporomeu.penlyapp.render.FontRegistry

class PenlyApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var repo: NotebookRepository
        private set
    lateinit var tools: ToolStore
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        repo = NotebookRepository(this, settings)
        tools = ToolStore(this)
        FontRegistry.init(repo.fontsDir)
        BitmapCache.configure(settings.value.cacheSizeMb)
    }
}
