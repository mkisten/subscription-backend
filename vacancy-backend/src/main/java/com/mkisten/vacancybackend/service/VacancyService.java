package com.mkisten.vacancybackend.service;

import com.mkisten.vacancybackend.client.AuthServiceClient;
import com.mkisten.vacancybackend.dto.ProfileResponse;
import com.mkisten.vacancybackend.entity.Vacancy;
import com.mkisten.vacancybackend.entity.VacancyStatus;
import com.mkisten.vacancybackend.repository.VacancyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VacancyService {

    private final VacancyRepository vacancyRepository;
    private final AuthServiceClient authServiceClient;

    private Long getTelegramId(String token) {

        ProfileResponse profile = authServiceClient.getCurrentUserProfile(token);
        return profile.getTelegramId();
    }

    /**
     * Сохранить новые вакансии пользователя.
     * Проверяет уникальность по (id + userTelegramId).
     * Только совершенно новые вакансии будут добавлены.
     */
    @Transactional
    public List<Vacancy> saveVacancies(String token, List<Vacancy> newVacancies) {
        Long userTelegramId = getTelegramId(token);
        if (newVacancies.isEmpty()) {
            return List.of();
        }

        Set<String> existingIds = vacancyRepository.findVacancyIdsByUser(userTelegramId);
        List<Vacancy> vacanciesToSave = newVacancies.stream()
                .filter(v -> !existingIds.contains(v.getId()))
                .peek(v -> {
                    v.setUserTelegramId(userTelegramId);
                    v.setStatus(VacancyStatus.NEW);
                    v.setSentToTelegram(false); // ЯВНО false для новых!
                    if (v.getLoadedAt() == null) {
                        v.setLoadedAt(java.time.LocalDateTime.now());
                    }
                })
                .collect(Collectors.toList());

        if (!vacanciesToSave.isEmpty()) {
            List<Vacancy> saved = vacancyRepository.saveAll(vacanciesToSave);
            log.info("Saved {} new vacancies for user {}", saved.size(), userTelegramId);
            return saved;
        }
        log.info("No new vacancies found for user {}", userTelegramId);
        return List.of();
    }

    /**
     * Пометить вакансию как отправленную в Telegram
     */
    @Transactional
    public void markAsSentToTelegram(String token, String vacancyId) {
        Long userTelegramId = getTelegramId(token);
        vacancyRepository.markSingleAsSentToTelegram(userTelegramId, vacancyId);
        log.debug("Marked vacancy {} as sent to telegram for user {}", vacancyId, userTelegramId);
    }

    /**
     * Пометить несколько вакансий как отправленные
     */
    @Transactional
    public void markMultipleAsSentToTelegram(String token, List<String> vacancyIds) {
        if (!vacancyIds.isEmpty()) {
            Long userTelegramId = getTelegramId(token);
            vacancyRepository.markAsSentToTelegram(userTelegramId, vacancyIds);
            log.info("Marked {} vacancies as sent to telegram for user {}", vacancyIds.size(), userTelegramId);
        }
    }

    /**
     * Пометить вакансию как просмотренную
     */
    @Transactional
    public void markAsViewed(String token, String vacancyId) {
        Long userTelegramId = getTelegramId(token);
        vacancyRepository.updateStatus(userTelegramId, vacancyId, VacancyStatus.VIEWED);
        log.debug("Marked vacancy {} as viewed for user {}", vacancyId, userTelegramId);
    }

    /**
     * Пометить несколько вакансий как просмотренные
     */
    @Transactional
    public void markMultipleAsViewed(String token, List<String> vacancyIds) {
        if (!vacancyIds.isEmpty()) {
            Long userTelegramId = getTelegramId(token);
            vacancyRepository.updateMultipleStatus(userTelegramId, vacancyIds, VacancyStatus.VIEWED);
            log.info("Marked {} vacancies as viewed for user {}", vacancyIds.size(), userTelegramId);
        }
    }

    /**
     * Получить все вакансии пользователя или только определённого статуса
     */
    @Transactional(readOnly = true)
    public List<Vacancy> getUserVacancies(String token, VacancyStatus status) {
        Long userTelegramId = getTelegramId(token);
        if (status == null) {
            return vacancyRepository.findByUserTelegramIdOrderByPublishedAtDesc(userTelegramId);
        }
        return vacancyRepository.findByUserTelegramIdAndStatusOrderByPublishedAtDesc(userTelegramId, status);
    }

    /**
     * Удалить одну вакансию пользователя
     */
    @Transactional
    public void deleteVacancy(String token, String vacancyId) {
        Long userTelegramId = getTelegramId(token);
        vacancyRepository.deleteByUserAndId(userTelegramId, vacancyId);
        log.debug("Deleted vacancy {} for user {}", vacancyId, userTelegramId);
    }

    /**
     * Удалить все вакансии пользователя
     */
    @Transactional
    public void deleteAllVacancies(String token) {
        Long userTelegramId = getTelegramId(token);
        vacancyRepository.deleteAllByUserTelegramId(userTelegramId);
        log.info("Deleted all vacancies for user {}", userTelegramId);
    }

    /**
     * Получить количество новых вакансий пользователя
     */
    @Transactional(readOnly = true)
    public Long getNewVacanciesCount(String token) {
        Long userTelegramId = getTelegramId(token);
        return vacancyRepository.countNewVacancies(userTelegramId);
    }
}
