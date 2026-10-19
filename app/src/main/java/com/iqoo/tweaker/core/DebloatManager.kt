package com.iqoo.tweaker.core

import android.annotation.SuppressLint

data class BloatApp(
    val packageName: String,
    val name: String,
    val description: String
)

object DebloatManager {

    // LISTA DE PROTEÇÃO OBRIGATÓRIA (NUNCA PODEM SER REMOVIDOS OU DESATIVADOS)
    val PROTECTED_PACKAGES = setOf(
        "com.vivo.gamewatch", // Controla o Ultra Game Mode e os chips de interpolação Q2/Q3
        "com.vivo.pem",       // Gerenciador central de energia, estabilidade e bateria
        "com.vivo.sps",       // Super Process System da Vivo
        "com.vivo.abe",       // Motor de background e estabilidade da Vivo
        "com.vivo.rms",       // Resource Management Service
        "android",            // Framework base do Android
        "com.android.systemui", // Interface do Sistema
        "com.bbk.launcher2"   // OriginOS Launcher
    )

    // Lista de Bloatware da ROM Chinesa do OriginOS 7 seguro para remoção
    val CN_BLOATWARE_LIST = listOf(
        BloatApp("com.xtc.originwidget", "Origin Widgets Extras", "Widgets chineses adicionais e feeds"),
        BloatApp("com.vivo.browser.novel.widget", "Novel Widget", "Widget de livros e romances chineses"),
        BloatApp("com.unionpay.tsmservice", "UnionPay TSM", "Serviço de pagamento chinês UnionPay"),
        BloatApp("com.vivo.familycare.widget", "FamilyCare Widget", "Widget de cuidado familiar chinês"),
        BloatApp("com.sina.news.vivo.widget", "Sina News Widget", "Feeds de notícias chinesas da Sina"),
        BloatApp("com.baidu.carlife.vivo", "Baidu CarLife", "Espelhamento automotivo Baidu (dispensável com Android Auto)"),
        BloatApp("com.tencent.mobileqq", "Tencent QQ", "Mensageiro QQ pré-instalado na ROM CN"),
        BloatApp("com.kugou.android", "KuGou Music", "App de streaming de música chinês"),
        BloatApp("com.vivo.hiboard", "Jovi Home / HiBoard", "Tela de assistente lateral esquerda com feeds CN"),
        BloatApp("com.baidu.input_vivo", "Baidu IME Vivo", "Teclado customizado chinês Baidu"),
        BloatApp("com.vivo.browser", "Vivo Browser CN", "Navegador padrão chinês com feeds de anúncios"),
        BloatApp("com.vivo.magazine", "Lockscreen Magazine", "Carrossel de papéis de parede chineses na tela de bloqueio"),
        BloatApp("com.vivo.wallet", "Vivo Wallet CN", "Carteira de pagamentos exclusiva para a China")
    )

    /**
     * Descobre automaticamente via Reflection o código da transação
     * TRANSACTION_setApplicationEnabledSetting do IPackageManager$Stub no Android 16/17.
     */
    @SuppressLint("PrivateApi")
    fun detectPackageTransactionCode(fallback: Int = 131): Int {
        return try {
            val stubClass = Class.forName("android.content.pm.IPackageManager\$Stub")
            val field = stubClass.getDeclaredField("TRANSACTION_setApplicationEnabledSetting")
            field.isAccessible = true
            field.getInt(null)
        } catch (_: Throwable) {
            fallback
        }
    }

    /**
     * Executa desativação/remoção com 3 camadas de bypass e proteção rigorosa.
     */
    suspend fun removePackage(
        pkg: String,
        useVivoIpcBypass: Boolean,
        customTxCode: Int
    ): String {
        val cleanPkg = pkg.trim()
        if (cleanPkg in PROTECTED_PACKAGES) {
            return "BLOQUEADO PELA TRAVA ANTI-BOOTLOOP: O pacote $cleanPkg é indispensável para o OriginOS / Ultra Game Mode!"
        }

        // Camada 1: Método padrão pm uninstall --user 0
        val stdResult = ShizukuShell.exec("pm uninstall --user 0 $cleanPkg")
        if (stdResult.stdout.contains("Success", ignoreCase = true)) {
            return "$cleanPkg desinstalado com sucesso para o usuário 0."
        }

        // Camada 2: Se falhar por restrição da Vivo (DELETE_FAILED_USER_RESTRICTED) ou solicitado bypass
        if (useVivoIpcBypass || stdResult.stdout.contains("RESTRICTED") || stdResult.stderr.contains("RESTRICTED")) {
            val disableCmd = """
                pm disable-user --user 0 $cleanPkg 2>/dev/null
                service call package $customTxCode s16 $cleanPkg i32 0 i32 0 2>/dev/null
                service call package $customTxCode s16 $cleanPkg i32 3 i32 0 2>/dev/null
                pm suspend --user 0 $cleanPkg 2>/dev/null
                cmd appops set $cleanPkg RUN_IN_BACKGROUND ignore 2>/dev/null
                cmd appops set $cleanPkg WAKE_LOCK ignore 2>/dev/null
            """.trimIndent()

            val ipcRes = ShizukuShell.exec(disableCmd)
            return "Bypass IPC Vivo aplicado (tx=$customTxCode) + Suspensão de fundo para $cleanPkg."
        }

        return "Resultado: ${stdResult.stdout} ${stdResult.stderr}"
    }

    /**
     * Restaura o aplicativo para o usuário 0.
     */
    suspend fun restorePackage(pkg: String, customTxCode: Int): String {
        val cleanPkg = pkg.trim()
        val cmd = """
            cmd package install-existing $cleanPkg 2>/dev/null
            pm enable $cleanPkg 2>/dev/null
            pm unsuspend --user 0 $cleanPkg 2>/dev/null
            cmd appops set $cleanPkg RUN_IN_BACKGROUND allow 2>/dev/null
            cmd appops set $cleanPkg WAKE_LOCK allow 2>/dev/null
            service call package $customTxCode s16 $cleanPkg i32 1 i32 0 2>/dev/null
        """.trimIndent()

        val res = ShizukuShell.exec(cmd)
        return "Restauração de $cleanPkg solicitada. Status: ${res.stdout.ifBlank { "OK" }}"
    }
}
