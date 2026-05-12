package com.vaibhavgala.url_shortner.config;

import com.maxmind.geoip2.DatabaseReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.io.File;

@Configuration
public class GeoIPConfig {

    @Bean
    @ConditionalOnProperty(name = "geoip.enabled", havingValue = "true")
    public DatabaseReader databaseReader() throws Exception {
        File database = new File(System.getenv().getOrDefault(
                "GEOIP_DB_PATH", "src/main/resources/GeoLite2-City.mmdb"));
        return new DatabaseReader.Builder(database).build();
    }
}
