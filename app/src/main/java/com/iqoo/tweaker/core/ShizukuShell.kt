package com.iqoo.tweaker.core

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String) {
    val isSuccess: Boolean get() = exitCode == 0
}

object ShizukuShell {

    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }

    fun hasPermission(): Boolean {
        if (!isAvailable()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    fun requestPermission(requestCode: Int = 1001) {
        if (isAvailable() && !hasPermission()) {
            try {
                Shizuku.requestPermission(requestCode)
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * Executa comandos shell no contexto UID 2000 (ADB/Shell) do Shizuku.
     * Utiliza reflexão para invocar newProcess garantindo compatibilidade entre versões da API
     * e leitura concorrente dos streams stdout/stderr para evitar deadlocks de buffer.
     */
    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            return@withContext ShellResult(-1, "", "Permissão do Shizuku não concedida.")
        }
        try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }

            val process = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as Process

            // Leitura concorrente em corrotinas assíncronas para eliminar risco de deadlock
            val stdoutDeferred = async(Dispatchers.IO) {
                BufferedReader(InputStreamReader(process.inputStream)).use { it.readText().trim() }
            }
            val stderrDeferred = async(Dispatchers.IO) {
                BufferedReader(InputStreamReader(process.errorStream)).use { it.readText().trim() }
            }

            val exitCode = process.waitFor()
            val stdout = stdoutDeferred.await()
            val stderr = stderrDeferred.await()

            ShellResult(exitCode, stdout, stderr)
        } catch (e: Throwable) {
            ShellResult(-1, "", e.message ?: "Erro ao executar comando via Shizuku")
        }
    }
}
