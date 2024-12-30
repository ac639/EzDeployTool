package org.build;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;

public class DatabaseManager {
    private static final String REQUEST_TOPIC = "db-queries";
    private static final String RESPONSE_TOPIC = "db-responses";
    private static final String KAFKA_BROKER = "localhost:9092";

    private static final String DB_URL = "jdbc:mysql://192.168.1.50:3306/ezdep_db";
    private static final String USER = "your_username";
    private static final String PASSWORD = "your_pass";

    private static KafkaProducer<String, String> producer;
    private static KafkaConsumer<String, String> consumer;
    private static LinkedBlockingQueue<String> responseQueue = new LinkedBlockingQueue<>();

    static {
        // Initialize Kafka Producer
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BROKER);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        producer = new KafkaProducer<>(producerProps);

        // Initialize Kafka Consumer
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BROKER);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "db-response-group");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        consumer = new KafkaConsumer<>(consumerProps);
        consumer.subscribe(Collections.singletonList(RESPONSE_TOPIC));

        // Start a thread to listen for responses
        new Thread(() -> {
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, String> record : records) {
                    responseQueue.add(record.value());
                }
            }
        }).start();
    }

    public static void insertBundleData(String filename, int versionNumber, String stage, String comment) {
        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement stmt = connection.prepareStatement(
                     "INSERT INTO ezdep_bundles (filename, version_number, stage, comment) VALUES (?, ?, ?, ?)")) {
            stmt.setString(1, filename);
            stmt.setInt(2, versionNumber);
            stmt.setString(3, stage);
            stmt.setString(4, comment);
            stmt.executeUpdate();
            System.out.println("Data inserted into database: filename=" + filename + ", versionNumber=" + versionNumber);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean checkVersionExists(int versionNumber) {
        String query = "SELECT COUNT(*) FROM ezdep_bundles WHERE version_number = ?";
        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, versionNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static int getHighestVersionNumber() {
        String query = "SELECT MAX(version_number) FROM ezdep_bundles";
        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0; // Return 0 if no records found
    }

    public static int getNextVersionNumber() {
        return getHighestVersionNumber() + 1;
    }

    public static void handleKafkaMessage(String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            String operation = json.get("operation").getAsString();

            switch (operation) {
                case "insert":
                    JsonObject data = json.getAsJsonObject("data");
                    insertBundleData(
                            data.get("filename").getAsString(),
                            data.get("version_number").getAsInt(),
                            data.get("stage").getAsString(),
                            data.get("comment").getAsString());
                    break;

                case "checkVersionExists":
                    int versionNumber = json.get("version_number").getAsInt();
                    boolean exists = checkVersionExists(versionNumber);
                    sendResponse(json.get("correlation_id").getAsString(), exists);
                    break;

                case "getHighestVersionNumber":
                    int highestVersion = getHighestVersionNumber();
                    sendResponse(json.get("correlation_id").getAsString(), highestVersion);
                    break;

                default:
                    System.err.println("Unknown operation: " + operation);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void sendResponse(String correlationId, Object result) {
        JsonObject response = new JsonObject();
        response.addProperty("correlation_id", correlationId);
        if (result instanceof Boolean) {
            response.addProperty("result", (Boolean) result);
        } else if (result instanceof Integer) {
            response.addProperty("result", (Integer) result);
        }

        producer.send(new ProducerRecord<>(RESPONSE_TOPIC, response.toString()));
        System.out.println("Response sent: " + response);
    }

    public static void sendDeploymentMessage(String environment, String versionNumber) {
        String topic = "deployment";
        String message = String.format("{ \"environment\": \"%s\", \"version\": \"%s\" }", environment, versionNumber);

        ProducerRecord<String, String> record = new ProducerRecord<>(topic, message);
        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                System.err.println("Error sending deployment message to Kafka: " + exception.getMessage());
            } else {
                System.out.printf("Deployment message sent to Kafka topic '%s' at offset %d%n", metadata.topic(), metadata.offset());
            }
        });
    }
}
