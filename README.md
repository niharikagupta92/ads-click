# Kafka Streams: Real-Time Ad Click Aggregation

A comprehensive hands-on project demonstrating **Apache Kafka Streams** core concepts through a real-world use case.

## 📚 Learning Objectives

This project demonstrates the following **Kafka Streams** concepts:

### Core Components
1. **StreamsBuilder** - Building the processing topology
2. **KStream** - Unbounded event stream
3. **KTable** - Changelog stream / Materialized view
4. **Windowing** - Time-based aggregations (Tumbling Windows)
5. **State Stores** - Persistent state backed by RocksDB
6. **Serdes** - Custom serialization/deserialization
7. **Grouping & Aggregations** - `groupByKey()`, `count()`
8. **Stream Transformations** - `selectKey()`, `map()`, `peek()`

## 🎯 Use Case

**Real-time ad click aggregation**: Count clicks per seller in 5-second tumbling windows.

### Data Flow
```
AdClick Events → KStream → Re-key by seller_id → Group → Window (5s) → Count → KTable → Output
```

## 🏗️ Architecture

```
┌─────────────────┐     ┌──────────────────────┐     ┌─────────────────┐
│  AdClickProducer│────▶│  ad-clicks (topic)   │────▶│  KStream        │
└─────────────────┘     └──────────────────────┘     └────────┬────────┘
                                                              │
                                                              ▼
                                                   ┌──────────────────────┐
                                                   │ selectKey(seller_id) │
                                                   └──────────┬───────────┘
                                                              │
                                                              ▼
                                                   ┌──────────────────────┐
                                                   │  groupByKey()        │
                                                   └──────────┬───────────┘
                                                              │
                                                              ▼
                                                   ┌──────────────────────┐
                                                   │ windowedBy(5s)       │
                                                   └──────────┬───────────┘
                                                              │
                                                              ▼
                                                   ┌──────────────────────┐
                                                   │ count() → KTable     │
                                                   │ State: RocksDB       │
                                                   └──────────┬───────────┘
                                                              │
                                                              ▼
                                         ┌────────────────────────────────┐
                                         │ seller-click-counts (topic)    │
                                         └────────────────────────────────┘
```

## 📋 Prerequisites

- **Java 17+**
- **Maven 3.6+**
- **Apache Kafka 3.x** running on `localhost:9092`

### Start Kafka (if not already running)

```bash
# Using Homebrew (macOS)
brew services start kafka

# Or manually
$KAFKA_HOME/bin/kafka-server-start.sh config/server.properties
```

## 🚀 Quick Start

### Step 1: Create Kafka Topics

```bash
# Input topic for ad clicks
kafka-topics --bootstrap-server localhost:9092 \
  --create --topic ad-clicks \
  --partitions 3 --replication-factor 1

# Output topic for aggregated counts
kafka-topics --bootstrap-server localhost:9092 \
  --create --topic seller-click-counts \
  --partitions 3 --replication-factor 1
```

### Step 2: Build the Project

```bash
mvn clean package
```

### Step 3: Run the Kafka Streams Application

**Terminal 1:**
```bash
mvn exec:java -Dexec.mainClass="com.streams.adclick.AdClickStreamProcessor"
```

You should see:
```
=== Starting Kafka Streams Application ===
Application ID: ad-click-stream-processor
Input Topic: ad-clicks
Output Topic: seller-click-counts
State Store: seller-clicks-store
=========================================
```

### Step 4: Send Test Events

**Terminal 2:**
```bash
mvn exec:java -Dexec.mainClass="com.streams.adclick.AdClickProducer"
```

This sends 50 ad-click events with 200ms delays.

### Step 5: Observe Real-Time Aggregations

In **Terminal 1**, you'll see windowed aggregations:

```
[AGGREGATION] Seller: SellerA | Count: 5 | Window: 2024-11-26T17:21:15Z to 2024-11-26T17:21:20Z
[AGGREGATION] Seller: SellerB | Count: 3 | Window: 2024-11-26T17:21:15Z to 2024-11-26T17:21:20Z
[AGGREGATION] Seller: SellerC | Count: 2 | Window: 2024-11-26T17:21:15Z to 2024-11-26T17:21:20Z
[AGGREGATION] Seller: SellerA | Count: 8 | Window: 2024-11-26T17:21:20Z to 2024-11-26T17:21:25Z
```

### Step 6 (Optional): Consume Output Topic

**Terminal 3:**
```bash
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic seller-click-counts \
  --from-beginning \
  --property print.key=true \
  --property print.timestamp=true
```

