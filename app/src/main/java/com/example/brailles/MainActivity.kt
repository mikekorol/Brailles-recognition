package com.example.brailles.recognition

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.brailles.ModelActivity
import kotlinx.android.synthetic.main.activity_main.*

typealias LumaListener = (luma: Double) -> Unit
val REQUEST_IMAGE_CAPTURE = 1

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recognize.setOnClickListener { recognition() }
    }

    fun recognition() {
        val intentRecognition = Intent(this, ModelActivity::class.java)
        startActivityForResult(intentRecognition, REQUEST_IMAGE_CAPTURE)
    }
}