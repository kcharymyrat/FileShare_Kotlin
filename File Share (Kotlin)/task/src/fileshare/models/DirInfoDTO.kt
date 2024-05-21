package fileshare.models

import com.fasterxml.jackson.annotation.JsonProperty

data class DirInfoDTO(
    @JsonProperty("total_files")
    val totalFiles: Int,
    @JsonProperty("total_bytes")
    val totalBytes: Long
)
