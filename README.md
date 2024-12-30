# EzDepTool

An improvement to my first deployment/packaging system 'dep_system_rabbitmq' created in 2019
This was made to provide some speedup in daily development. While it is not a versioning  system, it also keeps track of bundles for organizational reasons.

This may or may not be of any use to you. I made this to fit my specific needs. 
I wanted a way to write code/develop on one system (Windows) and package a snapshot of my code on to a server (Linux) and automatically deploy that code on the same or other machine for further development or production. 
I also implemented Apache Kafka for robust messaging, this allows you to have separate machines for Kafka, the MySQL server, as well as several dev, qa, and prod machines

Feature list (WIP not all have been implemented):
- Command line tool with arg support for multiple features
- Specify working directory
- Record bundle version
- Transfer bundle to another machine
- Set a "deployment" folder for 'prod' machines to automatically unpack bundles (quick web server deployment)
- Perform all communication through Kafka because why not?
- Hardcode or live set one or more multiple dev/qa/prod machines
- TBD


## Requirements

- MariaDB/MySQL database with configuration to allow remote host access
- Java 21
- Apache Kafka installed on host system


## How to Use

- Install Apache Kafka and move to /opt/kafka directory
- Create /tmp/kraft-combined-logs
- Format storage ./kafka-storage.sh format --config /opt/kafka/config/server.properties --cluster-id $(uuidgen)

(any command with ./ assumes you are in /opt/kafka folder)
- Start Kafka ./kafka-server-start.sh /opt/kafka/config/server.properties
- Create topics
- kafka-topics.sh --create --topic db-queries --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
- kafka-topics.sh --create --topic db-responses --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
- kafka-topics.sh --create --topic deployment --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1

- Run Application
- java -jar ezdeptool.jar dev -m "My first bundle"

## Debugging

- kafka-console-consumer.sh --topic db-queries --from-beginning --bootstrap-server localhost:9092
- kafka-console-consumer.sh --topic db-responses --from-beginning --bootstrap-server localhost:9092
- kafka-console-consumer.sh --topic deployment --from-beginning --bootstrap-server localhost:9092

## MySQL Errors

- Edit  /etc/mysql/mariadb.conf.d/50-server.cnf
- Set bind-address to 0.0.0.0

