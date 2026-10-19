# iQOO 15T Tweaker & 144Hz Native Unlocker (OriginOS 7 / Android 16/17)

Aplicativo Android nativo em **Kotlin + Jetpack Compose (Material 3)** e conjunto de ferramentas para **iQOO 15T (ROM Chinesa)** sem necessidade de root, operando via **Shizuku (UID 2000)**.

---

## ⚡ Funcionalidades Principais

1. **Desbloqueio de 144Hz Nativos (Sem Interpolação MEMC):**
   - Injeta o voto IPC bruto no serviço `com.vivo.rms.IRM` via `RmsVote.dex` e transação Binder nativa (`tx 31` com `flags=15`), liberando a propriedade protegida `persist.sys.private_fps`.
   - Neutraliza a interpolação sintética de quadros (`gamecube_frame_interpolation_for_sr 0:-1:0:0:0`), evitando queda de resolução para 720p e atraso de entrada (input lag).
   - Suporte a jogos globais (Call of Duty: Mobile, Brawl Stars, etc.) em 144Hz reais.
2. **Touch & Monster Boost:**
   - Reduz o intervalo de estabilização do multitoque para `0.2ms` e latência para `0.1ms`.
   - Força o perfil de máximo desempenho de CPU/GPU do sistema.
   - Inclui build flavor `benchmarkSpoof` (`com.ludashi.benchmark`) para liberar o perfil térmico `majorMode="2"` no OriginOS 7.
3. **Debloat Seguro para ROM Chinesa com Tripla Camada de Bypass:**
   - Remove ou desativa bloatwares nativos contornando o erro `[DELETE_FAILED_USER_RESTRICTED]` através de transações diretas no PackageManager e corte de processos de fundo via `appops`.
   - **Trava Anti-Bootloop Inviolável:** Proteção estrita que impede a remoção de pacotes vitais como `com.vivo.gamewatch` (Ultra Game Mode) e `com.vivo.pem` (gerenciador de energia).
4. **Quick Settings Tile (Central de Controle):**
   - Bloco nativo na barra de notificações para ligar e desligar os 144Hz em 1 toque de dentro de qualquer jogo, substituindo o Termux:Widget (bloqueado pelo launcher da Vivo).
5. **Overlay Flutuante & Auto-Lock Watchdog:**
   - Monitor leve em tempo real que exibe taxa física e razão do controle, com reconexão automática do voto de 144Hz se o jogo tentar forçar 120Hz via `reqFps`.

---

## 📦 Estrutura do Projeto

```text
iQOO15TTweaker/
├── .github/workflows/
│   └── build-apk.yml               <-- Compilação automática de APKs na nuvem
├── app/
│   ├── build.gradle.kts            <-- Compose, Shizuku e Flavors
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml     <-- Permissions, TileService e ForegroundService
│       ├── assets/
│       │   └── RmsVote.dex         <-- Binário oficial pré-incluído (AndroPlus)
│       └── java/com/iqoo/tweaker/
│           ├── core/
│           │   ├── ShizukuShell.kt     <-- Shell assíncrono anti-deadlock via Shizuku
│           │   ├── RmsController.kt   <-- Motor híbrido Binder IPC + RmsVote.dex
│           │   └── DebloatManager.kt  <-- Debloat CN com bypass e trava de segurança
│           ├── service/
│           │   ├── Quick144TileService.kt    <-- Botão na Central de Controle
│           │   └── FloatingOverlayService.kt <-- Overlay flutuante e Watchdog
│           └── ui/
│               └── MainActivity.kt           <-- Interface Material 3 em 4 abas
├── iqoo15t-tweaker.sh              <-- Script CLI para execução imediata no aparelho via rish
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew
```

---

## 🚀 Como Compilar o APK

### Opção A: Pelo GitHub Actions (Sem precisar de PC potente)
1. Crie um repositório no seu GitHub (pode ser privado).
2. Suba todos os arquivos desta pasta para o repositório (`git push`).
3. O workflow `.github/workflows/build-apk.yml` iniciará automaticamente na aba **Actions**.
4. Ao concluir, baixe os APKs prontos na seção **Artifacts**:
   - `app-standard-debug.apk`: Uso convencional.
   - `app-benchmarkSpoof-debug.apk`: Identificado como LuDaShi Benchmark para clocks sustentados máximos.

### Opção B: No Android Studio
1. Abra o Android Studio e selecione `Open...` apontando para a pasta `iQOO15TTweaker`.
2. Aguarde a sincronização do Gradle (JDK 17 recomendado).
3. Selecione a variante de compilação desejada em `Build Variants` e clique em `Run` ou `Build > Build Bundle(s) / APK(s) > Build APK(s)`.

---

## 📱 Como Usar pelo Terminal Direto (Sem esperar o APK)

Se você já estiver no aparelho com o Shizuku ativado e quiser aplicar os 144Hz ou o Debloat agora mesmo:

```bash
# Dar permissão de execução
chmod +x iqoo15t-tweaker.sh

# Ativar 144Hz Nativo agora
./iqoo15t-tweaker.sh on

# Ativar tudo (144Hz + Touch Boost + Monster Mode)
./iqoo15t-tweaker.sh all-boost

# Consultar frequência física real do painel
./iqoo15t-tweaker.sh status

# Executar Debloat seguro da ROM Chinesa
./iqoo15t-tweaker.sh debloat

# Reverter para LTPO padrão (1Hz - 120Hz)
./iqoo15t-tweaker.sh off
```
