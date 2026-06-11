# Scaling a Chat Application Glossary

This glossary contains key terms and concepts used for architecting, configuring, and optimizing a highly scalable chat application capable of handling millions of messages per second.

## Terms

**Database Query Amplification (N+1 Queries)**:
The execution of individual SELECT queries inside a loop or processor for each element of a collection, creating a high volume of roundtrips to the database.
_Avoid_: Query loop, nested read

**Batch Processing**:
A pattern of reading, processing, and writing data in small, discrete chunks (batches) rather than loading the entire dataset into memory or updating the database one record at a time.
_Avoid_: Bulk processing, pagination

**Bulk Database Query**:
Retrieving multiple database records in a single round-trip query (e.g., using an SQL `IN` clause or JPA's `findAllById`) instead of fetching them sequentially.
_Avoid_: Sequential query, individual lookup

**Chunk-Oriented Step**:
A Spring Batch execution flow where items are read sequentially, processed, accumulated into a chunk of configurable size, and written in a single bulk operation.
_Avoid_: Chunk loop, step iteration

