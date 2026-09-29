package com.hadii.tvcasing

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val textView = TextView(this).apply {
            text = "TV Casting"
            textSize = 28f
            setPadding(64, 64, 64, 64)
        }
        setContentView(textView)
    }
}
