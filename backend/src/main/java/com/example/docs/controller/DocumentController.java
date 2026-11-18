package com.example.docs.controller;

import com.example.docs.dto.CallbackResponse;
import com.example.docs.dto.DocumentInfo;
import com.example.docs.dto.EditorConfigResponse;
import com.example.docs.service.CallbackService;
import com.example.docs.service.DocumentNotFoundException;
import com.example.docs.service.DocumentStorageService;
import com.example.docs.service.EditorConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);
    private static final MediaType DOCX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final DocumentStorageService storage;
    private final EditorConfigService editorConfigService;
    private final CallbackService callbackService;

    public DocumentController(DocumentStorageService storage, EditorConfigService editorConfigService,
                              CallbackService callbackService) {
        this.storage = storage;
        this.editorConfigService = editorConfigService;
        this.callbackService = callbackService;
    }

    /** Endpoint 1: list the available .docx files. */
    @GetMapping
    public List<DocumentInfo> list() {
        return storage.list();
    }

    /** Endpoint 2: the (signed) ONLYOFFICE editor config for one document. */
    @GetMapping("/{id}/config")
    public EditorConfigResponse config(@PathVariable String id,
                                       @RequestParam(defaultValue = "user-1") String userId,
                                       @RequestParam(defaultValue = "Demo User") String userName) {
        return editorConfigService.buildConfig(id, userId, userName);
    }

    /** The raw file. Document Server downloads it from here when the editor opens. */
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable String id) {
        Path file = storage.resolve(id);
        return ResponseEntity.ok()
                .contentType(DOCX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(id, StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(file));
    }

    /**
     * Endpoint 3: Document Server callback. Status 2 (all editors closed) and 6 (force save) carry a URL
     * to the edited file, which is downloaded and written over the stored one.
     * Always answers HTTP 200; the {"error": n} body tells Document Server whether it worked.
     */
    @PostMapping("/track")
    public CallbackResponse track(@RequestParam String id,
                                  @RequestBody Map<String, Object> body,
                                  @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        try {
            Map<String, Object> payload = callbackService.authenticate(body, authorization);
            return callbackService.handle(id, payload) ? CallbackResponse.ok() : CallbackResponse.failed();
        } catch (SecurityException e) {
            log.warn("Rejected callback for {}: {}", id, e.getMessage());
            return CallbackResponse.failed();
        } catch (DocumentNotFoundException e) {
            log.warn("Callback for unknown document {}", id);
            return CallbackResponse.failed();
        }
    }
}
