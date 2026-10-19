package com.iqoo.tweaker.core

import android.content.Context
import android.os.IBinder
import android.os.Parcel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.io.File
import java.net.URL

data class RmsStatus(
    val activeRate: String,
    val reason: String,
    val isLocked144: Boolean
)

object RmsController {
    private const val DEX_TMP_PATH = "/data/local/tmp/RmsVote.dex"
    private const val GITHUB_DEX_URL =
        "https://raw.githubusercontent.com/AndroPlus-org/vivo-HighRefresh-NoRoot/main/RmsVote.dex"

    // Transações IPC do com.vivo.rms.IRM (vivo-services.jar)
    private const val TX_RELEASE_REFRESH_RATE = 24
    private const val TX_GET_ACTIVE_REFRESH_RATE = 26
    private const val TX_ACQUIRE_REFRESH_RATE_V2 = 31
    private const val DEFAULT_HANDLE = 900001L

    /**
     * Garante que /data/local/tmp/RmsVote.dex existe com permissão 644.
     * Extrai dos assets locais do app ou baixa do repositório oficial se necessário.
     */
    suspend fun ensureDexReady(context: Context): Boolean = withContext(Dispatchers.IO) {
        val check = ShizukuShell.exec("[ -f $DEX_TMP_PATH ] && echo OK || echo MISSING")
        if (check.stdout.contains("OK")) return@withContext true

        val localCacheDex = File(context.cacheDir, "RmsVote.dex")
        var extracted = false

        try {
            context.assets.open("RmsVote.dex").use { input ->
                localCacheDex.outputStream().use { output -> input.copyTo(output) }
            }
            extracted = true
        } catch (_: Throwable) {
            // Se o arquivo não estiver nos assets, faz download da fonte oficial
            try {
                URL(GITHUB_DEX_URL).openStream().use { input ->
                    localCacheDex.outputStream().use { output -> input.copyTo(output) }
                }
                extracted = true
            } catch (_: Throwable) {
                extracted = false
            }
        }

        if (!extracted || !localCacheDex.exists()) return@withContext false

        val copyCmd = "cp \"${localCacheDex.absolutePath}\" $DEX_TMP_PATH && chmod 644 $DEX_TMP_PATH"
        ShizukuShell.exec(copyCmd).isSuccess
    }

