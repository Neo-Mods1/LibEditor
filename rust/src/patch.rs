use crate::elf::{ElfData, ElfError, parse_hex_string};
use serde::{Deserialize, Serialize};
use std::path::Path;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PatchEntry {
    pub offset: String,
    #[serde(rename = "originalBytes")]
    pub original_bytes: String,
    #[serde(rename = "replacementBytes")]
    pub replacement_bytes: String,
    pub enabled: bool,
    pub description: String,
}

#[derive(Debug, Serialize)]
pub struct PatchResult {
    pub success: bool,
    pub output_path: String,
    pub error: String,
}

pub struct PatchApplier {
    elf: ElfData,
}

impl PatchApplier {
    pub fn new(elf: ElfData) -> Self {
        Self { elf }
    }

    pub fn validate_patch(&self, patch: &PatchEntry) -> Result<(), ElfError> {
        let offset = parse_hex_offset(&patch.offset)?;
        let original = parse_hex_string(&patch.original_bytes)?;
        let replacement = parse_hex_string(&patch.replacement_bytes)?;

        if replacement.len() != original.len() {
            return Err(ElfError::InvalidHex(format!(
                "Replacement size ({} bytes) differs from original ({} bytes)",
                replacement.len(),
                original.len()
            )));
        }

        let end = offset + original.len() as u64;
        if end > self.elf.bytes.len() as u64 {
            return Err(ElfError::OffsetOutOfBounds(end));
        }

        let actual = self.elf.read_bytes(offset, original.len())?;
        if actual != original {
            return Err(ElfError::InvalidHex(format!(
                "Bytes at offset {} don't match expected: got {}",
                patch.offset,
                crate::elf::bytes_to_hex(&actual)
            )));
        }

        Ok(())
    }

    pub fn apply_patch(&mut self, patch: &PatchEntry) -> Result<(), ElfError> {
        let offset = parse_hex_offset(&patch.offset)?;
        let replacement = parse_hex_string(&patch.replacement_bytes)?;

        self.elf.write_bytes(offset, &replacement)
    }

    pub fn apply_all_patches(&mut self, patches: &[PatchEntry]) -> Result<String, ElfError> {
        let enabled: Vec<_> = patches.iter().filter(|p| p.enabled).collect();

        for patch in &enabled {
            self.validate_patch(patch)?;
        }

        for i in 0..enabled.len() {
            for j in (i + 1)..enabled.len() {
                let off_a = parse_hex_offset(&enabled[i].offset)?;
                let len_a = parse_hex_string(&enabled[i].replacement_bytes)?.len() as u64;
                let off_b = parse_hex_offset(&enabled[j].offset)?;
                let len_b = parse_hex_string(&enabled[j].replacement_bytes)?.len() as u64;

                let a_start = off_a;
                let a_end = off_a + len_a;
                let b_start = off_b;
                let b_end = off_b + len_b;

                if a_start < b_end && b_start < a_end {
                    return Err(ElfError::InvalidHex(format!(
                        "Patches overlap: patch at {} ({}-{}) overlaps with patch at {} ({}-{})",
                        enabled[i].offset, a_start, a_end, enabled[j].offset, b_start, b_end
                    )));
                }
            }
        }

        for patch in &enabled {
            self.apply_patch(patch)?;
        }

        Ok("".to_string())
    }

    pub fn save(self, path: &Path) -> Result<(), ElfError> {
        self.elf.save(path)
    }

    pub fn read_bytes(&self, offset: u64, length: usize) -> Result<Vec<u8>, ElfError> {
        self.elf.read_bytes(offset, length)
    }

    pub fn get_bytes(&self) -> &[u8] {
        &self.elf.bytes
    }
}

pub fn parse_hex_offset(hex: &str) -> Result<u64, ElfError> {
    let cleaned = hex
        .trim()
        .trim_start_matches("0x")
        .trim_start_matches("0X");

    u64::from_str_radix(cleaned, 16)
        .map_err(|_| ElfError::InvalidHex(format!("Invalid hex offset: {}", hex)))
}
