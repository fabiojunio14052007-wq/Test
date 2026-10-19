#!/bin/bash
# ==============================================================================
# iQOO 15T Tweaker & 144Hz Native Unlocker (CLI Companion via Shizuku/rish)
# Compatível com OriginOS 7 (Android 16/17)
# ==============================================================================

RISH_BIN=""
if command -v rish >/dev/null 2>&1; then
    RISH_BIN="rish -c"
elif [ -x "/data/user/0/com.agy/files/usr/bin/rish" ]; then
    RISH_BIN="/data/user/0/com.agy/files/usr/bin/rish -c"
elif [ -x "/data/user/0/com.agy/files/runtime/bin/rish" ]; then
    RISH_BIN="/data/user/0/com.agy/files/runtime/bin/rish -c"
else
    # Fallback para execução direta em shell se já estiver como UID 2000 ou root
    RISH_BIN="sh -c"
fi

run_shizuku() {
    $RISH_BIN "$1"
}

DEX_SRC="$(dirname "$0")/app/src/main/assets/RmsVote.dex"
DEX_DST="/data/local/tmp/RmsVote.dex"

ensure_dex() {
    local check
    check=$(run_shizuku "[ -f $DEX_DST ] && echo OK || echo MISSING")
    if [[ "$check" != *"OK"* ]]; then
        echo "[*] Copiando RmsVote.dex para $DEX_DST..."
        if [ -f "$DEX_SRC" ]; then
            run_shizuku "cp '$DEX_SRC' $DEX_DST && chmod 644 $DEX_DST"
        else
            run_shizuku "curl -L -s https://raw.githubusercontent.com/AndroPlus-org/vivo-HighRefresh-NoRoot/main/RmsVote.dex -o $DEX_DST && chmod 644 $DEX_DST"
        fi
    fi
}

enable_144hz() {
    echo "=========================================="
    echo " Ativando 144Hz Nativos (Sem Interpolação)"
    echo "=========================================="
    ensure_dex
    
    run_shizuku "
        settings put system gamecube_frame_interpolation_for_sr 0:-1:0:0:0
        settings put system peak_refresh_rate 144.0
        settings put system min_refresh_rate 144.0
        settings put global vivo_screen_refresh_rate_mode 144
        settings put system game_mode_max_framerate 144
        settings put system NV_FPSLIMIT 144
        if [ -f $DEX_DST ]; then
            CLASSPATH=$DEX_DST app_process / RmsVote set 144
        fi
    "
    echo "[✓] Voto 144Hz injetado no com.vivo.rms.IRM com sucesso!"
    show_status
}

disable_144hz() {
    echo "=========================================="
    echo " Restaurando LTPO Padrão (1Hz - 120Hz)"
    echo "=========================================="
    ensure_dex
    run_shizuku "
        if [ -f $DEX_DST ]; then
            CLASSPATH=$DEX_DST app_process / RmsVote release 0 900001
        fi
        settings put system min_refresh_rate 1.0
        settings put system peak_refresh_rate 120.0
        settings put global vivo_screen_refresh_rate_mode 120
        settings delete system game_mode_max_framerate
        settings delete system NV_FPSLIMIT
    "
    echo "[✓] LTPO 1–120Hz restaurado!"
    show_status
}

show_status() {
    ensure_dex
    echo "--- Status do Painel LTPO ---"
    local rate
    local reason
    rate=$(run_shizuku "CLASSPATH=$DEX_DST app_process / RmsVote get 2>/dev/null")
    reason=$(run_shizuku "CLASSPATH=$DEX_DST app_process / RmsVote reason 2>/dev/null")
    echo "Frequência Real : ${rate:-rate=desconhecido}"
    echo "Controlador Atual: ${reason:-desconhecido}"
    echo "-----------------------------"
}

