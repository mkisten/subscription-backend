package com.mkisten.vacancybackend.repository;

import com.mkisten.vacancybackend.entity.Vacancy;
import com.mkisten.vacancybackend.entity.VacancyKey;
import com.mkisten.vacancybackend.entity.VacancyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface VacancyRepository extends JpaRepository<Vacancy, VacancyKey> {

    // Найти все вакансии пользователя
    List<Vacancy> findByUserTelegramIdOrderByPublishedAtDesc(Long userTelegramId);

    // Найти вакансии пользователя по статусу
    List<Vacancy> findByUserTelegramIdAndStatusOrderByPublishedAtDesc(
            Long userTelegramId, VacancyStatus status);

    // Проверка существования вакансии для пользователя
    boolean existsByIdAndUserTelegramId(String id, Long userTelegramId);

    // Найти только неотправленные вакансии пользователя, отсортированные по дате
    List<Vacancy> findByUserTelegramIdAndSentToTelegramFalseOrderByPublishedAtAsc(Long userTelegramId);

    // Получить id всех вакансий пользователя
    @Query("SELECT v.id FROM Vacancy v WHERE v.userTelegramId = :userTelegramId")
    Set<String> findVacancyIdsByUser(@Param("userTelegramId") Long userTelegramId);

    // Обновить статус вакансии пользователя
    @Modifying
    @Query("UPDATE Vacancy v SET v.status = :status WHERE v.userTelegramId = :userTelegramId AND v.id = :vacancyId")
    void updateStatus(@Param("userTelegramId") Long userTelegramId,
                      @Param("vacancyId") String vacancyId,
                      @Param("status") VacancyStatus status);


    // Обновить статус нескольких вакансий
    @Modifying
    @Query("UPDATE Vacancy v SET v.status = :status WHERE v.userTelegramId = :userTelegramId AND v.id IN :vacancyIds")
    void updateMultipleStatus(@Param("userTelegramId") Long userTelegramId,
                              @Param("vacancyIds") List<String> vacancyIds,
                              @Param("status") VacancyStatus status);

    // Удалить вакансию пользователя
    @Modifying
    @Query("DELETE FROM Vacancy v WHERE v.userTelegramId = :userTelegramId AND v.id = :vacancyId")
    void deleteByUserAndId(@Param("userTelegramId") Long userTelegramId,
                           @Param("vacancyId") String vacancyId);

    // Удалить все вакансии пользователя
    @Modifying
    @Query("DELETE FROM Vacancy v WHERE v.userTelegramId = :userTelegramId")
    void deleteAllByUserTelegramId(@Param("userTelegramId") Long userTelegramId);

    // Считать новые вакансии пользователя
    @Query("SELECT COUNT(v) FROM Vacancy v WHERE v.userTelegramId = :userTelegramId AND v.status = 'NEW'")
    Long countNewVacancies(@Param("userTelegramId") Long userTelegramId);

    // Пометить вакансии как отправленные
    @Modifying
    @Query("UPDATE Vacancy v SET v.sentToTelegram = true WHERE v.userTelegramId = :userTelegramId AND v.id IN :ids")
    int markAsSentToTelegram(@Param("userTelegramId") Long userTelegramId, @Param("ids") List<String> ids);

    // Пометить вакансию как отправленную
    @Modifying
    @Query("UPDATE Vacancy v SET v.sentToTelegram = true WHERE v.userTelegramId = :userTelegramId AND v.id = :vacancyId")
    int markSingleAsSentToTelegram(@Param("userTelegramId") Long userTelegramId, @Param("vacancyId") String vacancyId);
}
