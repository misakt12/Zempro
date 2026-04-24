package com.zakazky.app

import MainView
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.zakazky.app.common.models.AppDatabase
import com.zakazky.app.common.utils.PlatformStorage

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        AppDatabase.init(PlatformStorage(applicationContext))

        window.statusBarColor = android.graphics.Color.parseColor("#0B1120")
        window.navigationBarColor = android.graphics.Color.parseColor("#0B1120")

        setContent {
            MainView()
        }
    }
}