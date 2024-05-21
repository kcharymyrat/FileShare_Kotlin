package fileshare.repositories

import fileshare.models.UploadedFile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UploadedFileRepository : JpaRepository<UploadedFile, UUID> {
    fun findByHashCode(hashCode: String): Optional<UploadedFile>
}