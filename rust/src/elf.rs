use goblin::elf::Elf;
use goblin::Object;
use std::fs;
use std::path::Path;
use thiserror::Error;

#[derive(Error, Debug)]
pub enum ElfError {
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),
    #[error("Parse error: {0}")]
    Parse(String),
    #[error("Invalid architecture: {0}")]
    InvalidArchitecture(String),
    #[error("Offset out of bounds: {0}")]
    OffsetOutOfBounds(u64),
    #[error("Invalid hex input: {0}")]
    InvalidHex(String),
}

pub struct ElfInfo {
    pub name: String,
    pub architecture: String,
    pub file_size: u64,
    pub section_count: usize,
    pub entry_point: u64,
    pub is_64bit: bool,
}

pub struct ElfData {
    pub bytes: Vec<u8>,
    pub info: ElfInfo,
}

impl ElfData {
    pub fn open(path: &Path) -> Result<Self, ElfError> {
        let bytes = fs::read(path)?;
        let info = Self::parse_info(&bytes, path)?;

        Ok(Self { bytes, info })
    }

    fn parse_info(bytes: &[u8], path: &Path) -> Result<ElfInfo, ElfError> {
        match Object::parse(bytes)
            .map_err(|e| ElfError::Parse(e.to_string()))?
        {
            Object::Elf(elf) => {
                let arch = detect_architecture(&elf)?;
                let name = path
                    .file_name()
                    .map(|n| n.to_string_lossy().to_string())
                    .unwrap_or_default();

                Ok(ElfInfo {
                    name,
                    architecture: arch,
                    file_size: bytes.len() as u64,
                    section_count: elf.section_headers.len(),
                    entry_point: elf.entry,
                    is_64bit: elf.is_64,
                })
            }
            _ => Err(ElfError::Parse("Not an ELF file".to_string())),
        }
    }

    pub fn read_bytes(&self, offset: u64, length: usize) -> Result<Vec<u8>, ElfError> {
        let end = offset + length as u64;
        if end > self.bytes.len() as u64 {
            return Err(ElfError::OffsetOutOfBounds(end));
        }

        Ok(self.bytes[offset as usize..end as usize].to_vec())
    }

    pub fn write_bytes(&mut self, offset: u64, data: &[u8]) -> Result<(), ElfError> {
        let end = offset + data.len() as u64;
        if end > self.bytes.len() as u64 {
            return Err(ElfError::OffsetOutOfBounds(end));
        }

        let start = offset as usize;
        self.bytes[start..start + data.len()].copy_from_slice(data);
        Ok(())
    }

    pub fn save(&self, path: &Path) -> Result<(), ElfError> {
        fs::write(path, &self.bytes)?;
        Ok(())
    }
}

fn detect_architecture(elf: &Elf) -> Result<String, ElfError> {
    let arch = match (elf.header.e_machine, elf.is_64) {
        (goblin::elf::header::EM_AARCH64, true) => "arm64",
        (goblin::elf::header::EM_ARM, false) => "armv7",
        (goblin::elf::header::EM_386, false) => "x86",
        (goblin::elf::header::EM_X86_64, true) => "x86_64",
        _ => "unknown",
    };

    Ok(arch.to_string())
}

pub fn parse_hex_string(hex: &str) -> Result<Vec<u8>, ElfError> {
    let cleaned: String = hex
        .chars()
        .filter(|c| !c.is_whitespace() && *c != ' ')
        .collect();

    if cleaned.is_empty() {
        return Err(ElfError::InvalidHex("Empty hex string".to_string()));
    }

    if cleaned.len() % 2 != 0 {
        return Err(ElfError::InvalidHex(
            "Hex string must have even length".to_string(),
        ));
    }

    if !cleaned.chars().all(|c| c.is_ascii_hexdigit()) {
        return Err(ElfError::InvalidHex("Invalid hex characters".to_string()));
    }

    (0..cleaned.len())
        .step_by(2)
        .map(|i| {
            u8::from_str_radix(&cleaned[i..i + 2], 16)
                .map_err(|_| ElfError::InvalidHex(format!("Invalid byte at position {}", i)))
        })
        .collect()
}

pub fn bytes_to_hex(bytes: &[u8]) -> String {
    bytes
        .iter()
        .map(|b| format!("{:02X}", b))
        .collect::<Vec<_>>()
        .join(" ")
}

pub fn extract_strings(bytes: &[u8]) -> Vec<ExtractedString> {
    let mut strings = Vec::new();

    extract_ascii_strings(bytes, &mut strings);
    extract_utf16_strings(bytes, &mut strings);

    strings.sort_by_key(|s| s.offset);
    strings.dedup_by_key(|s| s.offset);

    strings
}

#[derive(Debug, Clone)]
pub struct ExtractedString {
    pub offset: u64,
    pub value: String,
    pub encoding: String,
    pub length: usize,
}

fn extract_ascii_strings(bytes: &[u8], strings: &mut Vec<ExtractedString>) {
    let min_length = 4;
    let mut current = Vec::new();
    let mut start_offset = 0u64;

    for (i, &byte) in bytes.iter().enumerate() {
        if byte >= 0x20 && byte < 0x7f || byte == b'\n' || byte == b'\r' || byte == b'\t' {
            if current.is_empty() {
                start_offset = i as u64;
            }
            current.push(byte);
        } else {
            if current.len() >= min_length {
                if let Ok(s) = String::from_utf8(current.clone()) {
                    strings.push(ExtractedString {
                        offset: start_offset,
                        value: s,
                        encoding: "UTF8".to_string(),
                        length: current.len(),
                    });
                }
            }
            current.clear();
        }
    }

    if current.len() >= min_length {
        if let Ok(s) = String::from_utf8(current.clone()) {
            strings.push(ExtractedString {
                offset: start_offset,
                value: s,
                encoding: "UTF8".to_string(),
                length: current.len(),
            });
        }
    }
}

fn extract_utf16_strings(bytes: &[u8], strings: &mut Vec<ExtractedString>) {
    let min_length = 4;
    let mut current = Vec::new();
    let mut start_offset = 0u64;

    for (i, chunk) in bytes.windows(2).step_by(2).enumerate() {
        let val = u16::from_le_bytes([chunk[0], chunk[1]]);

        if val >= 0x20 && val < 0x7f {
            if current.is_empty() {
                start_offset = (i * 2) as u64;
            }
            current.push(val as u8);
        } else {
            if current.len() >= min_length {
                if let Ok(s) = String::from_utf8(current.clone()) {
                    strings.push(ExtractedString {
                        offset: start_offset,
                        value: s,
                        encoding: "UTF16LE".to_string(),
                        length: current.len() * 2,
                    });
                }
            }
            current.clear();
        }
    }
}
