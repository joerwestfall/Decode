package com.westfall.decode

import kotlin.math.*

object SignalAnalyzer {
    data class Peak(val hz: Double, val db: Double, val classification: String)

    fun analyze(input: FloatArray, sampleRate: Int): String {
        if(input.size < 2048) return "Recording is too short for analysis."
        val n = minOf(16384, highestPow2(input.size))
        val x = DoubleArray(n)
        val start = (input.size-n)/2
        for(i in 0 until n) {
            val w=0.5-0.5*cos(2.0*PI*i/(n-1))
            x[i]=input[start+i]*w
        }
        val re=x.copyOf(); val im=DoubleArray(n); fft(re,im)
        val mags=DoubleArray(n/2){ i -> 20*log10(max(1e-12, hypot(re[i],im[i])/n)) }
        val candidates=mutableListOf<Peak>()
        for(i in 2 until mags.size-2) if(mags[i]>mags[i-1] && mags[i]>=mags[i+1]) {
            val hz=i.toDouble()*sampleRate/n
            if(hz>=10) candidates += Peak(hz,mags[i], classify(hz))
        }
        val peaks=candidates.sortedByDescending{it.db}.take(16)
        val rms=sqrt(input.fold(0.0){a,v->a+v*v}/input.size)
        return buildString {
            appendLine("SIGNAL FORENSICS REPORT")
            appendLine("Sample rate: $sampleRate Hz")
            appendLine("Samples: ${input.size}")
            appendLine("Duration: %.2f s".format(input.size.toDouble()/sampleRate))
            appendLine("RMS level: %.1f dBFS".format(20*log10(max(rms,1e-12))))
            appendLine()
            appendLine("PROMINENT COMPONENTS")
            peaks.forEachIndexed { idx,p ->
                appendLine("%2d. %8.2f Hz  %7.1f dB  %s".format(idx+1,p.hz,p.db,p.classification))
            }
            appendLine()
            appendLine("Interpretation is evidence-based: a spectral peak alone is not proof of an encoded message.")
        }
    }

    private fun classify(hz:Double):String {
        for(base in doubleArrayOf(60.0,50.0)) {
            val harmonic=(hz/base).roundToInt()
            if(harmonic>=1 && abs(hz-harmonic*base)<1.2)
                return "KNOWN INTERFERENCE: probable ${base.toInt()} Hz mains harmonic ×$harmonic"
        }
        return "STRUCTURED / UNIDENTIFIED"
    }
    private fun highestPow2(n:Int):Int { var p=1; while(p<=n/2)p*=2; return p }
    private fun fft(re:DoubleArray, im:DoubleArray) {
        val n=re.size; var j=0
        for(i in 1 until n){ var bit=n shr 1; while(j and bit != 0){j=j xor bit; bit=bit shr 1}; j=j xor bit
            if(i<j){ val tr=re[i];re[i]=re[j];re[j]=tr; val ti=im[i];im[i]=im[j];im[j]=ti } }
        var len=2
        while(len<=n){ val ang=-2*PI/len; val wr0=cos(ang); val wi0=sin(ang)
            var i=0
            while(i<n){ var wr=1.0;var wi=0.0
                for(k in 0 until len/2){ val u=i+k;val v=u+len/2
                    val tr=re[v]*wr-im[v]*wi;val ti=re[v]*wi+im[v]*wr
                    re[v]=re[u]-tr;im[v]=im[u]-ti;re[u]+=tr;im[u]+=ti
                    val nw=wr*wr0-wi*wi0;wi=wr*wi0+wi*wr0;wr=nw }
                i+=len }
            len*=2 }
    }
}
