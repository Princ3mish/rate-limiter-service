# The Rate Limiting Lab: A Spring Boot Sandbox

A lightweight, educational **Rate Limiting microservice** built with **Java 17/21** and **Spring Boot 3.2** [17, 18, 22]. This project serves as an interactive playground to implement, test, and compare industry-standard rate-limiting algorithms by observing their live runtime behaviors and telemetry logs [18, 22].

Rather than reading dry theoretical docs, **The Rate Limiting Lab** lets you trigger programmatic request bursts, observe how different filters shield your endpoints, and study chronological transaction timelines in real time [19, 22].

---

## 🎯 Core Learning Goal

> **"If I send requests in a certain pattern, how does each rate limiting algorithm respond — and why?"** [19, 23]

### How Learning Happens Here
Each algorithm exposes a **dedicated trigger endpoint** in the simulator [6, 19, 23]. When triggered, the system:
1. **Simulates programmatic request patterns** using a configurable test harness [6, 20, 23].
2. **Intercepts traffic at the filter level** to mimic professional gateway architectures [3, 4, 12].
3. **Logs outcomes chronologically** and outputs a structured visual timeline showing exactly where requests succeeded, where they were throttled, and when they recovered [20, 23].

---

## 🧠 Algorithms Covered

This playground implements three classic, production-grade rate-limiting strategies in pure Java:

### 1. Token Bucket (`TokenBucketRateLimiterService`)
* **How it works:** A bucket holds a maximum number of tokens (capacity) [11, 15]. Each request consumes one token. Tokens are refilled at a constant rate over time [15]. If the bucket is empty, requests are blocked with HTTP `429 Too Many Requests`.
* **Our Implementation:** Built using **Lazy Refills** [15]. Instead of running heavy background timer threads, the service calculates how many tokens should have accumulated since the user's last request timestamp (`now - lastRefillTimestamp`) and refills them on-the-fly when a new request arrives.

### 2. Fixed Window (`FixedSizeRateLimiterService`)
* **How it works:** Time is divided into rigid, static intervals (windows), such as non-overlapping 10-second blocks [11, 13, 14]. Each user gets a maximum request count per window. Crossing into a new window instantly resets the counter to zero.
* **Our Implementation:** Simple, deterministic tracker using active window boundaries. It is lightweight but demonstrates the classic "boundary burst" flaw (where a user can double their limit by bursting at the edge of a window transition).

### 3. Sliding Window (`SlidingWindowRateLimiterService`)
* **How it works:** To resolve the boundary-bursting issue of Fixed Window, this algorithm uses a dynamic, rolling window relative to the exact millisecond of the current request.
* **Our Implementation:** Built using an in-memory **Double-Ended Queue (Deque)** to log the timestamp of every successful request [11, 14]. When a request arrives, older timestamps outside the current sliding window boundary (`now - windowSize`) are pruned, and capacity is evaluated based on the remaining active log size [14].

---

## 🏗️ Architectural Design & Separation of Concerns

This project is built following strict software design principles to separate infrastructure concerns from core business logic [4, 5]:

* **Filter Interception (`RateLimiterFilter`):** We use a `OncePerRequestFilter` to intercept and rate-limit API calls *before* they ever reach our controllers [3, 11, 12]. This mirrors production environments where rate limiters sit at the API Gateway or Middleware layer [3, 4].
* **Separation of Concerns:** 
  * **Algorithms (`service/`):** Maintain the pure mathematical rate-limiting rules [5, 12].
  * **Controllers (`controller/`):** Expose guarded resource endpoints (`/limiter/api/check`) and simulator controls [5, 9, 10].
  * **Executors / Simulators (`DemoRunController`):** Run prebuilt burst patterns using `RestTemplate` to test the system [5, 8, 9].
  * **Timeline Recorder (`helper/ResponseTextHelper`):** Translates raw transaction decisions into a clear chronological JSON timeline [5, 7, 9].

---

## 📂 Directory Structure

