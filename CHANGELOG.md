# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.0.0] - 2026-07-08

### Added
- Hex Viewer tab for viewing raw binary data
- Import/Export patches as JSON files
- String replacement validation with length warnings
- Improved error messages with detailed context

### Fixed
- Invalid UTF-8 error when replacing strings (error code 115)
- Better error handling for JNI string conversion
- Graceful fallback for invalid replacement strings

### Changed
- Updated to version 3.0.0
- Improved string editor with redirect information display

## [0.1.0] - 2026-06-11

### Added
- Initial project setup
- ELF parsing with architecture detection
- Address patching with multi-patch support
- String extraction (ASCII, UTF-8, UTF-16)
- String replacement with strict size validation
- Jetpack Compose UI with Material 3
- Two-tab design (Address Patching, String Editor)
- Rust backend for binary operations
- JNI bridge between Kotlin and Rust
- GitHub Actions CI/CD workflows
- Support for ARM64, ARMv7, x86, x86_64
