package com.example.modmenu

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(60, 60, 60, 60)
        }
        val info = TextView(this).apply {
            text = "Нажми «Включить», выдай разрешение «Поверх других окон» и вернись сюда."
            textSize = 16f
            gravity = Gravity.CENTER
        }
        val start = Button(this).apply {
            text = "Включить меню"
            setOnClickListener { startMenu() }
        }
        val stop = Button(this).apply {
            text = "Выключить меню"
            setOnClickListener { stopService(Intent(this@MainActivity, OverlayService::class.java)) }
        }
        layout.addView(info)
        layout.addView(start)
        layout.addView(stop)
        setContentView(layout)
    }

    private fun startMenu() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Разреши «Поверх других окон», потом нажми снова", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        }
        val i = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        moveTaskToBack(true)
    }
}
