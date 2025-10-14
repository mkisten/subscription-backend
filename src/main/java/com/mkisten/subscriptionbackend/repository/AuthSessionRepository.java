package com.mkisten.subscriptionbackend.repository;

import com.mkisten.subscriptionbackend.entity.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {

    Optional<AuthSession> findBySessionIdAndDeviceId(String sessionId, String deviceId);

    List<AuthSession> findByStatus(AuthSession.AuthStatus status);

    @Modifying
    @Query("DELETE FROM AuthSession a WHERE a.createdAt < :expiryTime")
    void deleteExpiredSessions(@Param("expiryTime") LocalDateTime expiryTime);

    @Query("SELECT COUNT(a) FROM AuthSession a WHERE a.status = 'PENDING'")
    long countPendingSessions();

    Optional<AuthSession> findBySessionId(String sessionId);

    @Query("SELECT a FROM AuthSession a WHERE a.sessionId = :sessionId")
    Optional<AuthSession> findBySessionIdCustom(@Param("sessionId") String sessionId);

    List<AuthSession> findByStatusAndCreatedAtBefore(AuthSession.AuthStatus authStatus, LocalDateTime cutoffTime);
}