package com.vaibhavgala.url_shortner.repo;

import com.vaibhavgala.url_shortner.models.UrlClickAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UrlClickAnalyticsRepository extends JpaRepository<UrlClickAnalytics, Long> {

    // Find clicks by short code
    List<UrlClickAnalytics> findByShortCodeOrderByTimestampDesc(String shortCode);

    // Count clicks by country
    @Query("SELECT country, COUNT(*) FROM UrlClickAnalytics WHERE shortCode = ?1 GROUP BY country")
    List<Object[]> findClicksByCountry(String shortCode);

    // Count clicks by device type
    @Query("SELECT deviceType, COUNT(*) FROM UrlClickAnalytics WHERE shortCode = ?1 GROUP BY deviceType")
    List<Object[]> findClicksByDeviceType(String shortCode);

    // Count clicks by hour for last 24 hours
    @Query("SELECT HOUR(timestamp), COUNT(*) FROM UrlClickAnalytics WHERE shortCode = ?1 AND timestamp >= ?2 GROUP BY HOUR(timestamp)")
    List<Object[]> findClicksByHour(String shortCode, LocalDateTime since);

    // Top referrers
    @Query("SELECT referer, COUNT(*) FROM UrlClickAnalytics WHERE shortCode = ?1 AND referer IS NOT NULL GROUP BY referer ORDER BY COUNT(*) DESC")
    List<Object[]> findTopReferrers(String shortCode);
}
