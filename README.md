# All18 Android App

[![Download Android APK](https://img.shields.io/badge/Download-all18.apk%20(v1.2.0)-brightgreen?style=for-the-badge&logo=android)](https://github.com/anhot11/ALL18/releases/download/v1.2.0/all18.apk)
[![Latest Release](https://img.shields.io/github/v/release/anhot11/ALL18?style=for-the-badge)](https://github.com/anhot11/ALL18/releases/latest)
[![Build Status](https://img.shields.io/badge/Build-GitHub%20Actions-blue?style=for-the-badge&logo=githubactions)](.github/workflows/build-apk.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-purple?style=for-the-badge&logo=kotlin)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-4285F4?style=for-the-badge&logo=android)](https://developer.android.com/jetpack/compose)

Aplicación nativa de agregación multimedia para Android de alto rendimiento desarrollada en Kotlin con Jetpack Compose y reproducción silenciosa garantizada (0 dB).

### 📥 Descarga Directa
👉 **[Descargar all18.apk (v1.2.0)](https://github.com/anhot11/ALL18/releases/download/v1.2.0/all18.apk)**

---

### 🌟 Modos Principales
- **AllTube**: 100% UI Pornhub nativa con catálogo completo de sitios gratuitos, cuadrícula responsive, filtros de duración y miniaturas optimizadas.
- **TikTok**: 100% UI TikTok vertical full-screen con modo foto, scrubber, audio disco y gestos táctiles.
- **𝕏 Feed**: 100% UI Twitter / 𝕏 con pestañas "Para ti" / "Siguiendo" y reproductor integrado.
- **Silencio Total (0 dB)**: Reproducción segura garantizada con `SilentPlayerManager` (volumen bloqueado en 0.0).
- **Anti-Censura DNS**: Resolución DNS sobre HTTPS (`AppDns` con Google y Cloudflare DoH) para evasión de sinkholes ISP.

---

### 🏛️ Arquitectura Nativa
```
app/src/main/
├── AndroidManifest.xml
├── assets/
│   └── directory.json                # Catálogo curado de 98 categorías y 2,284 sitios
├── java/com/all18/nativeapp/
│   ├── MainActivity.kt               # Entrypoint y navegación Compose
│   ├── core/
│   │   ├── extractor/                # Extractores de tubes, gifs e imágenes
│   │   ├── model/                    # Modelos de datos
│   │   ├── network/                  # AppDns y AppNetworkClient (DoH + Anti-Hotlink)
│   │   ├── player/                   # SilentPlayerManager (Media3 ExoPlayer 0 dB)
│   │   └── repository/               # Repositorios de feed y directorios
│   └── ui/
│       ├── alltube/                  # AllTube (Pornhub UI)
│       ├── tiktok/                   # TikTok UI vertical
│       ├── xfeed/                    # 𝕏 Feed (Twitter UI)
│       ├── watch/                    # Reproductor nativo
│       ├── directory/                # Directorio ThePornDude
│       └── theme/                    # Tema AMOLED oscuro
└── res/                              # Iconos vectoriales y recursos mipmap
```

---

### ⚙️ Compilación con Gradle
```bash
# Compilar APK de desarrollo
./gradlew assembleDebug

# El archivo APK se genera en:
app/build/outputs/apk/debug/app-debug.apk
```
