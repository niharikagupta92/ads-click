package com.streams.adclick;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streams.adclick.model.AdClick;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ExecutionException;

/**
 * Test data producer for generating sample AdClick events.
 * Demonstrates Kafka producer configuration and message sending.
 */
public class AdClickProducer {
    
    private static final Logger log = LoggerFactory.getLogger(AdClickProducer.class);
    private static final String TOPIC = "ad-clicks";
    private static final String[] SELLERS = {"SellerA", "SellerB", "SellerC"};
    private static final String[] ADS = {"ad-1", "ad-2", "ad-3", "ad-4"};

    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all"); // Wait for all replicas
        props.put(ProducerConfig.RETRIES_CONFIG, 3);

        ObjectMapper mapper = new ObjectMapper();
        Random random = new Random();

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            
            log.info("=== Starting Ad Click Producer ===");
            log.info("Topic: {}", TOPIC);
            log.info("Sending 50 events with 200ms delay...");
            log.info("===================================\n");

            for (int i = 0; i < 50; i++) {
                String seller = SELLERS[random.nextInt(SELLERS.length)];
                String ad = ADS[random.nextInt(ADS.length)];

                AdClick adClick = new AdClick(
                    seller,
                    ad,
                    System.currentTimeMillis(),
                    "/product/" + i
                );

                String json = mapper.writeValueAsString(adClick);
                ProducerRecord<String, String> record = new ProducerRecord<>(
                    TOPIC,
                    seller,  // Key by seller for partitioning
                    json
                );

                // Send synchronously to see results
                try {
                    RecordMetadata metadata = producer.send(record).get();
                    log.info("✓ Sent: {} (partition={}, offset={})",
                        adClick.getSellerId(), metadata.partition(), metadata.offset());
                } catch (ExecutionException | InterruptedException e) {
                    log.error("Failed to send message", e);
                }

                // Delay between messages
                Thread.sleep(200);
            }

            log.info("\n✓ Successfully sent 50 events!");
        }
    }
}
