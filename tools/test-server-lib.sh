#!/usr/bin/env bash
# Fonctions communes aux serveurs jetables des tests ; à sourcer depuis les scripts.
set -euo pipefail

prepare_test_workdir() {
  if [ -z "${1:-}" ] || [ ! -f "$1" ]; then
    echo "ÉCHEC : fournir un JAR existant en premier argument." >&2
    return 1
  fi
  # Valeur renvoyée au script appelant après validation.
  # shellcheck disable=SC2034
  JAR="$(realpath "$1")"
  WORK="${2:-$(mktemp -d)}"
  mkdir -p "$WORK"
  WORK="$(realpath "$WORK")"
  local marker="$WORK/.terracraft-test-workdir"
  if [ -n "$(find "$WORK" -mindepth 1 -maxdepth 1 -print -quit)" ]; then
    if [ ! -f "$marker" ] || [ "$(cat "$marker")" != "TerraCraft tests v1" ]; then
      echo "ÉCHEC : dossier non vide et non réservé aux tests : $WORK" >&2
      return 1
    fi
  fi
  echo "TerraCraft tests v1" > "$marker"
}

TEST_SERVER_PID=""
TEST_INPUT=""

start_test_server() {
  local heap="$1" log="$2"
  rm -f .terracraft-console
  mkfifo .terracraft-console
  # Ouverture en lecture/écriture : la FIFO reste ouverte entre deux commandes.
  exec {TEST_INPUT}<> .terracraft-console
  java -Xms512M "-Xmx$heap" -jar server.jar nogui < .terracraft-console > "$log" 2>&1 &
  TEST_SERVER_PID=$!
  trap cleanup_test_server EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM
}

stop_test_server() {
  local timeout="${1:-90}"
  echo stop >&"$TEST_INPUT"
  for _ in $(seq "$timeout"); do
    if ! kill -0 "$TEST_SERVER_PID" 2>/dev/null; then
      local status=0
      wait "$TEST_SERVER_PID" || status=$?
      TEST_SERVER_PID=""
      if [ "$status" -ne 0 ]; then
        echo "ÉCHEC : le serveur s'est arrêté avec le code $status." >&2
        return 1
      fi
      return 0
    fi
    sleep 1
  done
  echo "ÉCHEC : le serveur ne s'est pas arrêté après $timeout secondes." >&2
  return 1
}

cleanup_test_server() {
  if [ -n "$TEST_SERVER_PID" ]; then
    kill "$TEST_SERVER_PID" 2>/dev/null || true
    for _ in $(seq 5); do
      kill -0 "$TEST_SERVER_PID" 2>/dev/null || break
      sleep 1
    done
    kill -KILL "$TEST_SERVER_PID" 2>/dev/null || true
    wait "$TEST_SERVER_PID" 2>/dev/null || true
  fi
  if [ -n "$TEST_INPUT" ]; then
    exec {TEST_INPUT}>&-
  fi
  rm -f .terracraft-console
}
