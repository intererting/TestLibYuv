package com.example.android.camera2basic.rtmp

import android.annotation.SuppressLint
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.util.Log
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

typealias AudioCallBack = (realData: ByteBuffer) -> Unit

typealias CodecChangeCallBack = (realData: ByteBuffer) -> Unit

object AudioRecorder {

    private const val TAG = "AudioRecorder"
    private const val encodeType = MediaFormat.MIMETYPE_AUDIO_AAC
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO //音频通道(单声道)
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT //音频格式
    private const val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC  //音频源（麦克风）
    private const val maxBufferSize = 4096
    private var callBack: AudioCallBack? = null
    private var codecCallback: CodecChangeCallBack? = null

    private lateinit var mediaEncode: MediaCodec
    private lateinit var encodeBufferInfo: MediaCodec.BufferInfo
    private var recorderThread: Thread

    @Volatile
    private var isRecording = AtomicBoolean(false)//录音标志
    private var audioRecord: AudioRecord? = null

    private var canceler: AcousticEchoCanceler? = null//回声消除


    val isDeviceSupport: Boolean
        get() = AcousticEchoCanceler.isAvailable()


    fun initAEC(audioSession: Int): Boolean {
        if (canceler != null) {
            return false
        }
        canceler = AcousticEchoCanceler.create(audioSession)
        if (canceler != null) {
            canceler!!.enabled = true
        }
        return canceler!!.enabled
    }

    private fun setAECEnabled(enable: Boolean): Boolean {
        if (null == canceler) {
            return false
        }
        canceler!!.enabled = enable
        return canceler!!.enabled
    }

    init {
        recorderThread = Thread(RecorderTask())
    }

    /*开始录音*/
    fun startAudioRecording(callBack: AudioCallBack, codecCallback: CodecChangeCallBack) {
        this.callBack = callBack
        this.codecCallback = codecCallback
        if (!recorderThread.isAlive) {
            initAACMediaEncode()
            recorderThread.start()
        }
    }

    /*停止录音*/
    fun stopAudioRecording() {
        if (audioRecord != null) {
            isRecording.compareAndSet(true, false)
            audioRecord!!.stop()
            audioRecord!!.release()
            audioRecord = null
        }
        //释放回声消除器
        setAECEnabled(false)
        try {
            mediaEncode.stop()
            mediaEncode.release()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }

    class RecorderTask : Runnable {
        override fun run() {
            //获取最小缓冲区大小
            val bufferSizeInBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            //回声消除
            audioRecord = AudioRecord(AUDIO_SOURCE,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSizeInBytes * 8)
            if (isDeviceSupport) {
                initAEC(audioRecord!!.audioSessionId)
            }
            mediaEncode.start()
            audioRecord!!.startRecording()
            isRecording.compareAndSet(false, true)

            var buffer: ByteArray
            var bufferReadResult: Int
            while (isRecording.get()) {
                buffer = ByteArray(maxBufferSize)
                //从缓冲区中读取数据，存入到buffer字节数组数组中
                bufferReadResult = audioRecord!!.read(buffer, 0, buffer.size)
                //判断是否读取成功
                if (bufferReadResult == AudioRecord.ERROR_BAD_VALUE || bufferReadResult == AudioRecord.ERROR_INVALID_OPERATION)
                    Log.e(TAG, "Audio Read error")
                if (bufferReadResult > 0) {
                    try {
                        dstAudioFormatFromPCM(buffer)
                    } catch (e: IllegalStateException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }


    /**
     * 初始化AAC编码器
     */
    private fun initAACMediaEncode() {
        try {
            //参数对应-> mime type、采样率、声道数
            val encodeFormat = MediaFormat.createAudioFormat(encodeType, SAMPLE_RATE, 1)
            encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)//比特率
            encodeFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO)
            encodeFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            encodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxBufferSize)//作用于inputBuffer的大小
            mediaEncode = MediaCodec.createEncoderByType(encodeType)
            mediaEncode.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        } catch (e: IOException) {
            println("initAACMediaEncode error")
            e.printStackTrace()
        }
    }

    /**
     * 编码PCM数据 得到AAC格式的音频
     */
    @SuppressLint("SwitchIntDef")
    private fun dstAudioFormatFromPCM(pcmData: ByteArray) {
        val inputBuffer: ByteBuffer?
        var outputBuffer: ByteBuffer?
        encodeBufferInfo = MediaCodec.BufferInfo()
        val inputIndex = mediaEncode.dequeueInputBuffer(5000)
        inputBuffer = mediaEncode.getInputBuffer(inputIndex)
        inputBuffer.clear()
        inputBuffer.limit(pcmData.size)
        inputBuffer.put(pcmData)//PCM数据填充给inputBuffer
        mediaEncode.queueInputBuffer(inputIndex, 0, pcmData.size, 0, 0)//通知编码器 编码
        var outputIndex = mediaEncode.dequeueOutputBuffer(encodeBufferInfo, 5000)
        while (true) {
            when (outputIndex) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val csd0 = mediaEncode.outputFormat.getByteBuffer("csd-0")
                    codecCallback?.invoke(csd0)
                }
                in (1..Int.MAX_VALUE) -> {
                    outputBuffer = mediaEncode.getOutputBuffer(outputIndex)
                    outputBuffer.position(encodeBufferInfo.offset)
                    outputBuffer.limit(encodeBufferInfo.offset + encodeBufferInfo.size)
                    callBack?.invoke(outputBuffer)
                }
            }
            mediaEncode.releaseOutputBuffer(outputIndex, false)
            outputIndex = mediaEncode.dequeueOutputBuffer(encodeBufferInfo, 5000)
        }
    }
}
