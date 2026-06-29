# Bottleneck Recognition and Chunking Proposal

The user successfully identified the database SELECT queries inside the Spring Batch `ItemProcessor` in `BatchConfig.java`. They proposed retrieving messages from the Redis list `chat_messages` batch-by-batch instead of reading all messages at once, resolving the potential OOM (Out Of Memory) issue during list fetches.
