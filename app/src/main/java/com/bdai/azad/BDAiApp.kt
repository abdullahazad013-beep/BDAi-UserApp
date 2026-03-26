package com.bdai.azad
import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex
class BDAiApp : Application() {
    override fun onCreate() { super.onCreate() }
    override fun attachBaseContext(base: Context) { super.attachBaseContext(base); MultiDex.install(this) }
}
