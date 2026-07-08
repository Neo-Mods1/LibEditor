use crate::elf::{ElfData, ExtractedString};
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

#[derive(Debug, Serialize)]
pub struct RedirectInfo {
    pub original_offset: u64,
    pub new_string_offset: u64,
    pub pointers_updated: usize,
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
    ) -> Result<Option<RedirectInfo>, String> {
        if original_length == 0 {
            return Err("Original length cannot be zero".to_string());
        }

        let end = offset + original_length as u64;
        if end > self.elf.bytes.len() as u64 {
            return Err(format!(
                "Offset {} + length {} = {} exceeds file size {}",
                offset,
                original_length,
                end,
                self.elf.bytes.len()
            ));
        }

        // Validate replacement is valid UTF-8
        if !replacement.is_char_boundary(0) && !replacement.is_empty() {
            // String is valid UTF-8 if is_char_boundary works for all positions
            for (i, _) in replacement.char_indices() {
                if !replacement.is_char_boundary(i) {
                    return Err(format!(
                        "Invalid UTF-8 in replacement string at byte position {}",
                        i
                    ));
                }
            }
        }

        let replacement_bytes = replacement.as_bytes();
        let null_terminated = [replacement_bytes, &[0]].concat();

        // If replacement fits in original space, do in-place replace
        if null_terminated.len() <= original_length {
            let mut padded = null_terminated.clone();
            padded.resize(original_length, 0);

            self.elf
                .write_bytes(offset, &padded)
                .map_err(|e| e.to_string())?;

            return Ok(None); // No redirect needed
        }

        // Replacement is longer - need to redirect
        self.redirect_string(offset, original_length, &null_terminated)
    }

    fn redirect_string(
        &mut self,
        original_offset: u64,
        original_length: usize,
        new_bytes: &[u8],
    ) -> Result<Option<RedirectInfo>, String> {
        // Find free space in the ELF for the new string
        // Use 1-byte alignment since strings don't need special alignment
        let free_offset = self
            .elf
            .find_free_space(new_bytes.len(), 1)
            .ok_or_else(|| {
                format!(
                    "No free space found for redirect (need {} bytes)",
                    new_bytes.len()
                )
            })?;

        // Write the new string to the free space
        self.elf
            .write_bytes(free_offset, new_bytes)
            .map_err(|e| e.to_string())?;

        // Null out the original string
        let null_padding = vec![0u8; original_length];
        self.elf
            .write_bytes(original_offset, &null_padding)
            .map_err(|e| e.to_string())?;

        // Try to find and update pointer references
        let pointers_updated = self.update_pointer_refs(original_offset, free_offset)?;

        // If no pointers were found, try virtual address based redirect
        let pointers_updated = if pointers_updated == 0 {
            self.update_pointer_refs_vaddr(original_offset, free_offset)?
        } else {
            pointers_updated
        };

        Ok(Some(RedirectInfo {
            original_offset,
            new_string_offset: free_offset,
            pointers_updated,
        }))
    }

    /// Find pointer references using file offset as a heuristic (for packed/flat ELFs)
    fn update_pointer_refs(
        &mut self,
        original_offset: u64,
        new_offset: u64,
    ) -> Result<usize, String> {
        let mut updated = 0;

        // For 32-bit ELF, scan for 4-byte file offset references
        if !self.elf.is_64bit {
            let needle = (original_offset as u32).to_le_bytes();
            let replacement = (new_offset as u32).to_le_bytes();

            for i in 0..self.elf.bytes.len().saturating_sub(3) {
                if self.elf.bytes[i..i + 4] == needle {
                    self.elf
                        .write_bytes(i as u64, &replacement)
                        .map_err(|e| e.to_string())?;
                    updated += 1;
                }
            }
        } else {
            // For 64-bit ELF, scan for 8-byte file offset references
            let needle = original_offset.to_le_bytes();
            let replacement = new_offset.to_le_bytes();

            for i in 0..self.elf.bytes.len().saturating_sub(7) {
                if self.elf.bytes[i..i + 8] == needle {
                    self.elf
                        .write_bytes(i as u64, &replacement)
                        .map_err(|e| e.to_string())?;
                    updated += 1;
                }
            }
        }

        Ok(updated)
    }

    /// Find pointer references using virtual addresses (standard ELF approach)
    fn update_pointer_refs_vaddr(
        &mut self,
        original_offset: u64,
        new_offset: u64,
    ) -> Result<usize, String> {
        let orig_vaddr = match self.elf.offset_to_vaddr(original_offset) {
            Some(v) => v,
            None => return Ok(0),
        };

        let new_vaddr = match self.elf.offset_to_vaddr(new_offset) {
            Some(v) => v,
            None => return Ok(0),
        };

        if orig_vaddr == new_vaddr {
            return Ok(0);
        }

        let refs = self.elf.find_pointer_refs(orig_vaddr);
        let mut updated = 0;

        for &ref_offset in &refs {
            if self.elf.is_64bit {
                let replacement = new_vaddr.to_le_bytes();
                self.elf
                    .write_bytes(ref_offset, &replacement)
                    .map_err(|e| e.to_string())?;
            } else {
                let replacement = (new_vaddr as u32).to_le_bytes();
                self.elf
                    .write_bytes(ref_offset, &replacement)
                    .map_err(|e| e.to_string())?;
            }
            updated += 1;
        }

        Ok(updated)
    }

    pub fn save(self, path: &Path) -> Result<(), String> {
        self.elf.save(path).map_err(|e| e.to_string())
    }

    pub fn get_bytes(&self) -> &[u8] {
        &self.elf.bytes
    }
}
