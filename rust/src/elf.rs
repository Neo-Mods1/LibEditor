use goblin::elf::Elf;
use goblin::elf::program_header::PT_LOAD;
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
}

pub struct SectionInfo {
    pub name: String,
    pub offset: u64,
    pub size: u64,
    pub section_type: String,
}

pub struct ElfData {
    pub bytes: Vec<u8>,
    pub info: ElfInfo,
    pub load_segments: Vec<LoadSegment>,
    pub sections: Vec<SectionInfo>,
    pub is_64bit: bool,
}

pub struct LoadSegment {
    pub file_offset: u64,
    pub virtual_addr: u64,
    pub file_size: u64,
    pub memory_size: u64,
}

impl ElfData {
    pub fn open(path: &Path) -> Result<Self, ElfError> {
        let bytes = fs::read(path)?;
        let (info, load_segments, sections, is_64bit) = Self::parse_info(&bytes, path)?;

        Ok(Self {
            bytes,
            info,
            load_segments,
            sections,
            is_64bit,
        })
    }

    fn parse_info(bytes: &[u8], path: &Path) -> Result<(ElfInfo, Vec<LoadSegment>, Vec<SectionInfo>, bool), ElfError> {
        match Object::parse(bytes)
            .map_err(|e| ElfError::Parse(e.to_string()))?
        {
            Object::Elf(elf) => {
                let arch = detect_architecture(&elf)?;
                let name = path
                    .file_name()
                    .map(|n| n.to_string_lossy().to_string())
                    .unwrap_or_default();

                let load_segments: Vec<LoadSegment> = elf
                    .program_headers
                    .iter()
                    .filter(|ph| ph.p_type == PT_LOAD)
                    .map(|ph| LoadSegment {
                        file_offset: ph.p_offset,
                        virtual_addr: ph.p_vaddr,
                        file_size: ph.p_filesz,
                        memory_size: ph.p_memsz,
                    })
                    .collect();

                let sections: Vec<SectionInfo> = elf
                    .section_headers
                    .iter()
                    .map(|sh| {
                        let name = elf.shdr_strtab.get_at(sh.sh_name).unwrap_or("").to_string();
                        let section_type = match sh.sh_type {
                            0 => "SHT_NULL",
                            1 => "SHT_PROGBITS",
                            2 => "SHT_SYMTAB",
                            3 => "SHT_STRTAB",
                            4 => "SHT_RELA",
                            5 => "SHT_HASH",
                            6 => "SHT_DYNAMIC",
                            7 => "SHT_NOTE",
                            8 => "SHT_NOBITS",
                            9 => "SHT_REL",
                            11 => "SHT_DYNSYM",
                            _ => "OTHER",
                        }.to_string();
                        SectionInfo {
                            name,
                            offset: sh.sh_offset,
                            size: sh.sh_size,
                            section_type,
                        }
                    })
                    .collect();

                let info = ElfInfo {
                    name,
                    architecture: arch,
                    file_size: bytes.len() as u64,
                    section_count: elf.section_headers.len(),
                    entry_point: elf.entry,
                };

                Ok((info, load_segments, sections, elf.is_64))
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

    /// Convert a file offset to its virtual address using load segments
    pub fn offset_to_vaddr(&self, file_offset: u64) -> Option<u64> {
        for seg in &self.load_segments {
            if file_offset >= seg.file_offset
                && file_offset < seg.file_offset + seg.file_size
            {
                let vaddr =
                    seg.virtual_addr + (file_offset - seg.file_offset);
                return Some(vaddr);
            }
        }
        None
    }

    /// Convert a virtual address back to file offset
    pub fn vaddr_to_offset(&self, vaddr: u64) -> Option<u64> {
        for seg in &self.load_segments {
            if vaddr >= seg.virtual_addr
                && vaddr < seg.virtual_addr + seg.file_size
            {
                let offset =
                    seg.file_offset + (vaddr - seg.virtual_addr);
                return Some(offset);
            }
        }
        None
    }

    /// Find a contiguous region of null bytes large enough to hold `size` bytes.
    /// Searches within existing file bounds (inter-section padding).
    pub fn find_free_space(&self, size: usize, alignment: usize) -> Option<u64> {
        // Search for null byte gaps within the file
        let mut run_start: Option<usize> = None;
        let mut run_len: usize = 0;

        for (i, &byte) in self.bytes.iter().enumerate() {
            if byte == 0 {
                if run_start.is_none() {
                    run_start = Some(i);
                    run_len = 0;
                }
                run_len += 1;

                if run_len >= size {
                    let start = run_start.unwrap();
                    let aligned_start = if alignment > 1 {
                        (start + alignment - 1) & !(alignment - 1)
                    } else {
                        start
                    };
                    let aligned_end = aligned_start + size;
                    // Make sure the aligned region fits within this null run
                    if aligned_end <= start + run_len {
                        return Some(aligned_start as u64);
                    }
                }
            } else {
                run_start = None;
                run_len = 0;
            }
        }

        None
    }

    /// Find all file offsets where a pointer to `target_vaddr` is stored.
    /// Scans for 4-byte little-endian pointer values.
    pub fn find_pointer_refs(&self, target_vaddr: u64) -> Vec<u64> {
        let mut refs = Vec::new();
        let needle = (target_vaddr as u32).to_le_bytes();
        let needle64 = target_vaddr.to_le_bytes();

        // Scan for 4-byte pointers (32-bit ELF)
        if !self.is_64bit {
            for i in 0..self.bytes.len().saturating_sub(3) {
                if self.bytes[i..i + 4] == needle {
                    refs.push(i as u64);
                }
            }
        } else {
            // Scan for 8-byte pointers (64-bit ELF)
            for i in 0..self.bytes.len().saturating_sub(7) {
                if self.bytes[i..i + 8] == needle64 {
                    refs.push(i as u64);
                }
            }
            // Also scan for 4-byte truncated pointers (common in 32-bit compat)
            for i in 0..self.bytes.len().saturating_sub(3) {
                if self.bytes[i..i + 4] == needle {
                    // Only add if not already found as 8-byte
                    if !refs.contains(&(i as u64)) {
                        refs.push(i as u64);
                    }
                }
            }
        }

        refs
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
    let mut code_units: Vec<u16> = Vec::new();
    let mut start_offset = 0u64;

    for (i, chunk) in bytes.windows(2).step_by(2).enumerate() {
        let val = u16::from_le_bytes([chunk[0], chunk[1]]);

        if val >= 0x20 && val < 0x7f {
            if code_units.is_empty() {
                start_offset = (i * 2) as u64;
            }
            code_units.push(val);
        } else {
            if code_units.len() >= min_length {
                let display_value: String = code_units.iter().map(|&c| c as u8 as char).collect();
                strings.push(ExtractedString {
                    offset: start_offset,
                    value: display_value,
                    encoding: "UTF16LE".to_string(),
                    length: code_units.len() * 2,
                });
            }
            code_units.clear();
        }
    }
}
