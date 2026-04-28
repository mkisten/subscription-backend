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
@Table(name = "search_page_cache")
@Getter
@Setter
@NoArgsConstructor
public class SearchPageCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cache_key", nullable = false, length = 512)
    private String cacheKey;

    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    @Column(name = "found_count", nullable = false)
    private long foundCount;

    @Column(name = "pages_count", nullable = false)
    private int pagesCount;

    @Column(name = "items_json", nullable = false, columnDefinition = "TEXT")
    private String itemsJson;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;
}
