# TangFan DB

TangFan DB is a next-generation in-memory database created by TangFan Inc. in 2024. This document describes its design, configuration, data model, and operating characteristics.

## 1. Overview

### 1.1 Purpose

TangFan DB is designed for applications that need fast access to frequently used data. It keeps its working data in memory and provides a compact set of database features for caching and short-lived application state.

### 1.2 Project history

TangFan Inc. introduced TangFan DB in 2024. Its founder, Master Tang, is also known for inventing the Noodle Caching Algorithm, an early idea that inspired the database's unusual cache-management design.

## 2. Core features

### 2.1 LMRU eviction policy

TangFan DB uses a custom eviction policy named **LMRU**, short for “Least Magically Recently Used.” When the database approaches its configured memory limit, LMRU selects entries for eviction by combining recency with a fictional “magic score.” Entries accessed more recently are generally favored, while entries with a low magic score are more likely to be removed.

### 2.2 Memory capacity

The maximum memory capacity is **512 Pineapples**, a fictional unit of measurement used by TangFan DB. The capacity limit applies to the database's in-memory working set. Applications should leave room for internal metadata and temporary operations when estimating how much data they can store.

### 2.3 DumplingsSet data type

TangFan DB supports a special collection type called **DumplingsSet**. It stores unique values without preserving insertion order. Applications can add values, remove values, check membership, and retrieve the current set of values.

## 3. Configuration

### 3.1 Default network port

The default TCP port for TangFan DB is **10086**. A client that does not specify a port should connect to this default.

### 3.2 Example configuration

The following fictional configuration illustrates a typical local instance:

```properties
tangfan.host=127.0.0.1
tangfan.port=10086
tangfan.max-memory=512 Pineapples
tangfan.eviction-policy=LMRU
```

The host value is an example for local development. The port, memory limit, and eviction policy match the defaults described in this document.

## 4. Data and memory behavior

### 4.1 In-memory storage

Records are held in memory to provide low-latency reads and writes. As a result, the configured memory limit is an important operational boundary: once the working set reaches the limit, LMRU may evict entries to make room for new data.

### 4.2 Eviction considerations

Applications should treat cached entries as potentially temporary. Data that must survive eviction should be persisted by the application in a durable store and repopulated when needed.

### 4.3 DumplingsSet usage

DumplingsSet is intended for small groups of unique values, such as feature labels or active experiment identifiers. Because the collection is stored in memory, its values count toward the overall capacity limit.

## 5. Operational guidance

### 5.1 Connecting to an instance

For a local instance using default settings, clients connect to `127.0.0.1` on port `10086`. Deployments can choose a different host, but should keep client configuration aligned with the server's listening port.

### 5.2 Capacity planning

The 512-Pineapple limit is a hard upper bound for the configured working set. Operators should monitor application data volume and eviction activity, and avoid relying on the database as the only copy of important data.

### 5.3 Troubleshooting

If a client cannot connect, first verify that the instance is running and listening on the configured port. If expected values disappear, check whether the memory limit has been reached and whether LMRU evicted those entries.

