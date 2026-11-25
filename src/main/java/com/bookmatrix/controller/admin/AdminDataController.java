package com.bookmatrix.controller.admin;

import com.bookmatrix.dto.Views.BackupReport;
import com.bookmatrix.dto.Views.ImportReport;
import com.bookmatrix.exception.ValidationException;
import com.bookmatrix.fileio.DataTransferService;
import com.bookmatrix.security.SessionUser;
import com.bookmatrix.util.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.List;

/** CSV export/import and backups. */
@RestController
@RequestMapping("/api/admin/data")
public class AdminDataController {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final DataTransferService dataService;

    public AdminDataController(DataTransferService dataService) {
        this.dataService = dataService;
    }

    @GetMapping("/events.csv")
    public ResponseEntity<String> exportEvents() {
        return csv("bookmatrix-events.csv", dataService.exportEventsCsv());
    }

    @GetMapping("/bookings.csv")
    public ResponseEntity<String> exportBookings() {
        return csv("bookmatrix-bookings.csv", dataService.exportBookingsCsv());
    }

    @PostMapping("/import/events")
    public ApiResponse<ImportReport> importEvents(SessionUser admin, @RequestParam(value = "file", required = false) MultipartFile file)
            throws IOException {
        if (file == null) {
            throw ValidationException.forField("file", "Choose a CSV file to upload.");
        }
        byte[] bytes = file.getBytes();
        DataTransferService.checkUpload(file.getOriginalFilename(), file.getSize(), bytes);
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw ValidationException.forField("file", "The file is not valid UTF-8 text. Save it as 'CSV UTF-8'.");
        }
        ImportReport report = dataService.importEvents(admin, text);
        return ApiResponse.ok(report, report.imported() + " event(s) imported, " + report.skipped() + " skipped.");
    }

    @PostMapping("/backup")
    public ApiResponse<BackupReport> backup(SessionUser admin) {
        return ApiResponse.ok(dataService.backup(admin), "Backup written to disk.");
    }

    @GetMapping("/backups")
    public ApiResponse<List<String>> backups() {
        return ApiResponse.ok(dataService.listBackups());
    }

    private static ResponseEntity<String> csv(String filename, String body) {
        return ResponseEntity.ok().contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }
}
