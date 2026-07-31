#!/bin/bash
# =========================================
# 앱 종료 스크립트 (jihun-portfolio)
# 사용법: ./killall.sh
# =========================================

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_NAME="jihun-portfolio-0.0.1-SNAPSHOT.jar"
APP_JAR="$BASE_DIR/libs/$JAR_NAME"

PID=$(pgrep -f "java -jar $APP_JAR")
if [ -z "$PID" ]; then
  echo "[INFO] 실행 중인 앱이 없습니다."
  exit 0
fi

# 정상 종료 요청 (SIGTERM) → Spring이 @PreDestroy로 워커를 정리하며 종료 (Graceful Shutdown)
echo "[INFO] 종료 요청 전송 (PID: $PID)"
kill $PID

for i in $(seq 1 10); do
  if ! kill -0 $PID 2>/dev/null; then
    echo "[INFO] 정상 종료 완료"
    exit 0
  fi
  sleep 1
done

echo "[WARN] 응답이 없어 강제 종료합니다"
kill -9 $PID
echo "[INFO] 강제 종료 완료"
