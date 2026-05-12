package com.vaibhavgala.url_shortner.repo;

import com.vaibhavgala.url_shortner.models.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {
    Optional<UrlMapping> findByShortCode(String shortCode);


    boolean existsByShortCode(String shortCode);

    Optional<UrlMapping> findByOriginalUrl(String originalUrl);

    @Modifying
    @Transactional
    @Query("UPDATE UrlMapping u SET u.clickCount = u.clickCount + :count WHERE u.shortCode = :shortCode")
    void incrementClickCountBy(String shortCode, int count);


    long countByCustom(boolean isCustom);


}
