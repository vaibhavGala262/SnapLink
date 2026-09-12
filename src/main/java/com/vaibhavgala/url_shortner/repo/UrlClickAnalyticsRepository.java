package com.vaibhavgala.url_shortner.repo;

import com.vaibhavgala.url_shortner.models.UrlClickAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UrlClickAnalyticsRepository extends JpaRepository<UrlClickAnalytics, Long> {

    List<UrlClickAnalytics> findByShortCodeOrderByTimestampDesc(String shortCode);

    @Query("SELECT country, COUNT(*) FROM UrlClickAnalytics WHERE shortCode = ?1 GROUP BY country")
    List<Object[]> findClicksByCountry(String shortCode);

    @Query("SELECT deviceType, COUNT(*) FROM UrlClickAnalytics WHERE shortCode = ?1 GROUP BY deviceType")
    List<Object[]> findClicksByDeviceType(String shortCode);

    @Query(value = "SELECT EXTRACT(HOUR FROM timestamp) AS hour, COUNT(*) FROM url_click_analytics WHERE short_code = ?1 AND timestamp >= ?2 GROUP BY hour ORDER BY hour", nativeQuery = true)
    List<Object[]> findClicksByHour(String shortCode, LocalDateTime since);

    @Query("SELECT referer, COUNT(*) FROM UrlClickAnalytics WHERE shortCode = ?1 AND referer IS NOT NULL GROUP BY referer ORDER BY COUNT(*) DESC")
    List<Object[]> findTopReferrers(String shortCode);
}