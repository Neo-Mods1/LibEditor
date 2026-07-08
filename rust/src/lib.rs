pub mod elf;
pub mod patch;
pub mod string;

use elf::ElfData;
use elf::bytes_to_hex;
use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jint, jlong, jstring};
use patch::{PatchApplier, PatchEntry};
use std::path::Path;
use string::StringReplacer;

fn to_jstring(env: &mut JNIEnv, s: &str) -> jstring {
    // Ensure the string is valid UTF-8 before sending to Java
    let safe_string = if s.is_empty() {
        "".to_string()
    } else {
        // Replace any invalid UTF-8 sequences with replacement character
        s.chars().map(|c| c).collect::<String>()
    };
    env.new_string(&safe_string)
        .unwrap_or_else(|_| {
            // Fallback: create a simple error JSON if string creation fails
            env.new_string(r#"{"success":false,"error":"Internal encoding error"}"#)
                .expect("Failed to create fallback string")
        })
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
    let path: String = match env.get_string(&file_path) {
        Ok(s) => s.into(),
        Err(_) => {
            return to_jstring(&mut env, r#"{"success":false,"error":"Invalid file path"}"#);
        }
    };

    let repl: String = match env.get_string(&replacement) {
        Ok(s) => s.into(),
        Err(_) => {
            return to_jstring(&mut env, r#"{"success":false,"error":"Invalid replacement string - contains invalid characters"}"#);
        }
    };

    let out: String = match env.get_string(&output_path) {
        Ok(s) => s.into(),
        Err(_) => {
            return to_jstring(&mut env, r#"{"success":false,"error":"Invalid output path"}"#);
        }
    };

    match ElfData::open(Path::new(&path)) {
        Ok(elf) => {
            let mut replacer = StringReplacer::new(elf);

            match replacer.replace_string(offset as u64, original_length as usize, &repl) {
                Ok(redirect_info) => match replacer.save(Path::new(&out)) {
                    Ok(_) => {
                        let mut json = serde_json::json!({ "success": true, "outputPath": out });
                        if let Some(info) = redirect_info {
                            json["redirected"] = serde_json::json!(true);
                            json["originalOffset"] = serde_json::json!(info.original_offset);
                            json["newStringOffset"] = serde_json::json!(info.new_string_offset);
                            json["pointersUpdated"] = serde_json::json!(info.pointers_updated);
                        } else {
                            json["redirected"] = serde_json::json!(false);
                        }
                        to_jstring(&mut env, &json.to_string())
                    }
                    Err(e) => {
                        let json = serde_json::json!({ "success": false, "error": format!("Save failed: {}", e) });
                        to_jstring(&mut env, &json.to_string())
                    }
                },
                Err(e) => {
                    let json = serde_json::json!({ "success": false, "error": format!("String edit failed: {}", e) });
                    to_jstring(&mut env, &json.to_string())
                }
            }
        }
        Err(e) => {
            let json = serde_json::json!({ "success": false, "error": format!("Failed to open library: {}", e) });
            to_jstring(&mut env, &json.to_string())
        }
    }
}
