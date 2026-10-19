package com.iqoo.tweaker.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iqoo.tweaker.core.*
import com.iqoo.tweaker.service.FloatingOverlayService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TweakerMainScreen()
                }
            }
        }
    }
}

@Composable
fun TweakerMainScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("144Hz Nativo", "Touch/Monster", "Debloat CN", "Overlay & Tile")

    Column(modifier = Modifier.fillMaxSize()) {
        ShizukuStatusBanner()

        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTab) {
            0 -> Tab144HzScreen()
            1 -> TabPerformanceTouchScreen()
            2 -> TabDebloatScreen()
            3 -> TabOverlayAndTileScreen()
        }
    }
}

@Composable
fun ShizukuStatusBanner() {
    var hasPerm by remember { mutableStateOf(ShizukuShell.hasPermission()) }

    LaunchedEffect(Unit) {
        while (true) {
            hasPerm = ShizukuShell.hasPermission()
            delay(2000)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasPerm) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (hasPerm) "Shizuku Conectado (UID 2000)" else "Shizuku Desconectado / Sem Permissão",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "iQOO 15T • OriginOS 7 • Sem Root",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!hasPerm) {
                Button(onClick = { ShizukuShell.requestPermission() }) {
                    Text("Autorizar")
                }
            }
        }
    }
}