    /**
     * Injeção IPC Direta via ShizukuBinderWrapper (Sem Interface Token) no serviço "rms".
     * Executa em ~0,1 ms com custo nulo de CPU/bateria.
     */
    fun acquire144ViaBinderDirect(targetFps: Int = 144): Boolean {
        return try {
            val rawBinder = SystemServiceHelper.getSystemService("rms") ?: return false
            val rmsBinder: IBinder = ShizukuBinderWrapper(rawBinder)
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                // O IRM da Vivo NÃO usa writeInterfaceToken!
                data.writeLong(DEFAULT_HANDLE) // handle = 900001
                data.writeString("FixRate")    // scene
                data.writeString("RmsVote")    // reason
                data.writeInt(-1)              // display = -1
                data.writeInt(0)               // resolution = 0
                data.writeInt(targetFps)       // fps = 144
                data.writeInt(0)               // dfps = 0
                data.writeInt(0)               // configBits = 0
                data.writeInt(0)               // states = 0
                data.writeInt(100)             // priority = 100
                data.writeInt(0)               // duration = 0 (permanente até release)
                data.writeInt(0)               // caller <= 0 (system_server assume o voto)
                data.writeInt(0)               // client = 0
                data.writeInt(15)              // flags = 15 (ativa flag 2 de persist.sys.private_fps)
                data.writeInt(0)               // arg1 = 0
                data.writeInt(0)               // arg2 = 0

                rmsBinder.transact(TX_ACQUIRE_REFRESH_RATE_V2, data, reply, 0)
                true
            } finally {
                data.recycle()
                reply.recycle()
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun releaseViaBinderDirect(): Boolean {
        return try {
            val rawBinder = SystemServiceHelper.getSystemService("rms") ?: return false
            val rmsBinder: IBinder = ShizukuBinderWrapper(rawBinder)
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInt(0)               // caller = 0
                data.writeLong(DEFAULT_HANDLE) // handle = 900001
                rmsBinder.transact(TX_RELEASE_REFRESH_RATE, data, reply, 0)
                true
            } finally {
                data.recycle()
                reply.recycle()
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun getActiveRefreshRateDirect(): Float? {
        return try {
            val rawBinder = SystemServiceHelper.getSystemService("rms") ?: return null
            val rmsBinder: IBinder = ShizukuBinderWrapper(rawBinder)
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                rmsBinder.transact(TX_GET_ACTIVE_REFRESH_RATE, data, reply, 0)
                reply.readFloat()
            } finally {
                data.recycle()
                reply.recycle()
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Ativa 144Hz Nativo (Sem interpolação MEMC + Settings + Voto no RMS)
     */
    suspend fun enable144HzNative(context: Context): String {
        ensureDexReady(context)

        // 1. Injeta voto direto via Binder (ultrarrápido)
        acquire144ViaBinderDirect(144)

        // 2. Desativa interpolação do chip Q2/Q3 e configura tabela de configurações
        val commands = """
            settings put system gamecube_frame_interpolation_for_sr 0:-1:0:0:0
            settings put system peak_refresh_rate 144.0
            settings put system min_refresh_rate 144.0
            settings put global vivo_screen_refresh_rate_mode 144
            settings put system game_mode_max_framerate 144
            settings put system NV_FPSLIMIT 144
            if [ -f $DEX_TMP_PATH ]; then
                CLASSPATH=$DEX_TMP_PATH app_process / RmsVote set 144 2>/dev/null
            fi
        """.trimIndent()

        ShizukuShell.exec(commands)
        val status = queryStatus(context)
        return "144Hz Nativo Ativado com sucesso! (${status.activeRate} | ${status.reason})"
    }

    /**
     * Desativa o voto de 144Hz e restaura o LTPO padrão (1Hz - 120Hz)
     */
    suspend fun disable144HzNative(context: Context): String {
        ensureDexReady(context)
        releaseViaBinderDirect()

        val commands = """
            if [ -f $DEX_TMP_PATH ]; then
                CLASSPATH=$DEX_TMP_PATH app_process / RmsVote release 0 900001 2>/dev/null
            fi
            settings put system min_refresh_rate 1.0
            settings put system peak_refresh_rate 120.0
            settings put global vivo_screen_refresh_rate_mode 120
            settings delete system game_mode_max_framerate
            settings delete system NV_FPSLIMIT
        """.trimIndent()

        ShizukuShell.exec(commands)
        val status = queryStatus(context)
        return "LTPO Padrão Restaurado (${status.activeRate})"
    }

    /**
     * Consulta a taxa real do painel e o controlador atual (reason).
     * Em fastMode (Overlay) prioriza leitura Binder nativa para evitar picos de CPU.
     */
    suspend fun queryStatus(context: Context, fastMode: Boolean = false): RmsStatus {
        val directRate = getActiveRefreshRateDirect()

        if (fastMode && directRate != null) {
            val is144 = directRate >= 140f
            return RmsStatus(
                activeRate = "rate=$directRate",
                reason = if (is144) "RmsVote|144" else "System/App",
                isLocked144 = is144
            )
        }

        ensureDexReady(context)
        val res = ShizukuShell.exec(
            """
            if [ -f $DEX_TMP_PATH ]; then
                RATE=${'$'}(CLASSPATH=$DEX_TMP_PATH app_process / RmsVote get 2>/dev/null)
                REASON=${'$'}(CLASSPATH=$DEX_TMP_PATH app_process / RmsVote reason 2>/dev/null)
                echo "${'$'}RATE;;${'$'}REASON"
            else
                echo "rate=${directRate ?: 0f};;DirectBinder"
            fi
            """.trimIndent()
        )

        val parts = res.stdout.split(";;")
        val rateStr = parts.getOrNull(0)?.ifBlank { "rate=${directRate ?: "?"}" } ?: "rate=${directRate ?: "?"}"
        val reasonStr = parts.getOrNull(1)?.ifBlank { "Nativo" } ?: "Nativo"
        val is144 = rateStr.contains("144") || reasonStr.contains("RmsVote|144") || (directRate != null && directRate >= 140f)

        return RmsStatus(rateStr, reasonStr, is144)
    }
}
