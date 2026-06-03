package com.mkisten.vacancybackend.service;

import com.mkisten.vacancybackend.dto.ResumeProfileResponse;
import com.mkisten.vacancybackend.entity.ResumeProfile;
import com.mkisten.vacancybackend.repository.ResumeProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeProfileService {

    private final ResumeProfileRepository resumeProfileRepository;
    private final UserSettingsService userSettingsService;
    private final ResumeAccessService resumeAccessService;

    @Value("${app.resume.storage-path:uploads/resumes}")
    private String storagePath;

    @Transactional(readOnly = true)
    public List<ResumeProfileResponse> listProfiles(String token) {
        Long telegramId = userSettingsService.getTelegramId(token);
        return resumeProfileRepository.findByTelegramIdOrderByUpdatedAtDesc(telegramId).stream()
                .map(ResumeProfileResponse::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ResumeProfile> getActiveProfileEntity(String token) {
        Long telegramId = userSettingsService.getTelegramId(token);
        return resumeProfileRepository.findFirstByTelegramIdAndActiveTrueOrderByUpdatedAtDesc(telegramId);
    }

    @Transactional(readOnly = true)
    public ResumeProfileResponse getActiveProfile(String token) {
        return getActiveProfileEntity(token).map(ResumeProfileResponse::new).orElse(null);
    }

    @Transactional
    public ResumeProfileResponse upload(String token, MultipartFile file) throws IOException {
        resumeAccessService.assertPaidFeatureAvailable(token);
        Long telegramId = userSettingsService.getTelegramId(token);
        validateFile(file);

        Path userDir = ensureUserDirectory(telegramId);
        String safeName = sanitizeFileName(file.getOriginalFilename());
        String targetName = UUID.randomUUID() + "-" + safeName;
        Path target = userDir.resolve(targetName);
        try (var inputStream = file.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }

        String extractedText = extractText(file.getBytes(), safeName);
        if (extractedText.isBlank()) {
            Files.deleteIfExists(target);
            throw new IllegalArgumentException("Не удалось извлечь текст из резюме");
        }

        resumeProfileRepository.deactivateAllByTelegramId(telegramId);
        ResumeProfile profile = new ResumeProfile();
        profile.setTelegramId(telegramId);
        profile.setFileName(safeName);
        profile.setContentType(file.getContentType());
        profile.setStoragePath(target.toAbsolutePath().toString());
        profile.setExtractedText(limitText(extractedText, 60000));
        profile.setActive(true);
        return new ResumeProfileResponse(resumeProfileRepository.save(profile));
    }

    @Transactional
    public ResumeProfileResponse activate(String token, Long profileId) {
        resumeAccessService.assertPaidFeatureAvailable(token);
        Long telegramId = userSettingsService.getTelegramId(token);
        ResumeProfile profile = resumeProfileRepository.findByIdAndTelegramId(profileId, telegramId)
                .orElseThrow(() -> new IllegalArgumentException("Резюме не найдено"));
        resumeProfileRepository.deactivateAllByTelegramId(telegramId);
        profile.setActive(true);
        return new ResumeProfileResponse(resumeProfileRepository.save(profile));
    }

    @Transactional
    public void delete(String token, Long profileId) {
        Long telegramId = userSettingsService.getTelegramId(token);
        ResumeProfile profile = resumeProfileRepository.findByIdAndTelegramId(profileId, telegramId)
                .orElseThrow(() -> new IllegalArgumentException("Резюме не найдено"));
        if (profile.getStoragePath() != null) {
            try {
                Files.deleteIfExists(Paths.get(profile.getStoragePath()));
            } catch (IOException ex) {
                log.warn("Failed to delete resume file {}: {}", profile.getStoragePath(), ex.getMessage());
            }
        }
        resumeProfileRepository.delete(profile);
        if (Boolean.TRUE.equals(profile.getActive())) {
            resumeProfileRepository.findByTelegramIdOrderByUpdatedAtDesc(telegramId).stream().findFirst().ifPresent(next -> {
                next.setActive(true);
                resumeProfileRepository.save(next);
            });
        }
    }

    @Transactional(readOnly = true)
    public ResumeProfile requireProfile(String token, Long profileId) {
        Long telegramId = userSettingsService.getTelegramId(token);
        if (profileId != null) {
            return resumeProfileRepository.findByIdAndTelegramId(profileId, telegramId)
                    .orElseThrow(() -> new IllegalArgumentException("Резюме не найдено"));
        }
        return getActiveProfileEntity(token)
                .orElseThrow(() -> new IllegalStateException("Сначала загрузите резюме"));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл резюме не выбран");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("У файла резюме отсутствует имя");
        }
        String lower = fileName.toLowerCase();
        if (!(lower.endsWith(".pdf") || lower.endsWith(".docx") || lower.endsWith(".doc") || lower.endsWith(".txt"))) {
            throw new IllegalArgumentException("Поддерживаются только PDF, DOCX, DOC и TXT");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("Резюме должно быть не больше 5 МБ");
        }
    }

    private Path ensureUserDirectory(Long telegramId) throws IOException {
        Path dir = Paths.get(storagePath).toAbsolutePath().resolve(String.valueOf(telegramId));
        Files.createDirectories(dir);
        return dir;
    }

    private String sanitizeFileName(String original) {
        String normalized = Normalizer.normalize(original, Normalizer.Form.NFKC)
                .replaceAll("[^a-zA-Z0-9._-]+", "_");
        return normalized.isBlank() ? "resume.txt" : normalized;
    }

    private String extractText(byte[] fileBytes, String fileName) throws IOException {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".txt")) {
            return new String(fileBytes, StandardCharsets.UTF_8).trim();
        }
        if (lower.endsWith(".docx")) {
            try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(fileBytes))) {
                StringBuilder text = new StringBuilder();
                document.getParagraphs().forEach(paragraph -> text.append(paragraph.getText()).append('\n'));
                return text.toString().trim();
            }
        }
        if (lower.endsWith(".doc")) {
            try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(fileBytes));
                 WordExtractor extractor = new WordExtractor(document)) {
                return extractor.getText().trim();
            }
        }
        if (lower.endsWith(".pdf")) {
            try (PDDocument document = Loader.loadPDF(fileBytes)) {
                return new PDFTextStripper().getText(document).trim();
            }
        }
        throw new IllegalArgumentException("Unsupported resume format");
    }

    private String limitText(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\u0000", "").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
