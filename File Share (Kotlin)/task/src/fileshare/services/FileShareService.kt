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
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.*


@Service
class FileShareService @Autowired constructor(
    private val uploadedFileRepository: UploadedFileRepository
) {

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

    fun getUploadsDirInfo(): ResponseEntity<Any> {
        val uploadsDir: Path = Path(uploadsDirPath)
        val totalFiles = uploadsDir.toFile().listFiles()?.size ?: 0
        val totalBytes = uploadsDir.toFile().listFiles()?.sumOf { it.length() } ?: 0
        return ResponseEntity.ok(DirInfoDTO(totalFiles = totalFiles, totalBytes = totalBytes))
    }

    fun saveFileToUploadsDir(file: MultipartFile): ResponseEntity<Any> {
        // Check if file is empty
        if (file.isEmpty) {
            return ResponseEntity.badRequest().body("File is empty")
        }

        // Get file name and its content type
        val fileName = file.originalFilename ?: return ResponseEntity.badRequest().body("File name is empty")
        val fileType = file.contentType ?: return ResponseEntity.badRequest().body("File content type is empty")

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

}