```text
.github/
└── workflows/
    └── docker-publish.yml        # CI/CD pipeline to compile & publish images to GHCR
src/
├── main/
│   └── java/
│       └── com/project/rate_limiter/
│           ├── constants/
│           │   └── RateLimiterAlgorithm.java
│           ├── controller/
│           │   ├── dto/
│           │   │   ├── DemoEvent.java
│           │   │   ├── DemoRunRequest.java
│           │   │   └── DemoRunResponse.java
│           │   ├── DemoRunController.java
│           │   └── RateLimiterController.java
│           ├── entity/
│           │   ├── RateLimiterDecision.java
│           │   ├── TokenBucket.java
│           │   └── UserRequestInfo.java
│           ├── filter/
│           │   └── RateLimiterFilter.java
│           ├── helper/
│           │   └── ResponseTextHelper.java
│           ├── service/
│           │   ├── FixedSizeRateLimiterService.java
│           │   ├── SlidingWindowRateLimiterService.java
│           │   └── TokenBucketRateLimiterService.java
│           ├── RateLimiterApplication.java
│           └── SecurityConfig.java
│   └── resources/
│       └── application.properties # Server port, swagger pathways & algorithm thresholds
└── test/
    └── java/
        └── com/project/rate_limiter/
            └── RateLimiterApplicationTests.java
```

---

## 🛠️ Installation & Setup

### Prerequisites
* **Java SDK 17 or 21**
* **Maven 3.8+**
* **Docker** (Optional, for containerized execution)

### 1. Build and Run Locally
Clone the repository and run the application using Maven:
```bash
# Compile and build the project
mvn clean install

# Launch the Spring Boot application
mvn spring-boot:run
```

The application will start up on port `8080` by default.

---

## 🚀 How to Use the Playground

### 1. Open Swagger UI
Navigate to your browser to access the interactive OpenAPI documentation:
👉 **`http://localhost:8080/swagger-ui/index.html`** [16]

### 2. Trigger a Simulator Run
Expand the **Demo Playground** tag, select an algorithm scenario (e.g., `POST /limiter/demo/token-bucket`), and click **"Try it out"** [9, 20, 24]. Pass a unique user ID:
```json
{
  "userId": "learning-sandbox-user"
}
```
Click **Execute** [20].

### 3. Read the Telemetry Timeline
The system returns a detailed chronological response log showing exactly how the algorithm reacted [20, 23]:

```json
{
  "algorithm": "TOKEN_BUCKET",
  "summary": {
    "allowed": 6,
    "blocked": 3,
    "config": {
      "refillRatePerSec": 1,
      "capacity": 5
    }
  },
  "timeline": [
    { "status": 200, "remaining": 4 },
    { "status": 200, "remaining": 3 },
    { "status": 200, "remaining": 2 },
    { "status": 200, "remaining": 1 },
    { "status": 200, "remaining": 0 },
    { "status": 429, "retryAfterMs": 1000 },
    { "status": 429, "retryAfterMs": 1000 },
    { "comment": "triggered calls at 1.5 sec interval", "event": "MARKER" },
    { "status": 200, "remaining": 0 }
  ]
}
```

#### Timeline Key Guide:
* **`status: 200`** ➔ The request successfully bypassed the filter [25].
* **`status: 429`** ➔ The rate limit was exceeded; the request was blocked [25].
* **`remaining`** ➔ Active remaining request capacity at that moment [25].
* **`retryAfterMs`** ➔ Time (in milliseconds) the client must wait before making another request [25].
* **`MARKER`** ➔ Highlights when the simulator paused (e.g., 1.5 seconds) to demonstrate token refills or window resets [25].

---

## 🐳 Containerization with Docker

You can package and run the entire playground in a completely isolated environment without needing Maven or Java pre-installed.

### Use Docker Compose
Spin up the service with a single command:
```bash
docker compose up --build
```
This builds the multi-stage, production-secure Docker image (caching Maven dependencies and compiling the app under JRE 17) and forwards port `8080` to your host machine [17, 18].

---

## 🤖 CI/CD Build & Publish Pipeline

This project includes an automated **GitHub Actions Workflow** (`docker-publish.yml`) [2, 3]. 

Whenever you push a semantic version tag (e.g., `v1.0.0`):
1. GitHub spins up a secure runner environment [3].
2. It compiles and packages the Spring Boot application using Maven [17].
3. It packages the final artifact inside a lightweight JRE runtime container [17, 18].
4. It signs and publishes the compiled image straight to the **GitHub Container Registry (GHCR)** [3], ready to be pulled and run anywhere.
