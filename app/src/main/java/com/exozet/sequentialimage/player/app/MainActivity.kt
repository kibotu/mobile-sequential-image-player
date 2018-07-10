package com.exozet.sequentialimage.player.app

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.exozet.sequentialimage.player.SequentialImagePlayerActivity
import com.exozet.sequentialimage.player.parseAssetFile
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        default_video.setOnClickListener {
            startSequentialPlayer((1 until 317).map { parseAssetFile(String.format("default/out%d.png", it)) }.toTypedArray())
        }
        stabilized_video.setOnClickListener {
            startSequentialPlayer((1 until 192).map { parseAssetFile(String.format("stabilized/out%03d.png", it)) }.toTypedArray())
        }
    }

    private fun startSequentialPlayer(list: Array<Uri>) {
        SequentialImagePlayerActivity.Builder
                .with(this)
                .uris(list)
                .fps(30) // default: 30
                .playBackwards(false) // default: false
                .autoPlay() // default: true
                .zoomable() // default: true
                .translatable() // default: true
                .showControls() // default: false
                .swipeSpeed(0.8f) // default: 1
                .blurLetterbox() // default: true
                .startActivity()
    }
}