enable_touch_boost() {
    echo "[*] Aplicando configurações de Touch & Multitouch Ultra Boost..."
    run_shizuku "
        settings put system game_mode_touch_boost_enabled true
        settings put system adaptive_touch_sensitivity 1
        settings put system MultitouchMinDistance 0px
        settings put system MultitouchSettleInterval 0.2ms
        settings put system QuietInterval 0.1ms
        settings put system PressureForID 0.01
        settings put system PointerVelocityControlParameters 1
    "
    echo "[✓] Touch Boost ativado com estabilização instantânea (0.2ms)!"
}

enable_monster_boost() {
    echo "[*] Aplicando perfil de CPU/GPU Monster Boost..."
    run_shizuku "
        settings put system game_mode_cpu_performance performance
        settings put system game_mode_gpu_render_mode performance
        settings put system game_processor_boost 1
        settings put system game_ub_turbo_mode 1
    "
    echo "[✓] Monster Boost ativado para perfil máximo de GPU/CPU!"
}

debloat_cn_apps() {
    echo "=========================================="
    echo " Executando Debloat Seguro da ROM Chinesa"
    echo "=========================================="
    
    # Trava de Segurança
    PROTECTED=("com.vivo.gamewatch" "com.vivo.pem" "com.vivo.sps" "com.vivo.abe" "android" "com.android.systemui" "com.bbk.launcher2")
    
    APPS=(
        "com.xtc.originwidget"
        "com.vivo.browser.novel.widget"
        "com.unionpay.tsmservice"
        "com.vivo.familycare.widget"
        "com.sina.news.vivo.widget"
        "com.baidu.carlife.vivo"
        "com.tencent.mobileqq"
        "com.kugou.android"
        "com.vivo.hiboard"
        "com.baidu.input_vivo"
        "com.vivo.browser"
        "com.vivo.magazine"
        "com.vivo.wallet"
    )

    for pkg in "${APPS[@]}"; do
        for prot in "${PROTECTED[@]}"; do
            if [ "$pkg" == "$prot" ]; then
                echo "[!] BLOQUEADO: $pkg é protegido pelo sistema!"
                continue 2
            fi
        done

        echo -n "[*] Removendo $pkg... "
        local res
        res=$(run_shizuku "pm uninstall --user 0 $pkg 2>&1")
        if [[ "$res" == *"Success"* ]]; then
            echo "Desinstalado (pm uninstall)."
        else
            # Bypass Vivo via IPC e Suspensão AppOps
            run_shizuku "
                pm disable-user --user 0 $pkg 2>/dev/null
                service call package 131 s16 $pkg i32 0 i32 0 2>/dev/null
                service call package 131 s16 $pkg i32 3 i32 0 2>/dev/null
                pm suspend --user 0 $pkg 2>/dev/null
                cmd appops set $pkg RUN_IN_BACKGROUND ignore 2>/dev/null
                cmd appops set $pkg WAKE_LOCK ignore 2>/dev/null
            "
            echo "Desativado via Bypass IPC + AppOps."
        fi
    done
    echo "[✓] Debloat concluído com sucesso!"
}

case "$1" in
    on|144)
        enable_144hz
        ;;
    off|120)
        disable_144hz
        ;;
    status)
        show_status
        ;;
    touch)
        enable_touch_boost
        ;;
    monster)
        enable_monster_boost
        ;;
    all-boost)
        enable_144hz
        enable_touch_boost
        enable_monster_boost
        ;;
    debloat)
        debloat_cn_apps
        ;;
    *)
        echo "Uso: $0 {on|off|status|touch|monster|all-boost|debloat}"
        echo "  on        - Desbloqueia 144Hz Nativos sem interpolação MEMC"
        echo "  off       - Restaura LTPO dinâmico padrão (1Hz-120Hz)"
        echo "  status    - Exibe taxa física atual e razão do bloqueio"
        echo "  touch     - Ativa Touch e Multitouch Boost de baixa latência"
        echo "  monster   - Ativa perfil máximo de CPU/GPU do Modo Monstro"
        echo "  all-boost - Ativa 144Hz + Touch + Monster Boost de uma vez"
        echo "  debloat   - Desativa bloatware chinês seguro com trava anti-bootloop"
        ;;
esac
