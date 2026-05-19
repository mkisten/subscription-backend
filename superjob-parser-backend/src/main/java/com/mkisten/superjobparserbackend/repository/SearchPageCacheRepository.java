package com.mkisten.superjobparserbackend.repository;

import com.mkisten.superjobparserbackend.entity.SearchPageCache;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SearchPageCacheRepository extends JpaRepository<SearchPageCache, Long> {

    Optional<SearchPageCache> findFirstByCacheKeyAndPageNumberOrderByFetchedAtDesc(String cacheKey, int pageNumber);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from SearchPageCache c where c.cacheKey = :cacheKey and c.fetchedAt < :fetchedAt")
    int deleteExpiredByCacheKey(@Param("cacheKey") String cacheKey, @Param("fetchedAt") LocalDateTime fetchedAt);
}
