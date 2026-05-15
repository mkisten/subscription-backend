package com.mkisten.getmatchparserbackend.repository;

import com.mkisten.getmatchparserbackend.entity.SearchProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SearchProfileRepository extends JpaRepository<SearchProfile, Long> {

    Optional<SearchProfile> findByCacheKey(String cacheKey);

    List<SearchProfile> findByEnabledTrueAndLastRequestedAtAfterOrderByLastRequestedAtDesc(LocalDateTime cutoff);
}
