#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build
javac --release 17 -d build src/main/java/ru/balandin/minikv/*.java src/test/java/ru/balandin/minikv/*.java
if [[ "${1:-test}" == "server" ]]; then
  java -cp build ru.balandin.minikv.MiniKvServer
else
  java -cp build ru.balandin.minikv.MiniKvTest
fi
