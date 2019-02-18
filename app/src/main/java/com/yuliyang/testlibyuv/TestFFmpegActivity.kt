package com.yuliyang.testlibyuv

import android.os.Bundle
import android.os.Environment
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_test_ffmpeg.*
import org.voiddog.ffmpeg.FFmpegNativeBridge
import java.io.File


class TestFFmpegActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_ffmpeg)

        compressMP4.setOnClickListener {

            val file = File(Environment.getExternalStorageDirectory(), "testFF.mp4")
            val outputFile = File(Environment.getExternalStorageDirectory(), "compressOut.mp4")
            if (!outputFile.exists()) {
                outputFile.createNewFile()
            }
            if (file.exists()) {
                val ret = FFmpegNativeBridge.runCommand(arrayOf("ffmpeg",
                        "-i", file.absolutePath,
                        "-y",
                        "-c:v", "libx264",
                        "-c:a", "aac",
                        "-vf", "scale=-2:640",
                        "-preset", "ultrafast", "-b:v",
                        "450k", "-b:a", "96k", outputFile.absolutePath))
                println("result   $ret")
            }

        }
    }
}