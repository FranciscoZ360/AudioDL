package com.audiodl.app

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Ejecuta el binario de FFmpeg que trae youtubedl-android
 * (ya extraído por FFmpeg.getInstance().init en App.kt).
 */
object FfmpegTool {

    private fun binary(ctx: Context) = File(ctx.applicationInfo.nativeLibraryDir, "libffmpeg.so")

    private fun builder(ctx: Context, args: List<String>): ProcessBuilder {
        val packages = File(ctx.noBackupFilesDir, "youtubedl-android/packages")
        val libs = listOf("ffmpeg/usr/lib", "python/usr/lib")
            .joinToString(":") { File(packages, it).absolutePath }
        return ProcessBuilder(listOf(binary(ctx).absolutePath) + args).apply {
            environment()["LD_LIBRARY_PATH"] = libs
            environment()["TMPDIR"] = ctx.cacheDir.absolutePath
            directory(ctx.cacheDir)
        }
    }

    private val common = listOf("-hide_banner", "-nostdin", "-loglevel", "error", "-y")

    fun run(ctx: Context, args: List<String>) {
        val process = builder(ctx, common + args).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val code = process.waitFor()
        if (code != 0) {
            throw IllegalStateException("FFmpeg falló ($code): ${output.takeLast(300).trim()}")
        }
    }

    /** Envolvente de volumen normalizada (0..1) para dibujar la forma de onda. */
    fun peaks(ctx: Context, input: File, buckets: Int): FloatArray {
        val errFile = File(ctx.cacheDir, "ffmpeg_peaks.log")
        val process = builder(
            ctx,
            common + listOf("-i", input.absolutePath, "-vn", "-ac", "1", "-ar", "800", "-f", "s16le", "-")
        ).redirectError(errFile).start()
        process.outputStream.close()

        val env = ArrayList<Float>()
        var blockMax = 0
        var count = 0
        BufferedInputStream(process.inputStream, 1 shl 16).use { ins ->
            while (true) {
                val lo = ins.read()
                if (lo < 0) break
                val hi = ins.read()
                if (hi < 0) break
                val a = abs(((hi shl 8) or lo).toShort().toInt())
                if (a > blockMax) blockMax = a
                if (++count == 16) {
                    env.add(blockMax.toFloat()); blockMax = 0; count = 0
                }
            }
        }
        process.waitFor()
        errFile.delete()
        if (env.isEmpty()) throw IllegalStateException("No se pudo analizar el audio.")

        val n = minOf(buckets, env.size)
        val out = FloatArray(n) { i ->
            val from = i * env.size / n
            val to = ((i + 1) * env.size / n).coerceAtLeast(from + 1).coerceAtMost(env.size)
            var m = 0f
            for (k in from until to) if (env[k] > m) m = env[k]
            m
        }
        val maxV = out.maxOrNull()?.takeIf { it > 0f } ?: 1f
        for (i in out.indices) out[i] = sqrt(out[i] / maxV)
        return out
    }

    /** Recorta [startMs, endMs]. Sin fundido copia el audio sin recodificar. */
    fun trim(ctx: Context, input: File, output: File, startMs: Long, endMs: Long, fade: Boolean) {
        val durMs = endMs - startMs
        val base = listOf(
            "-ss", secs(startMs), "-i", input.absolutePath, "-t", secs(durMs),
            "-map", "0:a:0", "-map_metadata", "-1"
        )
        if (!fade) {
            try {
                run(ctx, base + listOf("-c:a", "copy", output.absolutePath))
                if (output.length() > 0L) return
            } catch (e: Exception) {
                output.delete()
            }
        }
        val encode = buildList {
            if (fade) {
                val f = minOf(1000L, durMs / 4)
                add("-af")
                add("afade=t=in:st=0:d=${secs(f)},afade=t=out:st=${secs(durMs - f)}:d=${secs(f)}")
            }
            addAll(listOf("-c:a", "libmp3lame", "-q:a", "2"))
        }
        run(ctx, base + encode + output.absolutePath)
    }

    private fun secs(ms: Long) = String.format(Locale.US, "%.3f", ms / 1000.0)
}
