package com.mkisten.hhparserbackend.repository;

import com.mkisten.hhparserbackend.entity.SearchPageCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SearchPageCacheRepository extends JpaRepository<SearchPageCache, Long> {

    Optional<SearchPageCache> findFirstByCacheKeyAndPageNumberOrderByFetchedAtDesc(String cacheKey, int pageNumber);

    void deleteByCacheKeyAndFetchedAtBefore(String cacheKey, LocalDateTime fetchedAt);
}
