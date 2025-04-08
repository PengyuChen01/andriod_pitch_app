package com.example.myapplication

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.abs

class PitchDetector {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_FLOAT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    private var lastFrequency = 0.0
    private val frequencyThreshold = 1.0 // Hz threshold for frequency change

    fun startRecording() {
        if (isRecording) return

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            ).apply {
                if (state != AudioRecord.STATE_INITIALIZED) {
                    release()
                    throw IllegalStateException("Failed to initialize AudioRecord")
                }
            }

            isRecording = true
            audioRecord?.startRecording()

            Thread {
                val buffer = FloatArray(bufferSize)
                while (isRecording && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val readSize = audioRecord?.read(buffer, 0, bufferSize, AudioRecord.READ_BLOCKING) ?: 0
                    if (readSize > 0) {
                        val frequency = detectPitch(buffer, readSize)
                        if (abs(frequency - lastFrequency) > frequencyThreshold) {
                            lastFrequency = frequency
                            currentNote = frequencyToNote(frequency)
                        }
                    }
                }
            }.start()
        } catch (e: SecurityException) {
            isRecording = false
            currentNote = "需要麦克风权限"
        } catch (e: Exception) {
            isRecording = false
            currentNote = "错误: ${e.message}"
        }
    }

    fun stopRecording() {
        isRecording = false
        try {
            audioRecord?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            // Handle any cleanup errors
        } finally {
            audioRecord = null
        }
    }

    private fun detectPitch(buffer: FloatArray, size: Int): Double {
        if (size < 2) return 0.0
        
        var maxSum = 0.0
        var maxIndex = 0
        val minPeriod = (sampleRate / 2000.0).toInt() // 2000 Hz maximum
        val maxPeriod = (sampleRate / 50.0).toInt()   // 50 Hz minimum

        // 使用自相关算法检测音高
        for (lag in minPeriod..maxPeriod) {
            var sum = 0.0
            var norm = 0.0
            for (i in 0 until size - lag) {
                sum += buffer[i] * buffer[i + lag]
                norm += buffer[i] * buffer[i]
            }
            // 归一化自相关
            if (norm > 0) {
                sum /= norm
                if (sum > maxSum) {
                    maxSum = sum
                    maxIndex = lag
                }
            }
        }

        return if (maxSum > 0.5) sampleRate.toDouble() / maxIndex else 0.0
    }

    private fun frequencyToNote(frequency: Double): String {
        if (frequency <= 0) return "未检测到声音"
        if (frequency < 20 || frequency > 4186) return "超出范围" // C0 to C8

        val a4 = 440.0
        val c0 = a4 * 2.0.pow(-4.75)
        val halfStepsBelowMiddleC = round(12 * log2(frequency / c0))
        val octave = floor(halfStepsBelowMiddleC / 12)
        val noteIndex = ((halfStepsBelowMiddleC % 12) + 12) % 12

        val notes = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        return "${notes[noteIndex.toInt()]}${octave.toInt()} (${frequency.toInt()}Hz)"
    }

    companion object {
        var currentNote: String = "等待声音输入..."
    }
} 