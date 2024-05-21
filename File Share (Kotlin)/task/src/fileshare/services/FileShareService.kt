package fileshare.services

import fileshare.models.DirInfoDTO
import fileshare.models.UploadedFile
import fileshare.repositories.UploadedFileRepository
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CharsetDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.*


@Service
class FileShareService @Autowired constructor(
    private val uploadedFileRepository: UploadedFileRepository
) {

    private var totalAllowedSpace: Long = 200_000  // 200KB
    private val maxFileSize: Long = 50_000 // 50KB
    private val allowedExtensions = listOf("text/plain", "image/jpeg", "image/png")

    @Value("\${uploads.dir}")
    private lateinit var uploadsDirPath: String

    @Value("\${server.port}")
    private lateinit var serverPort: String

    @PostConstruct
    fun createAndGetUploadsDir(){
        val uploadsDir: Path = Path(uploadsDirPath)
        try {
            if (!uploadsDir.toFile().exists()){
                uploadsDir.toFile().mkdirs()
            }
        } catch (e: Exception) {
            println("Failed to create upload directory")
        }
    }

    fun getUploadsDirPath(): String {
        return uploadsDirPath
    }

    fun saveToDb(uploadedFile: UploadedFile): UploadedFile {
        return uploadedFileRepository.save(uploadedFile)
    }

    fun findById(id: UUID): UploadedFile? {
        return uploadedFileRepository.findById(id).orElse(null)
    }

    fun getRemainingSpace(): Long {
        val uploadsDir: Path = Path(uploadsDirPath)
        val totalBytes: Long = uploadsDir.toFile().listFiles()?.sumOf { it.length() } ?: 0
        return totalAllowedSpace - totalBytes
    }

    fun getUploadsDirInfo(): ResponseEntity<Any> {
        val uploadsDir: Path = Path(uploadsDirPath)
        val totalFiles = uploadsDir.toFile().listFiles()?.size ?: 0
        val totalBytes = uploadsDir.toFile().listFiles()?.sumOf { it.length() } ?: 0
        return ResponseEntity.ok(DirInfoDTO(totalFiles = totalFiles, totalBytes = totalBytes))
    }

    fun isPng(fileBytes: ByteArray): Boolean {
        val pngSignature = byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
        )
        return fileBytes.startsWith(pngSignature)
    }

    fun isJpg(fileBytes: ByteArray): Boolean {
        val jpgStart = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        val jpgEnd = byteArrayOf(0xFF.toByte(), 0xD9.toByte())
        return fileBytes.startsWith(jpgStart) && fileBytes.endsWith(jpgEnd)
    }

    fun isUTF8(fileBytes: ByteArray): Boolean {
        try {
            val utf8Decoder: CharsetDecoder = StandardCharsets.UTF_8.newDecoder()
            utf8Decoder.reset()
            utf8Decoder.decode(ByteBuffer.wrap(fileBytes))
            return true
        } catch (e: CharacterCodingException) {
            return false
        }
    }


    fun isFileTypeValid(file: MultipartFile): Boolean {
        // First check extensions
        val fileType = file.contentType ?: return false
        if (fileType !in allowedExtensions) {
            return false
        }

        // Check the bytes not
        val fileBytes = file.bytes
        return when (fileType) {
            "image/png" -> isPng(fileBytes)
            "image/jpeg" -> isJpg(fileBytes)
            "text/plain" -> isUTF8(fileBytes)
            else -> false
        }
    }

    fun saveFileToUploadsDir(file: MultipartFile): ResponseEntity<Any> {
        // Check if file is empty
        if (file.isEmpty) {
            return ResponseEntity.badRequest().body("File is empty")
        }

        // Total Space left
        val spaceLeft = getRemainingSpace()

        // Get file size
        val fileSize = file.size

        // Compare if enough space is left to store
        if (fileSize > spaceLeft) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build()
        }

        // Compare if file size is more than allowed max file size
        if (fileSize > maxFileSize) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build()
        }
        println("fileSize = $fileSize, spaceLeft = $spaceLeft")

        // Compare if file type is valid
        if (!isFileTypeValid(file)) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build()
        }

        // Get file name and its content type
        val fileName = file.originalFilename ?: return ResponseEntity.badRequest().body("File name is empty")
        val fileType = file.contentType ?: return ResponseEntity.badRequest().body("File content type is empty")

        println("fileName = $fileName, fileType = $fileType")

        // Save the file info into db
        val toBeUploadedFile = UploadedFile(fileName = fileName, fileType = fileType)
        val uploadedFileEntity = uploadedFileRepository.save(toBeUploadedFile)

        // New name of the file will be uuid (id of the saved UploadedFile)
        val encodedFileName = uploadedFileEntity.id.toString()

        val filePath: Path = Path.of(uploadsDirPath, encodedFileName)

        // Save file with uuid encoded file name
        try {
            file.transferTo(filePath)
            val location = "http://localhost:$serverPort/api/v1/download/$encodedFileName"
            println("File: $encodedFileName saved at $location")
            // Reduce space left
            return ResponseEntity.created(URI.create(location)).body("File: $fileName saved")
        } catch (e: Exception) {
            e.printStackTrace()
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to save file")
        }
    }

    fun downloadFromUploadsDir(fileName: String): ResponseEntity<Any> {
        val uploadsDirPath = getUploadsDirPath()

        // get
        val optionalUploadedFile = uploadedFileRepository.findById(UUID.fromString(fileName))
        if (optionalUploadedFile.isEmpty) {
            return ResponseEntity.notFound().build()
        }
        val uploadedFile = optionalUploadedFile.get()
        val uuidOfUploadedFile = uploadedFile.id.toString()
        val nameOfUploadedFile = uploadedFile.fileName
        val typeOfUploadedFile = uploadedFile.fileType

        val filePath = Path.of(uploadsDirPath, uuidOfUploadedFile)

        if (!filePath.toFile().exists()) {
            return ResponseEntity.notFound().build()
        }

        return try {
            val fileBytes = Files.readAllBytes(filePath)

            // Set the response headers
            val headers = HttpHeaders()
            headers.contentType = typeOfUploadedFile?.let { MediaType.parseMediaType(it) }
            headers.contentDisposition = ContentDisposition.builder("attachment")
                .filename(nameOfUploadedFile!!)
                .build()

            return ResponseEntity.ok().headers(headers).body(fileBytes)

        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null)
        }
    }


    fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (this.size < prefix.size) return false
        return this.take(prefix.size).toByteArray().contentEquals(prefix)
    }

    fun ByteArray.endsWith(suffix: ByteArray): Boolean {
        if (this.size < suffix.size) return false
        return this.takeLast(suffix.size).toByteArray().contentEquals(suffix)
    }

}