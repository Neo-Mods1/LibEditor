# LibEditor

An Android ELF shared library editor with address patching and string editing capabilities. Built with Kotlin, Jetpack Compose, and a Rust backend for high-performance binary manipulation.

## Features

### Address Patching
- Read bytes at any offset in ELF files
- Add multiple patches with offset, original bytes, and replacement bytes
- Enable/disable patches individually
- Apply all patches at once
- Hex format validation and byte alignment checks

### String Editor
- Extract ASCII, UTF-8, and UTF-16 strings from libraries
- Search and filter strings in real-time
- Replace strings with strict size validation
- Display string offset, encoding, and length

### Supported Architectures
- ARM64 (aarch64)
- ARMv7 (armeabi-v7a)
- x86
- x86_64

## Architecture

```
┌─────────────────────────────────────┐
│           Jetpack Compose UI        │
├─────────────────────────────────────┤
│            ViewModels               │
├─────────────────────────────────────┤
│        Repository Layer             │
├─────────────────────────────────────┤
│         JNI Bridge (C++)            │
├─────────────────────────────────────┤
│       Rust Backend (ELF parsing)    │
└─────────────────────────────────────┘
```

- **UI Layer**: Material 3 Compose with two-tab design
- **ViewModel**: MVVM pattern with StateFlow
- **Repository**: Abstracts data operations
- **JNI Bridge**: C++ layer connecting Kotlin to Rust
- **Rust Backend**: ELF parsing, patching, string extraction using `goblin`

## Build Instructions

### Prerequisites
- Android Studio Hedgehog or later
- JDK 17
- Rust toolchain with `cargo-ndk`
- Android NDK

### Local Build
```bash
# Install cargo-ndk
cargo install cargo-ndk

# Add Android targets
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android

# Build Rust libraries
cd rust
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86 -t x86_64 build --release

# Copy to jniLibs
mkdir -p ../app/src/main/jniLibs
cp target/aarch64-linux-android/release/libeditor.so ../app/src/main/jniLibs/arm64-v8a/
cp target/armv7-linux-androideabi/release/libeditor.so ../app/src/main/jniLibs/armeabi-v7a/
cp target/i686-linux-android/release/libeditor.so ../app/src/main/jniLibs/x86/
cp target/x86_64-linux-android/release/libeditor.so ../app/src/main/jniLibs/x86_64/

# Build APK
cd ..
./gradlew assembleRelease
```

### GitHub Actions
The project includes CI/CD workflows that automatically:
- Build Rust libraries for all architectures
- Compile the Android APK
- Upload build artifacts

## JNI Flow

1. Kotlin ViewModel calls `JniBridge` methods
2. `JniBridge` invokes native C++ functions via JNI
3. C++ forwards calls to Rust library
4. Rust performs ELF parsing/patching operations
5. Results serialized as JSON and returned through JNI

## Project Structure

```
LibEditor/
├── app/
│   ├── src/main/
│   │   ├── java/com/neomods/libeditor/
│   │   │   ├── model/          # Data classes
│   │   │   ├── repository/     # Repository pattern
│   │   │   ├── service/        # JNI bridge
│   │   │   ├── viewmodel/      # MVVM ViewModels
│   │   │   └── ui/             # Compose UI
│   │   ├── cpp/                # JNI C++ layer
│   │   └── res/                # Android resources
├── rust/
│   └── src/
│       ├── elf.rs              # ELF parsing
│       ├── patch.rs            # Patch operations
│       ├── string.rs           # String operations
│       └── lib.rs              # JNI exports
└── .github/workflows/          # CI/CD
```

## License

MIT License - see [LICENSE](LICENSE) for details.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for release history.
