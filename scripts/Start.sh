#!/bin/bash
# =========================================
# 앱 백그라운드 실행 스크립트 (jihun-portfolio)
# 배치 구조:
#   /home/jihun/Start.sh, killall.sh
#   /home/jihun/conf/app.conf   ← 포트·DB 등 환경 설정
#   /home/jihun/libs/jihun-portfolio-0.0.1-SNAPSHOT.jar
#   /home/jihun/logs/app.log    ← 당일 로그 (자정에 YYYY-MM-DD.log로 자동 보관)
# 사용법: ./Start.sh
# =========================================

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"

JAR_NAME="jihun-portfolio-0.0.1-SNAPSHOT.jar"
APP_JAR="$BASE_DIR/libs/$JAR_NAME"
CONF_FILE="$BASE_DIR/conf/app.conf"
LOG_DIR="$BASE_DIR/logs"

mkdir -p "$LOG_DIR"

# 1. 설정 파일 로드 (set -a: 읽은 변수를 전부 export → java가 환경변수로 받음)
if [ -f "$CONF_FILE" ]; then
  set -a
  source "$CONF_FILE"
  set +a
else
  echo "[WARN] 설정 파일이 없습니다: $CONF_FILE → 기본값 사용"
fi
export SERVER_PORT="${SERVER_PORT:-8080}"
export LOG_DIR

# 2. jar 파일 존재 확인
if [ ! -f "$APP_JAR" ]; then
  echo "[ERROR] jar 파일이 없습니다: $APP_JAR"
  exit 1
fi

# 3. 중복 실행 방지
PID=$(pgrep -f "java -jar $APP_JAR")
if [ -n "$PID" ]; then
  echo "[WARN] 이미 실행 중입니다. (PID: $PID)"
  echo "       재시작하려면 먼저 ./killall.sh 로 종료하세요."
  exit 1
fi

# 4. 백그라운드 실행
PROFILE_INFO="${SPRING_PROFILES_ACTIVE:-default(H2)}"
echo "[INFO] 앱 시작 (포트: $SERVER_PORT, 프로파일: $PROFILE_INFO)"
cd "$BASE_DIR"
nohup java -jar "$APP_JAR" > "$LOG_DIR/console.log" 2>&1 &

echo "[INFO] 시작 완료 (PID: $!)"
echo "[INFO] 실시간 로그 보기: tail -f $LOG_DIR/app.log"
