package com.example.docs.service;

import com.example.docs.config.AppProperties;
import com.example.docs.dto.DocumentInfo;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Flat-directory storage of .docx files. A document's id is its file name. */
@Service
public class DocumentStorageService {

    private static final Logger log = LoggerFactory.getLogger(DocumentStorageService.class);

    /** A plain file name ending in .docx: no path separators, no "..", no leading dot. */
    private static final Pattern VALID_ID = Pattern.compile("^[^/\\\\.][^/\\\\]*\\.docx$", Pattern.CASE_INSENSITIVE);

    private final Path root;

    public DocumentStorageService(AppProperties appProperties) {
        this.root = Path.of(appProperties.storageDir()).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(root);
        if (list().isEmpty()) {
            Path sample = root.resolve("sample.docx");
            writeSampleDocx(sample);
            log.info("Storage was empty; created {}", sample);
        }
        log.info("Document storage: {}", root);
    }

    public List<DocumentInfo> list() {
        try (Stream<Path> files = Files.list(root)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(p -> VALID_ID.matcher(p.getFileName().toString()).matches())
                    .map(this::toInfo)
                    .sorted(Comparator.comparing(DocumentInfo::title, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Resolves an id to an existing file, rejecting anything that could escape the storage directory. */
    public Path resolve(String id) {
        if (id == null || !VALID_ID.matcher(id).matches()) {
            throw new DocumentNotFoundException(id);
        }
        Path file = root.resolve(id).normalize();
        if (!file.getParent().equals(root) || !Files.isRegularFile(file)) {
            throw new DocumentNotFoundException(id);
        }
        return file;
    }

    public DocumentInfo info(String id) {
        return toInfo(resolve(id));
    }

    /**
     * Document key for ONLYOFFICE. Derived from name + size + mtime, so it changes after every save
     * and Document Server never serves a stale cached version.
     */
    public String documentKey(String id) {
        Path file = resolve(id);
        try {
            String seed = id + "|" + Files.size(file) + "|" + Files.getLastModifiedTime(file).toMillis();
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Overwrites the document atomically: writes a temp file next to it, then moves it into place. */
    public void overwrite(String id, InputStream content) throws IOException {
        Path target = resolve(id);
        Path tmp = Files.createTempFile(root, ".upload-", ".tmp");
        try {
            Files.copy(content, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private DocumentInfo toInfo(Path file) {
        try {
            return new DocumentInfo(file.getFileName().toString(), file.getFileName().toString(),
                    Files.size(file), Files.getLastModifiedTime(file).toInstant());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Writes the smallest valid .docx: content types, package relationships and one paragraph. */
    private static void writeSampleDocx(Path target) throws IOException {
        String contentTypes = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>""";
        String rels = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>""";
        String document = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    <w:p><w:r><w:rPr><w:b/><w:sz w:val="36"/></w:rPr><w:t>Sample document</w:t></w:r></w:p>
                    <w:p><w:r><w:t>Edit this text in ONLYOFFICE, then close the editor or press Ctrl+S to save it back to the server.</w:t></w:r></w:p>
                  </w:body>
                </w:document>""";
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            putEntry(zip, "[Content_Types].xml", contentTypes);
            putEntry(zip, "_rels/.rels", rels);
            putEntry(zip, "word/document.xml", document);
        }
    }

    private static void putEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
