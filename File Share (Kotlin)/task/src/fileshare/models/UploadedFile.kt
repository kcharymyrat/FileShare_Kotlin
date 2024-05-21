package fileshare.models

import jakarta.persistence.*
import java.util.UUID

@Entity
data class UploadedFile(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: UUID? = null,

    @Column(name = "file_name")
    var fileName: String? = null,

    @Column(name = "file_type")
    var fileType: String? = null
)