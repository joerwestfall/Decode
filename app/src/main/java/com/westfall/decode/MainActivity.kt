package com.westfall.decode

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.westfall.decode.databinding.ActivityMainBinding
import java.nio.ByteOrder
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private var uri: Uri? = null
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if (picked != null) {
            uri = picked
            b.fileLabel.text = picked.lastPathSegment ?: "Audio selected"
            b.analyzeButton.isEnabled = true
            b.results.text = ""
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater); setContentView(b.root)
        b.openButton.setOnClickListener { picker.launch(arrayOf("audio/*")) }
        b.analyzeButton.setOnClickListener { uri?.let(::analyze) }
    }
    private fun analyze(input: Uri) {
        b.analyzeButton.isEnabled = false
        b.progress.visibility = android.view.View.VISIBLE
        b.results.text = "Decoding audio…"
        thread {
            try {
                val audio = AudioDecoder.decode(this, input)
                val report = SignalAnalyzer.analyze(audio.samples, audio.sampleRate)
                runOnUiThread { b.results.text = report; done() }
            } catch (e: Exception) {
                runOnUiThread { b.results.text = "Analysis failed: ${e.message}"; done() }
            }
        }
    }
    private fun done() { b.progress.visibility = android.view.View.GONE; b.analyzeButton.isEnabled = true }
}

data class DecodedAudio(val samples: FloatArray, val sampleRate: Int)

object AudioDecoder {
    fun decode(ctx: android.content.Context, uri: Uri): DecodedAudio {
        val ex = MediaExtractor(); ex.setDataSource(ctx, uri, null)
        var track = -1; var format: MediaFormat? = null
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i)
            if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) { track=i; format=f; break }
        }
        require(track >= 0 && format != null) { "No audio track found" }
        ex.selectTrack(track)
        val mime = format!!.getString(MediaFormat.KEY_MIME)!!
        val codec = MediaCodec.createDecoderByType(mime); codec.configure(format, null, null, 0); codec.start()
        val rate = format!!.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format!!.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val out = ArrayList<Float>(rate * 30)
        val info = MediaCodec.BufferInfo(); var inputDone=false; var outputDone=false
        while (!outputDone) {
            if (!inputDone) {
                val idx=codec.dequeueInputBuffer(10000)
                if(idx>=0){ val buf=codec.getInputBuffer(idx)!!; val size=ex.readSampleData(buf,0)
                    if(size<0){ codec.queueInputBuffer(idx,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone=true }
                    else { codec.queueInputBuffer(idx,0,size,ex.sampleTime,0); ex.advance() }
                }
            }
            val oi=codec.dequeueOutputBuffer(info,10000)
            if(oi>=0){
                val buf=codec.getOutputBuffer(oi)!!.order(ByteOrder.LITTLE_ENDIAN)
                val shorts=info.size/2; var frame=0
                while(frame+channels<=shorts){ var sum=0f; for(c in 0 until channels) sum += buf.short/32768f; out.add(sum/channels); frame+=channels }
                codec.releaseOutputBuffer(oi,false)
                if(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone=true
            }
        }
        codec.stop(); codec.release(); ex.release()
        return DecodedAudio(FloatArray(out.size){out[it]}, rate)
    }
}
