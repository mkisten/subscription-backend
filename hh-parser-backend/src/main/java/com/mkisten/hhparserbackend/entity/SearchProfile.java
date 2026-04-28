package com.mkisten.hhparserbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "search_profiles")
@Getter
@Setter
@NoArgsConstructor
public class SearchProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cache_key", nullable = false, unique = true, length = 512)
    private String cacheKey;

    @Column(name = "params_json", nullable = false, columnDefinition = "TEXT")
    private String paramsJson;

    @Column(name = "query_text", length = 512)
    private String queryText;

    @Column(name = "areas", length = 255)
    private String areas;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "last_requested_at", nullable = false)
    private LocalDateTime lastRequestedAt;

    @Column(name = "last_prefetched_at")
    private LocalDateTime lastPrefetchedAt;

    @Column(name = "last_success_at")
    private LocalDateTime lastSuccessAt;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "last_error", length = 1000)
    private String lastError;
}
