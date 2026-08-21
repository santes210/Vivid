// swift-tools-version: 5.9
// Package.swift para el framework compartido (KMP → iOS)
//
// Este archivo define cómo Swift consume el framework Kotlin Multiplatform.
// En un flujo de trabajo normal:
//   1. Gradle compila el módulo :shared como framework iOS (Shared.framework)
//   2. Xcode lo enlaza vía "Embed Frameworks" o SPM
//   3. Swift importa `Shared` para acceder a modelos y repositorios
//
// Para desarrollo local, se puede usar el framework generado por Gradle
// directamente en Xcode (File → Add Package Dependencies → local path).

import PackageDescription

let package = Package(
    name: "VividShared",
    platforms: [
        .iOS(.v16)
    ],
    products: [
        .library(
            name: "VividShared",
            targets: ["VividShared"]
        )
    ],
    targets: [
        // El framework Kotlin se importa como binaryTarget.
        // Generarlo con: cd vivid-app && ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
        .binaryTarget(
            name: "Shared",
            path: "../shared/build/xcode-frameworks/Shared.xcframework"
        ),
        .target(
            name: "VividShared",
            dependencies: ["Shared"]
        )
    ]
)
