package com.mkisten.subscriptionbackend.repository;

import com.mkisten.subscriptionbackend.entity.User;
import com.mkisten.subscriptionbackend.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByTelegramId(Long telegramId);
    Optional<User> findByEmail(String email);
    Optional<User> findByLogin(String login);
    boolean existsByLogin(String login);
    List<User> findBySubscriptionEndDateBefore(LocalDate date);
    List<User> findBySubscriptionEndDateAfter(LocalDate date);
    boolean existsByTelegramId(Long telegramId);

    // Исправленный метод поиска
    @Query("SELECT u FROM User u WHERE " +
            "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "CAST(u.telegramId AS string) LIKE CONCAT('%', :query, '%')")
    List<User> searchUsers(@Param("query") String query);
    List<User> findByRole(UserRole role);
    Optional<User> findByUsername(String username);

    // Альтернативный вариант - поиск по отдельным полям
    List<User> findByFirstNameContainingIgnoreCase(String firstName);
    List<User> findByLastNameContainingIgnoreCase(String lastName);
    List<User> findByUsernameContainingIgnoreCase(String username);
    List<User> findByEmailContainingIgnoreCase(String email);
}
