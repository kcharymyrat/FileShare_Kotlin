package fileshare.web

import fileshare.services.FileShareService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
class UploadRestController @Autowired constructor(
    private val fileShareService: FileShareService
) {

    @PostMapping(path = ["/api/v1/upload"])
    fun upload(@RequestParam file: MultipartFile): ResponseEntity<Any> {
        return fileShareService.saveFileToUploadsDir(file)
    }

    @GetMapping(path = ["api/v1/info"])
    fun getDirInfo(): ResponseEntity<Any> {
        return fileShareService.getUploadsDirInfo()
    }

    @GetMapping(path = ["api/v1/download/{fileName}"])
    fun download(@PathVariable fileName: String): ResponseEntity<Any> {
        return fileShareService.downloadFromUploadsDir(fileName)
    }
}