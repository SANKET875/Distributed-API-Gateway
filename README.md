# Reactive Distributed API Gateway & Rate Limiter

A high-performance, asynchronous API Gateway built with **Spring WebFlux** that serves as a secure reverse proxy layer for microservice backends. The system integrates a distributed, thread-safe **Token Bucket** rate-limiting mechanism powered by an atomic **Lua Script** running inside a centralized **Redis (Valkey)** data store.

---

## 🏗️ System Architecture & Request Lifecycle

This gateway abstracts internal microservice topology from external clients by running on port `8089` and handling downstream proxy routing to the core business service running on port `8080`.

1. **Ingress:** An incoming HTTP request hits the embedded non-blocking **Netty** web server.
2. **Interception:** The packet is routed into a custom `WebFilter` pipeline where the client's unique IP address is extracted dynamically.
3. **Distributed Evaluation:** The gateway schedules a non-blocking network execution call to the centralized **Redis** tier, firing a custom Lua script.
    * **Math Execution:** The script computes a lazy-refill calculation matching the elapsed time clock.
    * **Atomic Logic:** If tokens are available, they are decremented and a `1` (Allowed) is returned. If depleted, the state updates without deduction and a `0` (Blocked) is returned. Both paths refresh a 1-hour **Time-To-Live (TTL)** expiration window to prevent server memory leaks.
4. **Reactive Routing Fork (`flatMap`):**
    * **Green Light (Allowed):** A reactive `WebClient` instance duplicates request verbs, headers, and body payloads, proxying the request downstream to the journaling backend.
    * **Red Light (Blocked):** The reactive chain halts immediately, mutating the HTTP response header to a strict `429 Too Many Requests` status, dropping the execution context before it ever impacts backend resources.

---

## 🛠️ Tech Stack & Prerequisites

* **Java 21**
* **Spring Boot 3.x** (Spring WebFlux, Reactive Spring Data Redis)
* **Project Reactor** (Asynchronous, Non-blocking Streams: `Mono`/`Flux`)
* **Redis / Valkey** (In-Memory Centralized Data Structure Store)
* **Lua Scripting** (Atomic Database-Side Calculations)
* **Maven** (Project Lifecycle & Dependency Management)
