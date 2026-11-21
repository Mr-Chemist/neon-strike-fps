package ir.hiwa.neonstrike.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class SoundEngine {

    enum class Sfx {
        PISTOL,
        SHOTGUN,
        RIFLE,
        RELOAD,
        EMPTY,
        HIT,
        ENEMY_DEATH,
        PLAYER_HURT,
        PICKUP,
        LEVEL_CLEAR
    }

    private val sampleRate = 22050
    private val executor: ExecutorService = Executors.newFixedThreadPool(3)
    private val sounds: Map<Sfx, ShortArray>
    @Volatile
    var isMuted: Boolean = false

    init {
        sounds = mapOf(
            Sfx.PISTOL to generateShot(durationSec = 0.14f, baseFreq = 240f, noiseMix = 0.65f, decay = 24f),
            Sfx.SHOTGUN to generateShot(durationSec = 0.28f, baseFreq = 115f, noiseMix = 0.85f, decay = 12f),
            Sfx.RIFLE to generateShot(durationSec = 0.10f, baseFreq = 360f, noiseMix = 0.55f, decay = 30f),
            Sfx.RELOAD to generateReload(),
            Sfx.EMPTY to generateBeep(0.06f, 520f, 300f, 0.35f),
            Sfx.HIT to generateBeep(0.07f, 920f, 440f, 0.45f),
            Sfx.ENEMY_DEATH to generateExplosion(0.32f),
            Sfx.PLAYER_HURT to generateBeep(0.18f, 160f, 75f, 0.65f),
            Sfx.PICKUP to generateChime(),
            Sfx.LEVEL_CLEAR to generateFanfare()
        )
    }

    fun play(sfx: Sfx) {
        if (isMuted) return
        val pcm = sounds[sfx] ?: return
        executor.execute {
            try {
                val byteSize = pcm.size * 2
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(byteSize.coerceAtLeast(1024))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(pcm, 0, pcm.size)
                track.play()
                val durationMs = (pcm.size * 1000L / sampleRate) + 40L
                Thread.sleep(durationMs)
                track.stop()
                track.release()
            } catch (_: Throwable) {
                // Ignore audio hardware interruptions safely
            }
        }
    }

    fun release() {
        executor.shutdownNow()
    }

    private fun generateShot(
        durationSec: Float,
        baseFreq: Float,
        noiseMix: Float,
        decay: Float
    ): ShortArray {
        val count = (sampleRate * durationSec).toInt()
        val data = ShortArray(count)
        val rng = Random(42)
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val env = exp((-decay * t).toDouble()).toFloat()
            val freq = baseFreq * (1f - 0.65f * (t / durationSec))
            val tone = sin(2.0 * PI * freq * t).toFloat()
            val noise = (rng.nextFloat() * 2f - 1f)
            val sample = ((tone * (1f - noiseMix) + noise * noiseMix) * env * 0.8f)
                .coerceIn(-1f, 1f)
            data[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }
        return data
    }

    private fun generateReload(): ShortArray {
        val count = (sampleRate * 0.24f).toInt()
        val data = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val click1 = if (t < 0.05f) sin(2.0 * PI * 680.0 * t).toFloat() * exp(-45.0 * t).toFloat() else 0f
            val click2 = if (t in 0.14f..0.22f) {
                val lt = t - 0.14f
                sin(2.0 * PI * 980.0 * lt).toFloat() * exp(-38.0 * lt).toFloat()
            } else 0f
            val sample = ((click1 + click2) * 0.6f).coerceIn(-1f, 1f)
            data[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }
        return data
    }

    private fun generateBeep(
        durationSec: Float,
        startFreq: Float,
        endFreq: Float,
        volume: Float
    ): ShortArray {
        val count = (sampleRate * durationSec).toInt()
        val data = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val progress = i.toFloat() / count
            val freq = startFreq + (endFreq - startFreq) * progress
            phase += 2.0 * PI * freq / sampleRate
            val env = (1f - progress)
            val sample = (sin(phase).toFloat() * env * volume).coerceIn(-1f, 1f)
            data[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }
        return data
    }

    private fun generateExplosion(durationSec: Float): ShortArray {
        val count = (sampleRate * durationSec).toInt()
        val data = ShortArray(count)
        val rng = Random(99)
        var lowPass = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val env = exp(-8.5 * t).toFloat()
            val noise = rng.nextFloat() * 2f - 1f
            lowPass += 0.18f * (noise - lowPass)
            val rumble = sin(2.0 * PI * (95.0 - 50.0 * (t / durationSec)) * t).toFloat()
            val sample = ((lowPass * 0.7f + rumble * 0.45f) * env * 0.85f).coerceIn(-1f, 1f)
            data[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }
        return data
    }

    private fun generateChime(): ShortArray {
        val count = (sampleRate * 0.22f).toInt()
        val data = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val freq = if (t < 0.10f) 660.0 else 990.0
            val env = exp(-12.0 * t).toFloat()
            val sample = (sin(2.0 * PI * freq * t).toFloat() * env * 0.55f).coerceIn(-1f, 1f)
            data[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }
        return data
    }

    private fun generateFanfare(): ShortArray {
        val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.50)
        val noteLen = (sampleRate * 0.11f).toInt()
        val data = ShortArray(noteLen * notes.size)
        for (n in notes.indices) {
            val freq = notes[n]
            for (i in 0 until noteLen) {
                val t = i.toFloat() / sampleRate
                val env = exp(-6.0 * t).toFloat()
                val sample = (sin(2.0 * PI * freq * t).toFloat() * env * 0.5f).coerceIn(-1f, 1f)
                data[n * noteLen + i] = (sample * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return data
    }
}
