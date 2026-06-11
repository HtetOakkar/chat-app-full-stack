# Mission: Scaling a Chat Application to Millions of Messages per Second

## Why
The user is looking to scale their existing chat application ([chatapp](file:///Users/htetoakkar/Projects/chatapp)) to handle millions of messages per second. Gaining this skill will enable them to architect, optimize, and configure production-grade real-time systems that can handle extreme traffic and high-throughput write/read requirements.

## Success looks like
- A clear, production-grade architectural blueprint for scaling the application.
- Optimization of the Java/Spring Boot backend, transitioning from single-node bottlenecks to horizontal scalability.
- Replacing the simple Redis List queue with a high-throughput partitioning solution (e.g., Redis Streams, Kafka, or Pulsar).
- Implementing database optimizations (caching, sharding, or choosing a write-optimized database like Cassandra/ScyllaDB) to handle millions of inserts/sec.
- A local benchmark setup to test and measure bottleneck points in the current setup.

## Constraints
- Focused on the current technology stack: Java 21, Spring Boot, MySQL, Redis.
- Simulated benchmarks and configuration on local machines.

## Out of scope
- Setting up actual production cloud infrastructure (AWS/GCP/Kubernetes) or billing configuration.
- Designing advanced user interface features or styling.
- Non-chat-related backend features (e.g., email notifications, unless they impact message flow performance).
