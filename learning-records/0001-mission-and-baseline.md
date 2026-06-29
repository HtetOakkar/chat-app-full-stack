# Established Scaling Mission and Stack Baseline

We established the teaching mission to scale the user's existing Java-based chat application project to handle millions of messages per second. The baseline codebase uses Spring Boot (3.5.6), Java 21, Spring WebSocket, Spring Batch, MySQL (with Flyway), and a single Redis List buffer. Identifying these starting parameters allows us to target specific architectural bottlenecks in Spring Batch and MySQL writes.
