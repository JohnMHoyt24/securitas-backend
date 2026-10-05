package com.securitas.backend.ingest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/admin")
public class ImportController {

    private final AmlCsvImportService importService;

    public ImportController(AmlCsvImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/import")
    public ResponseEntity<AmlCsvImportService.ImportResult> importCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Integer maxAccounts,
            @RequestParam(required = false) Integer maxTransactions) throws IOException {
        Path tempFile = Files.createTempFile("aml-import-", ".csv");
        try {
            file.transferTo(tempFile);
            AmlCsvImportService.ImportOptions options = (maxAccounts == null || maxTransactions == null)
                    ? AmlCsvImportService.ImportOptions.unlimited()
                    : new AmlCsvImportService.ImportOptions(maxAccounts, maxTransactions);
            AmlCsvImportService.ImportResult result = importService.importCsv(tempFile, options);
            return ResponseEntity.ok(result);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> reset() {
        importService.resetImportedData();
        return ResponseEntity.noContent().build();
    }
}
