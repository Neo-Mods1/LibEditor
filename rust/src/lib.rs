pub mod elf;
pub mod patch;
pub mod string;

use elf::ElfData;
use jni::JNIEnv;
use jni::objects::{JClass, JObjectArray, JString, JValue};
use jni::sys::{jint, jlong, jstring};
use patch::{PatchApplier, PatchEntry};
use std::path::Path;
use string::StringReplacer;

fn to_jstring(env: &mut JNIEnv, s: &str) -> jstring {
    env.new_string(s)
        .expect("Failed to create Java string")
        .into_raw()
}

#[no_mangle]
pub extern "C" fn Java_com_neomods_libeditor_service_JniBridge_nativeGetLibraryInfo(
    mut env: JNIEnv,
    _class: JClass,
    file_path: JString,
) -> jstring {
    let path: String = env
        .get_string(&file_path)
        .expect("Failed to get path")
        .into();

    match ElfData::open(Path::new(&path)) {
        Ok(elf) => {
            let info = &elf.info;
            let json = serde_json::json!({
                "name": info.name,
                "architecture": info.architecture,
                "fileSize": info.file_size,
                "sectionCount": info.section_count,
                "stringCount": crate::elf::extract_strings(&elf.bytes).len(),
                "entryPoint": info.entry_point
            });
            to_jstring(&mut env, &json.to_string())
        }
        Err(e) => {
            let json = serde_json::json!({ "error": e.to_string() });
            to_jstring(&mut env, &json.to_string())
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_com_neomods_libeditor_service_JniBridge_nativeReadOffset(
    mut env: JNIEnv,
    _class: JClass,
    file_path: JString,
    offset: jlong,
    length: jint,
) -> jstring {
    let path: String = env
        .get_string(&file_path)
        .expect("Failed to get path")
        .into();

    match ElfData::open(Path::new(&path)) {
        Ok(elf) => match elf.read_bytes(offset as u64, length as usize) {
            Ok(bytes) => {
                let hex = bytes_to_hex(&bytes);
                let byte_vec: Vec<u8> = bytes;
                let json = serde_json::json!({
                    "success": true,
                    "hex": hex,
                    "bytes": byte_vec
                });
                to_jstring(&mut env, &json.to_string())
            }
            Err(e) => {
                let json = serde_json::json!({ "success": false, "error": e.to_string() });
                to_jstring(&mut env, &json.to_string())
            }
        },
        Err(e) => {
            let json = serde_json::json!({ "success": false, "error": e.to_string() });
            to_jstring(&mut env, &json.to_string())
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_com_neomods_libeditor_service_JniBridge_nativeApplyPatches(
    mut env: JNIEnv,
    _class: JClass,
    file_path: JString,
    patches_json: JString,
    output_path: JString,
) -> jstring {
    let path: String = env
        .get_string(&file_path)
        .expect("Failed to get path")
        .into();

    let patches_str: String = env
        .get_string(&patches_json)
        .expect("Failed to get patches")
        .into();

    let out: String = env
        .get_string(&output_path)
        .expect("Failed to get output path")
        .into();

    match ElfData::open(Path::new(&path)) {
        Ok(elf) => {
            let patches: Vec<PatchEntry> = match serde_json::from_str(&patches_str) {
                Ok(p) => p,
                Err(e) => {
                    let json = serde_json::json!({ "success": false, "error": e.to_string() });
                    return to_jstring(&mut env, &json.to_string());
                }
            };

            let mut applier = PatchApplier::new(elf);

            match applier.apply_all_patches(&patches) {
                Ok(_) => match applier.save(Path::new(&out)) {
                    Ok(_) => {
                        let json = serde_json::json!({ "success": true, "outputPath": out });
                        to_jstring(&mut env, &json.to_string())
                    }
                    Err(e) => {
                        let json = serde_json::json!({ "success": false, "error": e.to_string() });
                        to_jstring(&mut env, &json.to_string())
                    }
                },
                Err(e) => {
                    let json = serde_json::json!({ "success": false, "error": e.to_string() });
                    to_jstring(&mut env, &json.to_string())
                }
            }
        }
        Err(e) => {
            let json = serde_json::json!({ "success": false, "error": e.to_string() });
            to_jstring(&mut env, &json.to_string())
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_com_neomods_libeditor_service_JniBridge_nativeExtractStrings(
    mut env: JNIEnv,
    _class: JClass,
    file_path: JString,
) -> jstring {
    let path: String = env
        .get_string(&file_path)
        .expect("Failed to get path")
        .into();

    match ElfData::open(Path::new(&path)) {
        Ok(elf) => {
            let strings = crate::elf::extract_strings(&elf.bytes);
            let json_strings: Vec<serde_json::Value> = strings
                .iter()
                .map(|s| {
                    serde_json::json!({
                        "offset": s.offset,
                        "value": s.value,
                        "encoding": s.encoding,
                        "length": s.length
                    })
                })
                .collect();

            let json = serde_json::to_string(&json_strings).unwrap_or_else(|_| "[]".to_string());
            to_jstring(&mut env, &json)
        }
        Err(e) => {
            let json = serde_json::json!({ "error": e.to_string() });
            to_jstring(&mut env, &json.to_string())
        }
    }
}

#[no_mangle]
pub extern "C" fn Java_com_neomods_libeditor_service_JniBridge_nativeReplaceString(
    mut env: JNIEnv,
    _class: JClass,
    file_path: JString,
    offset: jlong,
    original_length: jint,
    replacement: JString,
    output_path: JString,
) -> jstring {
    let path: String = env
        .get_string(&file_path)
        .expect("Failed to get path")
        .into();

    let repl: String = env
        .get_string(&replacement)
        .expect("Failed to get replacement")
        .into();

    let out: String = env
        .get_string(&output_path)
        .expect("Failed to get output path")
        .into();

    match ElfData::open(Path::new(&path)) {
        Ok(elf) => {
            let mut replacer = StringReplacer::new(elf);

            match replacer.replace_string(offset as u64, original_length as usize, &repl) {
                Ok(_) => match replacer.save(Path::new(&out)) {
                    Ok(_) => {
                        let json = serde_json::json!({ "success": true, "outputPath": out });
                        to_jstring(&mut env, &json.to_string())
                    }
                    Err(e) => {
                        let json = serde_json::json!({ "success": false, "error": e });
                        to_jstring(&mut env, &json.to_string())
                    }
                },
                Err(e) => {
                    let json = serde_json::json!({ "success": false, "error": e });
                    to_jstring(&mut env, &json.to_string())
                }
            }
        }
        Err(e) => {
            let json = serde_json::json!({ "success": false, "error": e.to_string() });
            to_jstring(&mut env, &json.to_string())
        }
    }
}
