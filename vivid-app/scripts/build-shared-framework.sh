#!/bin/bash
# ─────────────────────────────────────────────────────────────
#  build-shared-framework.sh
#  Compila el módulo :shared como framework de iOS para Xcode.
#
#  Uso:
#    ./scripts/build-shared-framework.sh [debug|release]
#
#  El framework se genera en:
#    shared/build/xcode-frameworks/Shared.xcframework
#
#  Después de compilar, abrir iosApp/iosApp.xcworkspace en Xcode.
# ─────────────────────────────────────────────────────────────

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BUILD_TYPE="${1:-debug}"

echo "═══════════════════════════════════════════════"
echo "  Vivid KMP — Build Shared Framework ($BUILD_TYPE)"
echo "═══════════════════════════════════════════════"
echo ""

cd "$PROJECT_DIR"

# Detectar arquitectura del Mac para elegir el target correcto
ARCH=$(uname -m)
if [ "$ARCH" = "arm64" ]; then
    SIMULATOR_TARGET="linkDebugFrameworkIosSimulatorArm64"
    echo "🖥  Mac Apple Silicon detectado → iosSimulatorArm64"
else
    SIMULATOR_TARGET="linkDebugFrameworkIosSimulatorX64"
    echo "🖥  Mac Intel detectado → iosSimulatorArm64"
fi

echo ""
echo "📦 Compilando framework para simulador..."
./gradlew ":shared:${SIMULATOR_TARGET}" --quiet

echo "📦 Compilando framework para dispositivo real (arm64)..."
./gradlew ":shared:linkDebugFrameworkIosArm64" --quiet

echo ""
echo "🔧 Creando XCFramework..."

# Rutas de los frameworks compilados
SIM_FW="shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework"
DEVICE_FW="shared/build/bin/iosArm64/debugFramework/Shared.framework"
OUTPUT="shared/build/xcode-frameworks"

if [ "$ARCH" != "arm64" ]; then
    SIM_FW="shared/build/bin/iosSimulatorX64/debugFramework/Shared.framework"
fi

mkdir -p "$OUTPUT"

# Limpiar XCFramework anterior
rm -rf "$OUTPUT/Shared.xcframework"

xcodebuild -create-xcframework \
    -framework "$SIM_FW" \
    -framework "$DEVICE_FW" \
    -output "$OUTPUT/Shared.xcframework"

echo ""
echo "✅ XCFramework creado en: $OUTPUT/Shared.xcframework"
echo ""
echo "Para usar en Xcode:"
echo "  1. Abrir iosApp/iosApp.xcworkspace"
echo "  2. Agregar Shared.xcframework al target iosApp"
echo "  3. En Build Phases → Embed Frameworks, marcar 'Embed & Sign'"
echo ""

# Verificar tests
echo "🧪 Ejecutando tests del módulo shared..."
./gradlew :shared:allTests --quiet 2>/dev/null && echo "✅ Tests OK" || echo "⚠️  Tests fallaron (ver: ./gradlew :shared:allTests)"

echo ""
echo "═══════════════════════════════════════════════"
echo "  Build completado 🎉"
echo "═══════════════════════════════════════════════"
