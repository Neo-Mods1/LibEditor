use crate::elf::{ElfData, ExtractedString, bytes_to_hex};
use serde::{Deserialize, Serialize};
use std::path::Path;

#[derive(Debug, Serialize, Deserialize)]
pub struct StringReplaceRequest {
    pub offset: u64,
    pub original_length: usize,
    pub replacement: String,
}

#[derive(Debug, Serialize)]
pub struct StringReplaceResult {
    pub success: bool,
    pub output_path: String,
    pub error: String,
}

pub struct StringReplacer {
    elf: ElfData,
}

impl StringReplacer {
    pub fn new(elf: ElfData) -> Self {
        Self { elf }
    }

    pub fn extract_strings(&self) -> Vec<ExtractedString> {
        crate::elf::extract_strings(&self.elf.bytes)
    }

    pub fn replace_string(
        &mut self,
        offset: u64,
        original_length: usize,
        replacement: &str,
    ) -> Result<(), String> {
        let replacement_bytes = replacement.as_bytes();

        if replacement_bytes.len() > original_length {
            return Err(format!(
                "Replacement ({} bytes) exceeds original allocated size ({} bytes)",
                replacement_bytes.len(),
                original_length
            ));
        }

        let mut padded = replacement_bytes.to_vec();
        padded.resize(original_length, 0);

        self.elf
            .write_bytes(offset, &padded)
            .map_err(|e| e.to_string())?;

        Ok(())
    }

    pub fn save(self, path: &Path) -> Result<(), String> {
        self.elf.save(path).map_err(|e| e.to_string())
    }

    pub fn get_bytes(&self) -> &[u8] {
        &self.elf.bytes
    }
}
