package com.chacha.jadeime

import android.app.Application

class JadeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