Output:
```
SellerA@1732639275000    {"seller_id":"SellerA","count":5,"window_start":1732639275000,"window_end":1732639280000}
SellerB@1732639275000    {"seller_id":"SellerB","count":3,"window_start":1732639275000,"window_end":1732639280000}
```

## 📂 Project Structure

```
src/main/java/com/streams/adclick/
├── AdClickStreamProcessor.java    # Main Streams application
├── AdClickProducer.java            # Test data generator
├── model/
│   └── AdClick.java                # Event model (POJO)
└── serde/
    └── JsonSerde.java              # Custom JSON Serde
```

## 🔑 Key Kafka Streams Concepts Explained

### 1. StreamsBuilder

The entry point for building your processing topology:

```java
StreamsBuilder builder = new StreamsBuilder();
KStream<String, AdClick> stream = builder.stream("ad-clicks");
```

### 2. KStream vs KTable

- **KStream**: Unbounded stream of events (inserts only)
- **KTable**: Changelog stream (upserts - latest value per key)

```java
KStream<String, AdClick> stream = ...;  // Event stream
KTable<String, Long> table = ...;       // Materialized view
```

### 3. Windowing

Group events into time-based windows:

```java
TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(5))  // 5-second tumbling windows
```

**Window Types:**
- **Tumbling**: Fixed, non-overlapping windows
- **Hopping**: Overlapping windows
- **Sliding**: Windows triggered by events
- **Session**: Dynamic windows based on inactivity

### 4. State Stores

Persistent storage backed by RocksDB:

```java
.count(Materialized.as("seller-clicks-store"))
```

Stored in: `/tmp/kafka-streams/{application-id}/{state-store-name}`

### 5. Serdes

Serialization/Deserialization:

```java
JsonSerde<AdClick> serde = new JsonSerde<>(AdClick.class);
Consumed.with(Serdes.String(), serde)
```

### 6. Aggregations

```java
stream
  .groupByKey()           // Group by key
  .windowedBy(windows)    // Apply windowing
  .count()                // Aggregate function
```

## 🛠️ Configuration

Key Kafka Streams configurations in `AdClickStreamProcessor`:

| Property | Value | Description |
|----------|-------|-------------|
| `application.id` | `ad-click-stream-processor` | Consumer group + state dir prefix |
| `bootstrap.servers` | `localhost:9092` | Kafka broker address |
| `auto.offset.reset` | `earliest` | Start from beginning if no offset |
| `commit.interval.ms` | `1000` | Save state every 1 second |
| `state.dir` | `/tmp/kafka-streams` | RocksDB storage location |

## 🧪 Testing & Verification

### Check Topics

```bash
kafka-topics --bootstrap-server localhost:9092 --list
```

### View Internal Topics

Kafka Streams creates internal topics:
```
ad-click-stream-processor-seller-clicks-store-changelog
ad-click-stream-processor-seller-clicks-store-repartition
```

### Reset Application State

```bash
kafka-streams-application-reset \
  --application-id ad-click-stream-processor \
  --bootstrap-servers localhost:9092 \
  --input-topics ad-clicks
```

## 🧹 Clean Up

```bash
# Stop the Streams app (Ctrl+C)

# Delete topics
kafka-topics --bootstrap-server localhost:9092 --delete --topic ad-clicks
kafka-topics --bootstrap-server localhost:9092 --delete --topic seller-click-counts

# Delete internal topics (optional)
kafka-topics --bootstrap-server localhost:9092 --delete \
  --topic ad-click-stream-processor-seller-clicks-store-changelog
```

## 📖 Article Outline Suggestion

1. **Introduction to Kafka Streams**
   - What is stream processing?
   - Kafka Streams vs other frameworks

2. **Core Concepts**
   - StreamsBuilder, KStream, KTable
   - Windowing types
   - State stores

3. **Hands-On: Ad Click Aggregator**
   - Use case overview
   - Topology design
   - Implementation walkthrough

4. **Running the Application**
   - Setup and execution
   - Observing real-time results

5. **Advanced Topics**
   - Exactly-once semantics
   - Interactive queries
   - Testing strategies

## 🔗 References

- [Kafka Streams Documentation](https://kafka.apache.org/documentation/streams/)
- [Kafka Streams JavaDoc](https://kafka.apache.org/37/javadoc/index.html?org/apache/kafka/streams/)
- [Confluent Kafka Streams Tutorial](https://docs.confluent.io/platform/current/streams/tutorial.html)

## 📝 License

MIT License - Free to use for educational and commercial purposes.
