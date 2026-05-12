package com.vaibhavgala.url_shortner.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

@EnableKafka
@Configuration
public class KafkaConfig {
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    private static final String GROUP_ID = "click-tracking-group-v4";

    // Producer Configuration
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // Idempotent producer settings - required for exactly-once guarantees
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        // High-throughput batching and compression options
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 65536);         // 64KB batch size
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 5);              // Wait up to 5ms to batch records
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");// Compression codec

        // Retry config
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 67108864);   // 64MB buffer memory

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // Consumer Configuration
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        // Optimized consumer batch processing configs
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);             // Max batch size
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);     // 5 min max poll interval
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);        // 30 sec session timeout
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);     // 10 sec heartbeat
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024);            // Min 1KB fetch
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);            // Max wait for fetch 500ms
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Disable auto commit for safer batch processing and offset management
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());

        // Enable batch listener for batch processing
        factory.setBatchListener(true);

        // Set concurrency to 8 if you have 8 partitions for throughput
        factory.setConcurrency(8);

        // Configure container properties for batch acknowledgment and polling
        ContainerProperties containerProps = factory.getContainerProperties();
        containerProps.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);         // Acknowledge offset after batch processing
        containerProps.setPollTimeout(3000);                                 // 3 second poll timeout
        containerProps.setIdleEventInterval(30000L);                         // 30 second idle event interval
        containerProps.setMissingTopicsFatal(false);                         // Don't fail if topic missing

        return factory;
    }
}
