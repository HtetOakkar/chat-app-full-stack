# Batch Processor Optimization for Bulk Lookups

We successfully refactored the Spring Batch message pipeline in `BatchConfig.java` to eliminate database query amplification (N+1 queries). By moving user mapping into the `ItemWriter` and using a bulk query (`findAllById`), we reduced database SELECT operations per chunk from 200 down to 1. All unit and integration tests continue to pass.
