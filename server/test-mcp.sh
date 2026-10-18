#!/bin/sh
# Startup cua server (panel: sh .agent/test-mcp.sh)
# - MCP agent chay trong vong lap rieng: tool reload_agent chi can thoat, script tu bat lai.
# - Minecraft chay trong vong lap: CHI khoi dong lai khi agent ghi .agent/.restart-flag
#   (tool schedule_restart). Bam Stop tren panel -> khong co co -> thoat binh thuong.

FLAG=.agent/.restart-flag
rm -f "$FLAG"

(
    cd .agent || exit 1
    while true; do
        AGENT_SUPERVISED=1 ./node/bin/node server.js
        echo "[startup] MCP agent thoat (code $?), bat lai sau 3s"
        sleep 3
    done
) &

while true; do
    java -Xms6G -Xmx6G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true -Dlog4j.configurationFile=log4j2.xml -Dterminal.jline=false -Dterminal.ansi=true -jar server.jar
    CODE=$?
    if [ -f "$FLAG" ]; then
        echo "[startup] Restart theo yeu cau agent: $(cat "$FLAG")"
        mv -f "$FLAG" "$FLAG.last"
        sleep 5
        continue
    fi
    echo "[startup] Minecraft thoat (code $CODE)"
    exit $CODE
done
