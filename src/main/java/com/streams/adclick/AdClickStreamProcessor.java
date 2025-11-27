package com.streams.adclick;

import com.streams.adclick.model.AdClick;
import com.streams.adclick.serde.JsonSerde;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Properties;

/**
 * Kafka Streams Application demonstrating core concepts:
 * 
 * 1. StreamsBuilder - Building the processing topology
 * 2. KStream - Unbounded stream of events
 * 3. KTable - Changelog stream / Materialized view
 * 4. Windowing - Time-based aggregations (Tumbling Windows)
 * 5. State Stores - Persistent state backed by RocksDB
 * 6. Serdes - Custom serialization/deserialization
 * 7. Grouping & Aggregations - groupByKey, count
 * 8. Stream Transformations - selectKey, map, peek
 * 
 * Use Case: Real-time ad click aggregation per seller in 5-second windows
 */
public class AdClickStreamProcessor {
    
    private static final Logger log = LoggerFactory.getLogger(AdClickStreamProcessor.class);
    
    // Topic configuration
    private static final String INPUT_TOPIC = "ad-clicks";
    private static final String OUTPUT_TOPIC = "seller-click-counts";
    
    // Application configuration
    private static final String APPLICATION_ID = "ad-click-stream-processor";
    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    
    // State store name
    private static final String STATE_STORE_NAME = "seller-clicks-store";

    public static void main(String[] args) {
        // Build Kafka Streams configuration
        Properties props = buildStreamProperties();
        
        // Build the processing topology using StreamsBuilder
        StreamsBuilder builder = new StreamsBuilder();
        buildTopology(builder);
        
        // Create and start KafkaStreams instance
        KafkaStreams streams = new KafkaStreams(builder.build(), props);
        
        // Add shutdown hook for graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down Kafka Streams application...");
            streams.close(Duration.ofSeconds(10));
            log.info("Application shut down successfully");
        }));
        
        try {
            log.info("=== Starting Kafka Streams Application ===");
            log.info("Application ID: {}", APPLICATION_ID);
            log.info("Input Topic: {}", INPUT_TOPIC);
            log.info("Output Topic: {}", OUTPUT_TOPIC);
            log.info("State Store: {}", STATE_STORE_NAME);
            log.info("=========================================");
            
            streams.start();
            
        } catch (Exception e) {
            log.error("Failed to start Kafka Streams application", e);
            System.exit(1);
        }
    }

    /**
     * Configure Kafka Streams properties.
     * Demonstrates essential configurations for a Streams application.
     */
    private static Properties buildStreamProperties() {
        Properties props = new Properties();
        
        // Required: Application ID (consumer group + prefix for internal topics)
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, APPLICATION_ID);
        
        // Required: Kafka bootstrap servers
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        
        // Default Serdes for keys and values
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        
        // Start reading from earliest message if no offset exists
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        // Commit interval (how often to save state and offsets)
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 1000);
        
        // State directory (where RocksDB stores state)
        props.put(StreamsConfig.STATE_DIR_CONFIG, "/tmp/kafka-streams");
        
        return props;
    }

    /**
     * Build the Kafka Streams topology.
     * 
     * Topology Flow:
     * 1. Read KStream<String, AdClick> from input topic
     * 2. Re-key by seller_id (for proper partitioning)
     * 3. Group by key (seller_id)
     * 4. Window into 5-second tumbling windows
     * 5. Count clicks per seller per window
     * 6. Convert windowed KTable to KStream
     * 7. Log results
     * 8. Write to output topic
     */
    private static void buildTopology(StreamsBuilder builder) {
        
        // Custom Serde for AdClick objects
        JsonSerde<AdClick> adClickSerde = new JsonSerde<>(AdClick.class);
        
        // ========================================
        // STEP 1: Create KStream from input topic
        // ========================================
        // KStream represents an unbounded stream of events
        KStream<String, AdClick> adClickStream = builder.stream(
            INPUT_TOPIC,
            Consumed.with(Serdes.String(), adClickSerde)
        );
        
        log.info("Created KStream from topic: {}", INPUT_TOPIC);
        
        // ========================================
        // STEP 2: Re-key by seller_id
        // ========================================
        // Ensures events for same seller go to same partition
        KStream<String, AdClick> rekeyed = adClickStream
            .selectKey((key, adClick) -> {
                log.debug("Re-keying event: {} -> {}", key, adClick.getSellerId());
                return adClick.getSellerId();
            });
        
        // ========================================
        // STEP 3: Define Tumbling Window
        // ========================================
        // 5-second tumbling windows with no grace period
        TimeWindows tumblingWindow = TimeWindows
            .ofSizeWithNoGrace(Duration.ofSeconds(5));
        
        log.info("Configured tumbling window: 5 seconds");
        
        // ========================================
        // STEP 4: Group by key and apply windowing
        // ========================================
        KTable<Windowed<String>, Long> clickCounts = rekeyed
            // Group by seller_id (already the key after selectKey)
            .groupByKey(Grouped.with(Serdes.String(), adClickSerde))
            
            // Apply tumbling window
            .windowedBy(tumblingWindow)
            
            // Count events in each window
            // Creates a KTable backed by a state store (RocksDB)
            .count(Materialized.as(STATE_STORE_NAME));
        
        log.info("Created windowed KTable with state store: {}", STATE_STORE_NAME);
        
        // ========================================
        // STEP 5: Convert KTable to KStream for output
        // ========================================
        KStream<Windowed<String>, Long> countsStream = clickCounts.toStream();
        
        // ========================================
        // STEP 6: Log aggregation results
        // ========================================
        countsStream.peek((windowedKey, count) -> {
            String sellerId = windowedKey.key();
            long windowStart = windowedKey.window().start();
            long windowEnd = windowedKey.window().end();
            
            log.info("[AGGREGATION] Seller: {} | Count: {} | Window: {} to {}",
                sellerId,
                count,
                Instant.ofEpochMilli(windowStart),
                Instant.ofEpochMilli(windowEnd)
            );
        });
        
        // ========================================
        // STEP 7: Transform and write to output topic
        // ========================================
        countsStream
            .map((windowedKey, count) -> {
                // Create output key: "sellerId@windowStart"
                String sellerId = windowedKey.key();
                long windowStart = windowedKey.window().start();
                long windowEnd = windowedKey.window().end();
                
                String outputKey = sellerId + "@" + windowStart;
                
                // Create output value as JSON
                String outputValue = String.format(
                    "{\"seller_id\":\"%s\",\"count\":%d,\"window_start\":%d,\"window_end\":%d}",
                    sellerId, count, windowStart, windowEnd
                );
                
                return KeyValue.pair(outputKey, outputValue);
            })
            .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.String()));
        
        log.info("Configured output to topic: {}", OUTPUT_TOPIC);
    }
}
