# NeoLibEditor

A powerful ELF shared library editor for Android. Patch memory addresses and replace strings in `.so` files directly on your device.

## Features

### Address Patching
- Read raw bytes at any offset in an ELF binary
- Create multiple patches with offset, original bytes, and replacement bytes
- Enable or disable individual patches before saving
- Automatic 8-byte reads with confirmation for larger patches
- Hex validation and byte-size mismatch detection
- Live preview of original vs replacement bytes

### String Editor
- Extract ASCII, UTF-8, and UTF-16 strings from any loaded library
- Search and filter strings in real time
- Queue multiple string replacements with size validation
- Shows offset, encoding type, and byte length for each string

### Library Info
- View architecture (ARM64, ARMv7, x86, x86_64)
- File size, section count, string count, and entry point
- One-tap access from the editor toolbar

### General
- Backup and revert — always safe to experiment
- Patches and string edits are applied together in one pass
- Material 3 design with dynamic color support
- Multilingual interface (English, Arabic, Chinese, French, German, Japanese, Portuguese, Russian, Spanish)
- Dark and light theme support
- Custom output directory for patched files

## Supported Architectures

| Architecture | ABI |
|---|---|
| ARM 64-bit | `arm64-v8a` |
| ARM 32-bit | `armeabi-v7a` |
| x86 64-bit | `x86_64` |
| x86 32-bit | `x86` |

## How to Use

1. Open the app and tap **Pick .so File**
2. Select an ELF shared library from your file manager
3. Use the **Address Patching** tab to read and patch specific memory offsets
4. Use the **String Editor** tab to find and replace strings
5. Check your changes in the **Mods** tab
6. Tap the save icon to apply all modifications
7. Patched file is saved to your configured output directory

## Download

Grab the latest APK from [Releases](https://github.com/Neo-Mods1/LibEditor/releases).

## Building

### Requirements
- Android Studio Hedgehog or later
- JDK 17
- Rust toolchain with `cargo-ndk`
- Android NDK

### Build Steps
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
cp target/aarch64-linux-android/release/libNeoLibEditor.so ../app/src/main/jniLibs/arm64-v8a/
cp target/armv7-linux-androideabi/release/libNeoLibEditor.so ../app/src/main/jniLibs/armeabi-v7a/
cp target/i686-linux-android/release/libNeoLibEditor.so ../app/src/main/jniLibs/x86/
cp target/x86_64-linux-android/release/libNeoLibEditor.so ../app/src/main/jniLibs/x86_64/

# Build APK
cd ..
./gradlew assembleRelease
```

The project also includes a GitHub Actions workflow that builds everything automatically on push.

## License

MIT License — see [LICENSE](LICENSE) for details.
