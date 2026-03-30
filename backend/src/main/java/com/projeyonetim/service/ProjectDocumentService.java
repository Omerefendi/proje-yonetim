package com.projeyonetim.service;

import com.projeyonetim.dto.ProjectDocumentResponse;
import com.projeyonetim.model.Project;
import com.projeyonetim.model.ProjectDocument;
import com.projeyonetim.model.User;
import com.projeyonetim.repository.ProjectDocumentRepository;
import com.projeyonetim.repository.ProjectRepository;
import com.projeyonetim.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class ProjectDocumentService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "jpg", "jpeg", "png");

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "image/jpeg",
            "image/png");

    @Autowired
    private ProjectDocumentRepository projectDocumentRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Value("${app.storage.project-documents-dir:./data/project-documents}")
    private String projectDocumentsDir;

    public List<ProjectDocumentResponse> getDocumentsByProject(Long projectId) {
        ensureProjectExists(projectId);
        return projectDocumentRepository.findByProjectIdOrderByUploadedAtDesc(projectId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<ProjectDocumentResponse> storeDocuments(Long projectId, MultipartFile[] files, String username) {
        if (files == null || files.length == 0) {
            throw new RuntimeException("Yuklenecek dosya secilmedi.");
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Proje bulunamadi: " + projectId));

        User uploadedBy = resolveUploadedBy(username);
        Path projectDirectory = resolveProjectDirectory(projectId);
        createDirectories(projectDirectory);

        List<ProjectDocumentResponse> uploadedDocuments = new ArrayList<>();
        for (MultipartFile file : files) {
            validateFile(file);

            String originalFileName = sanitizeOriginalFileName(file.getOriginalFilename());
            String extension = extractExtension(originalFileName);
            String storedFileName = UUID.randomUUID() + "." + extension;
            Path targetPath = projectDirectory.resolve(storedFileName).normalize();

            if (!targetPath.startsWith(projectDirectory)) {
                throw new RuntimeException("Gecersiz dosya yolu olustu.");
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException("Dosya kaydedilemedi: " + originalFileName, e);
            }

            ProjectDocument document = new ProjectDocument();
            document.setProject(project);
            document.setOriginalFileName(originalFileName);
            document.setStoredFileName(storedFileName);
            document.setContentType(resolveContentType(file.getContentType()));
            document.setSizeBytes(file.getSize());
            document.setUploadedBy(uploadedBy);

            uploadedDocuments.add(toResponse(projectDocumentRepository.save(document)));
        }

        return uploadedDocuments;
    }

    public ProjectDocument getDocument(Long projectId, Long documentId) {
        ensureProjectExists(projectId);
        return projectDocumentRepository.findByIdAndProjectId(documentId, projectId)
                .orElseThrow(() -> new RuntimeException("Dokuman bulunamadi: " + documentId));
    }

    public Resource loadDocumentAsResource(ProjectDocument document) {
        Path filePath = resolveStoredPath(document);
        if (!Files.exists(filePath)) {
            throw new RuntimeException("Dokuman dosyasi bulunamadi.");
        }
        return new FileSystemResource(filePath);
    }

    @Transactional
    public void deleteDocument(Long projectId, Long documentId) {
        ProjectDocument document = getDocument(projectId, documentId);
        deleteStoredFile(document);
        projectDocumentRepository.delete(document);
        cleanupProjectDirectory(projectId);
    }

    @Transactional
    public void deleteAllForProject(Long projectId) {
        List<ProjectDocument> documents = projectDocumentRepository.findByProjectIdOrderByUploadedAtDesc(projectId);
        for (ProjectDocument document : documents) {
            deleteStoredFile(document);
        }
        projectDocumentRepository.deleteAll(documents);
        cleanupProjectDirectory(projectId);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Bos dosya yuklenemez.");
        }

        String originalFileName = sanitizeOriginalFileName(file.getOriginalFilename());
        String extension = extractExtension(originalFileName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new RuntimeException("Sadece PDF, Word, Excel, JPG, JPEG ve PNG dosyalari yuklenebilir.");
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            String normalizedContentType = contentType.toLowerCase(Locale.ROOT);
            if (!"application/octet-stream".equals(normalizedContentType)
                    && !ALLOWED_CONTENT_TYPES.contains(normalizedContentType)) {
                throw new RuntimeException("Desteklenmeyen dosya tipi: " + originalFileName);
            }
        }
    }

    private User resolveUploadedBy(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }

        Optional<User> uploadedBy = userRepository.findByUsername(username);
        return uploadedBy.orElse(null);
    }

    private String sanitizeOriginalFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new RuntimeException("Dosya adi bos olamaz.");
        }

        String safeFileName = Paths.get(originalFileName).getFileName().toString().trim();
        safeFileName = safeFileName.replace("\r", "_").replace("\n", "_");
        if (safeFileName.isBlank()) {
            throw new RuntimeException("Gecersiz dosya adi.");
        }
        return safeFileName;
    }

    private String extractExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == fileName.length() - 1) {
            throw new RuntimeException("Dosya uzantisi bulunamadi: " + fileName);
        }
        return fileName.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }

    private String resolveContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        return contentType;
    }

    private ProjectDocumentResponse toResponse(ProjectDocument document) {
        return new ProjectDocumentResponse(
                document.getId(),
                document.getOriginalFileName(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getUploadedAt(),
                document.getUploadedBy() != null ? document.getUploadedBy().getFullName() : null);
    }

    private void ensureProjectExists(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new RuntimeException("Proje bulunamadi: " + projectId);
        }
    }

    private Path resolveStorageRoot() {
        return Paths.get(projectDocumentsDir).toAbsolutePath().normalize();
    }

    private Path resolveProjectDirectory(Long projectId) {
        return resolveStorageRoot().resolve("project-" + projectId).normalize();
    }

    private Path resolveStoredPath(ProjectDocument document) {
        Path projectDirectory = resolveProjectDirectory(document.getProject().getId());
        Path resolvedPath = projectDirectory.resolve(document.getStoredFileName()).normalize();
        if (!resolvedPath.startsWith(projectDirectory)) {
            throw new RuntimeException("Gecersiz dokuman yolu.");
        }
        return resolvedPath;
    }

    private void createDirectories(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new RuntimeException("Dokuman klasoru olusturulamadi.", e);
        }
    }

    private void deleteStoredFile(ProjectDocument document) {
        try {
            Files.deleteIfExists(resolveStoredPath(document));
        } catch (IOException e) {
            throw new RuntimeException("Dokuman silinemedi: " + document.getOriginalFileName(), e);
        }
    }

    private void cleanupProjectDirectory(Long projectId) {
        Path projectDirectory = resolveProjectDirectory(projectId);
        try {
            if (!Files.exists(projectDirectory) || !Files.isDirectory(projectDirectory)) {
                return;
            }

            try (Stream<Path> files = Files.list(projectDirectory)) {
                if (files.findAny().isEmpty()) {
                    Files.deleteIfExists(projectDirectory);
                }
            }
        } catch (IOException e) {
            // Klasor temizligi basarisiz olsa bile belge islemi tamamlanmis sayilir.
        }
    }
}