@Composable
fun Tab144HzScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(RmsStatus("...", "...", false)) }
    var logText by remember { mutableStateOf("Pronto para desbloquear 144Hz nativos sem interpolação MEMC.") }

    LaunchedEffect(Unit) {
        while (true) {
            if (ShizukuShell.hasPermission()) {
                status = RmsController.queryStatus(context)
            }
            delay(2500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Status do Painel LTPO (Tempo Real)", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Frequência Física: ${status.activeRate}")
                Text("Controlador Atual (Reason): ${status.reason}")
                Text("Interpolação MEMC (Q2/Q3): Neutralizada (0:-1:0:0:0)")
                Text("Trava persist.sys.private_fps: Ativa via Flags=15")
            }
        }

        Button(
            onClick = {
                scope.launch {
                    logText = "Aplicando 144Hz Nativo via RMS Binder + RmsVote..."
                    logText = RmsController.enable144HzNative(context)
                    status = RmsController.queryStatus(context)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("LIGAR 144Hz NATIVOS (Sem Interpolação)")
        }

        OutlinedButton(
            onClick = {
                scope.launch {
                    logText = RmsController.disable144HzNative(context)
                    status = RmsController.queryStatus(context)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("DESLIGAR 144Hz (Restaurar LTPO 1–120Hz)")
        }

        Text(text = logText, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun TabPerformanceTouchScreen() {
    val scope = rememberCoroutineScope()
    var touchBoostEnabled by remember { mutableStateOf(false) }
    var cpuGpuBoostEnabled by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Touch & Multitouch Ultra Boost", fontWeight = FontWeight.Bold)
                    Text(
                        "SettleInterval 0.2ms, QuietInterval 0.1ms, MinDistance 0px, Pressure 0.01",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = touchBoostEnabled,
                    onCheckedChange = { enabled ->
                        touchBoostEnabled = enabled
                        scope.launch {
                            val cmd = if (enabled) {
                                """
                                settings put system game_mode_touch_boost_enabled true
                                settings put system adaptive_touch_sensitivity 1
                                settings put system MultitouchMinDistance 0px
                                settings put system MultitouchSettleInterval 0.2ms
                                settings put system QuietInterval 0.1ms
                                settings put system PressureForID 0.01
                                settings put system PointerVelocityControlParameters 1
                                """.trimIndent()
                            } else {
                                """
                                settings put system game_mode_touch_boost_enabled false
                                settings delete system MultitouchMinDistance
                                settings delete system MultitouchSettleInterval
                                settings delete system QuietInterval
                                settings delete system PressureForID
                                """.trimIndent()
                            }
                            ShizukuShell.exec(cmd)
                            feedback = if (enabled) "Touch Boost ativado!" else "Touch Boost revertido."
                        }
                    }
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("CPU/GPU Monster Boost", fontWeight = FontWeight.Bold)
                    Text(
                        "Ativa game_processor_boost=1, game_ub_turbo_mode=1 e perfil performance",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = cpuGpuBoostEnabled,
                    onCheckedChange = { enabled ->
                        cpuGpuBoostEnabled = enabled
                        scope.launch {
                            val cmd = if (enabled) {
                                """
                                settings put system game_mode_cpu_performance performance
                                settings put system game_mode_gpu_render_mode performance
                                settings put system game_processor_boost 1
                                settings put system game_ub_turbo_mode 1
                                """.trimIndent()
                            } else {
                                """
                                settings put system game_mode_cpu_performance normal
                                settings put system game_mode_gpu_render_mode normal
                                settings put system game_processor_boost 0
                                settings put system game_ub_turbo_mode 0
                                """.trimIndent()
                            }
                            ShizukuShell.exec(cmd)
                            feedback = if (enabled) "Monster CPU/GPU Boost ativado!" else "Monster Boost revertido."
                        }
                    }
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Nota sobre Spoofing de Benchmark:", fontWeight = FontWeight.Bold)
                Text(
                    "O projeto inclui o flavor 'benchmarkSpoof' (applicationId: com.ludashi.benchmark). " +
                    "Ao instalar esse APK alternativo, o OriginOS 7 desbloqueia o perfil térmico majorMode=\"2\" para clocks sustentados.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (feedback.isNotEmpty()) {
            Text(feedback, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun TabDebloatScreen() {
    val scope = rememberCoroutineScope()
    var useVivoBypass by remember { mutableStateOf(true) }
    var txCode by remember {
        mutableStateOf(DebloatManager.detectPackageTransactionCode(131).toString())
    }
    var actionLog by remember { mutableStateOf("Trava anti-bootloop ativa: com.vivo.gamewatch e com.vivo.pem estão blindados.") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Bypass Vivo IPC (Bypass DELETE_RESTRICTED)", fontWeight = FontWeight.Bold)
                    Switch(checked = useVivoBypass, onCheckedChange = { useVivoBypass = it })
                }
                OutlinedTextField(
                    value = txCode,
                    onValueChange = { txCode = it },
                    label = { Text("Código IPC do IPackageManager (Auto-detectado / 131 / 132 / 134)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(actionLog, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(DebloatManager.CN_BLOATWARE_LIST) { app ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(app.name, fontWeight = FontWeight.Bold)
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                        Text(app.description, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                scope.launch {
                                    val code = txCode.toIntOrNull() ?: 131
                                    actionLog = DebloatManager.removePackage(app.packageName, useVivoBypass, code)
                                }
                            }) {
                                Text("Remover / Suspender")
                            }
                            OutlinedButton(onClick = {
                                scope.launch {
                                    val code = txCode.toIntOrNull() ?: 131
                                    actionLog = DebloatManager.restorePackage(app.packageName, code)
                                }
                            }) {
                                Text("Restaurar")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TabOverlayAndTileScreen() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("1. Bloco na Central de Controle (Quick Settings Tile)", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Substituto ideal para o Termux:Widget (bloqueado pelo launcher do OriginOS 7). " +
                    "Arraste a Central de Controle para baixo, toque em editar blocos e adicione '144Hz Nativo'. " +
                    "Você poderá ligar/desligar os 144Hz com 1 toque mesmo dentro do Call of Duty ou Brawl Stars!"
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("2. Overlay Flutuante de Hz & Auto-Lock Watchdog", fontWeight = FontWeight.Bold)
                Text(
                    "Exibe em tempo real a taxa e o reason na tela. Se o jogo tentar forçar 120Hz via reqFps, " +
                    "o serviço reaplica o voto de 144Hz automaticamente sem causar stutter."
                )

                Button(
                    onClick = {
                        if (!Settings.canDrawOverlays(context)) {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        } else {
                            context.startForegroundService(Intent(context, FloatingOverlayService::class.java))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Iniciar Overlay Flutuante")
                }

                OutlinedButton(
                    onClick = {
                        context.stopService(Intent(context, FloatingOverlayService::class.java))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Parar Overlay Flutuante")
                }
            }
        }
    }
}
