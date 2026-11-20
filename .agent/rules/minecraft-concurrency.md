# Minecraft concurrency rules

Paper, Folia, and Velocity run events and database operations across thread pools.

## Collections across asynchronous tasks
Do not mutate standard `ArrayList` or `HashMap` instances across multiple concurrent `CompletableFuture` callbacks or async threads. Use thread-safe alternatives:
1. `ConcurrentHashMap` for key-value maps.
2. `ConcurrentHashMap.newKeySet()` for sets.
3. `Collections.synchronizedList(new ArrayList<>())` or pre-sized arrays for indexed output lines.

## Chronological output ordering
When querying database rows ordered by time (`ORDER BY start_time DESC`) and mapping each row asynchronously:
1. Allocate an array matching the result size (`String[] lines = new String[results.size()]`).
2. Assign each completed item to its index (`lines[index] = renderedLine`).
3. Send lines to the recipient only after `CompletableFuture.allOf` resolves.
This prevents lines from displaying out of chronological order.

## Date formatting
Never use static instances of `java.text.SimpleDateFormat`. `SimpleDateFormat` stores mutable calendar state and corrupts date strings under concurrent execution. Use immutable `java.time.format.DateTimeFormatter` with `ZoneId.systemDefault()`.
