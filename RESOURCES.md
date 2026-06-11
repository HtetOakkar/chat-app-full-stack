# Scaling a Chat Application Resources

## Knowledge

- [System Design Interview — Chat System by Alex Xu](https://bytebytego.com/)
  Highly recommended foundational blueprint detailing WebSocket connections, presence servers, message routing via brokers, and NoSQL databases for message history.
- [Medium: Designing a Chat Application at Scale](https://medium.com/)
  Practical implementation guides discussing WebSocket scaling issues (epoll vs. select, file descriptors), load balancer configurations, and message delivery guarantees (at-least-once).
- [Cassandra/ScyllaDB Architecture for High-Write Workloads](https://www.scylladb.com/)
  Explains LSM-tree engine mechanics showing why wide-column stores outperform relational DBs like MySQL for message log writes.
- [Redis Streams Documentation](https://redis.io/docs/data-types/streams/)
  Details the append-only log data structure in Redis, providing consumer groups and partitioning logic.

## Wisdom (Communities)

- [r/systemdesign](https://www.reddit.com/r/systemdesign/)
  A active community of system architects and engineers discussing architectural designs, scaling trade-offs, and scaling bottlenecks.
- [r/java](https://www.reddit.com/r/java/)
  Useful for Spring Boot WebSocket scaling discussions, virtual threads (Project Loom) tuning, and Netty performance optimization.
