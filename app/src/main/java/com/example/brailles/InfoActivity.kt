package com.example.brailles.recognition

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.brailles.PREDICTION_RESULT

class InfoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_info)

        val textViewRes: TextView? = findViewById(R.id.textViewResult)

        if (textViewRes != null) {
            textViewRes.text = PREDICTION_RESULT.get()
        }
    }
}