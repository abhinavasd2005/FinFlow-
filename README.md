# FinFlow 💸

> **Production-grade payment processing backend** built with Java Spring Boot — demonstrating real-world concurrency safety, fraud detection, and financial transaction integrity.

![Java](https://img.shields.io/badge/Java_17-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_3.2-6DB33F?style=flat&logo=springboot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL_8.0-4479A1?style=flat&logo=mysql&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-000000?style=flat&logo=jsonwebtokens&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-C71A36?style=flat&logo=apachemaven&logoColor=white)

---

## Overview

FinFlow is a wallet-based payment processing system that simulates the core backend of a real fintech product. It handles concurrent money transfers between users, enforces financial correctness under high load, and detects suspicious activity asynchronously without blocking transactions.

The project was built to demonstrate backend engineering depth — specifically concurrency patterns, transactional safety, and system design — using Java 17, Spring Boot, and MySQL as an OLTP system.

A minimal HTML/CSS/JavaScript frontend provides a working interface for users and a dedicated admin panel for fraud monitoring.

---
## 🔗 Live Demo

| Service | URL |
|---|---|
| Frontend | https://finflow-frontend-m169.onrender.com |
| Backend API | https://finflow-backendapp.onrender.com |


## Key Features

### User Features
- Register and login with JWT-based authentication
- Create multiple wallets per account with custom names and daily limits
- Transfer funds to any wallet (own or another user's) by wallet number
- Real-time balance checks before every transfer
- View full transaction history with date range filtering
- Generate a detailed transfer slip/receipt for every transaction (success or failure)

### Admin Features
- Dedicated admin dashboard with live system metrics
- View all fraud alerts with PENDING / REVIEWED / DISMISSED status tabs
- Review or dismiss individual fraud alerts
- Manually freeze and unfreeze any wallet
- Monitor fraud queue size and transfer throughput in real time

### Advanced Backend Features
- Optimistic locking for normal transfers (JPA `@Version`)
- Pessimistic locking (`SELECT FOR UPDATE`) for high-value transfers above $10,000
- Deadlock prevention via consistent lock ordering (`Math.min / Math.max` on wallet IDs)
- Semaphore-based throttling — max 3 concurrent operations per wallet
- ReentrantLock for in-memory account freeze management
- Idempotency key protection — duplicate requests return cached response, never double-process
- Async fraud detection using `CompletableFuture` — runs after transfer commit, never blocks response
- `BlockingQueue` fraud review pipeline — suspicious transactions queued for processing
- `AtomicLong` counters for lock-free real-time transfer metrics
- `ThreadPoolTaskExecutor` with separate pools for transfers, fraud, and notifications
- Double-entry bookkeeping — every transfer creates a DEBIT and CREDIT ledger entry
- Auto wallet freeze when fraud score exceeds 70/100

---

## Tech Stack

### Backend
| Technology | Purpose |
|---|---|
| Java 17 | Core language |
| Spring Boot 3.2 | Application framework |
| Spring MVC | REST API layer |
| Spring Security | Authentication and role-based access |
| Spring Data JPA / Hibernate | ORM and database access |
| Spring Retry | Retry on optimistic lock failure (up to 3 attempts) |
| Spring Async | Background thread execution |
| Maven | Build and dependency management |

### Frontend
| Technology | Purpose |
|---|---|
| HTML5 | Page structure |
| CSS3 | Styling (dark theme, responsive grid) |
| Vanilla JavaScript | API calls, DOM manipulation, JWT handling |

### Database
| Technology | Purpose |
|---|---|
| MySQL 8.0 | Primary OLTP database |

### Security
| Technology | Purpose |
|---|---|
| JWT (HS256) | Stateless authentication |
| BCrypt | Password hashing (10 rounds) |
| Role-based access | USER and ADMIN roles enforced at endpoint level |

### Testing
| Tool | Purpose |
|---|---|
| Postman | Manual API testing |
| Apache JMeter | Concurrency and load testing |

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        FRONTEND                             │
│   HTML/CSS/JS (User)          HTML/CSS/JS (Admin)           │
│   dashboard, transfer,        admin-dashboard,              │
│   history, slip               admin-fraud                   │
└──────────────────┬─────────────────────┬────────────────────┘
                   │  HTTP / REST        │
                   ▼                     ▼
┌─────────────────────────────────────────────────────────────┐
│                   REST API LAYER                            │
│  AuthController  WalletController  TransferController       │
│  TransactionController           FraudController            │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                  SERVICE LAYER                              │
│  AuthService      WalletService     TransferService         │
│  TransactionService  FraudDetectionService  LedgerService   │
│  NotificationService                                        │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │            CONCURRENCY LAYER                        │   │
│  │  WalletLockManager   SemaphoreManager               │   │
│  │  AccountFreezeManager   TransferMetrics             │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │            ASYNC / FRAUD LAYER                      │   │
│  │  FraudRulesEngine   FraudScoreCalculator            │   │
│  │  FraudQueueProcessor (BlockingQueue)                │   │
│  │  AsyncConfig (ThreadPoolTaskExecutor)               │   │
│  └─────────────────────────────────────────────────────┘   │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                REPOSITORY LAYER                             │
│  UserRepository  WalletRepository  TransactionRepository    │
│  LedgerEntryRepository  FraudAlertRepository                │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                  MySQL DATABASE                             │
│  users  wallets  transactions  ledger_entries  fraud_alerts │
└─────────────────────────────────────────────────────────────┘
```

### Async Fraud Processing Flow
```
Transfer COMPLETED
        │
        ▼
CompletableFuture.runAsync()  ──────────────────────────────┐
        │                                                    │
        ▼  (fraudExecutor thread pool)                       ▼ (notificationExecutor)
FraudRulesEngine.evaluate()                        NotificationService.send()
        │                                                    │
        ▼                                                    ▼
FraudScoreCalculator                              LOG: Transaction alert
        │
        ▼
Score >= 40 ?
  YES → FraudQueueProcessor.enqueue()
            │
            ▼
        BlockingQueue (capacity 1000)
            │
            ▼  (background processor thread)
        FraudAlert saved to DB
        Transaction fraud_score updated
            │
Score >= 70 ?
  YES → AccountFreezeManager.freezeWallet()
        LOG: Auto-freeze triggered
```

### Admin Fraud Monitoring Flow
```
Admin Login → JWT with ROLE_ADMIN
        │
        ▼
admin-dashboard.html
  │── GET /api/transfers/metrics  → live TPS, volumes, fraud count
  │── GET /api/fraud/alerts       → pending alerts preview
  └── GET /api/fraud/queue/size   → queue depth

admin-fraud.html
  │── GET  /api/fraud/alerts              → all alerts by status
  │── PATCH /api/fraud/alerts/{id}/review → mark reviewed
  │── PATCH /api/fraud/alerts/{id}/dismiss→ dismiss alert
  │── POST /api/fraud/freeze/{walletId}   → freeze wallet
  └── POST /api/fraud/unfreeze/{walletId} → unfreeze wallet
```

---

## How The System Works

### 1. User Registration and Login
A user registers with username, email, and password. The password is BCrypt-hashed before storage. On successful registration or login, a signed JWT token (HS256, 1hr expiry) is returned and stored in `systemStorage`. All subsequent API calls include this token in the `Authorization: Bearer` header.

Admin accounts are created via a separate protected endpoint using an admin secret key. On login, the role (`USER` or `ADMIN`) is embedded in the JWT response and used for client-side routing.

### 2. Role-Based Access
Spring Security enforces roles at the endpoint level:
- `USER` — can access wallet, transfer, and transaction endpoints
- `ADMIN` — additionally accesses fraud and metrics endpoints

On the frontend, role is read from `sessionStorage` after login. A `USER` visiting admin pages is redirected. An `ADMIN` is redirected away from user dashboard pages.

### 3. Wallet Creation
A user can create multiple wallets. Each wallet gets a unique wallet number (`WLT-XXXXXXXX`), an initial balance, and a configurable daily transfer limit. Wallets belong to a user via a foreign key relationship.

### 4. Transfer Flow

```
POST /api/transfers
        │
        ├─ 1. Idempotency check    (return cached if key exists)
        ├─ 2. Request validation   (amount > 0, wallets differ)
        ├─ 3. Ownership check      (sender owns from_wallet)
        ├─ 4. Freeze check         (wallet not frozen)
        ├─ 5. Semaphore acquire    (max 3 concurrent per wallet)
        ├─ 6. Ordered lock         (deadlock prevention)
        ├─ 7. @Transactional begin (READ_COMMITTED isolation)
        │       ├─ Lock strategy   (pessimistic if > $10,000)
        │       ├─ Balance check
        │       ├─ Daily limit check
        │       ├─ Debit from_wallet
        │       ├─ Credit to_wallet
        │       ├─ Save Transaction (PENDING → COMPLETED)
        │       └─ Create 2 LedgerEntry records
        ├─ 8. @Transactional commit
        ├─ 9. Async: fraud analysis + notification
        └─10. Return TransferResponse (slip data)
```

On any failure, the transaction is rolled back and a FAILED transaction record is saved with the failure reason.

### 5. Transaction History and Slip Generation
Users can view all transactions for a wallet, or filter by date range. The statement endpoint returns a summary (total debited, credited, count) alongside the transaction list. Clicking any transaction opens a detailed transfer slip showing wallet names, owner usernames, reference number, amount, status, and balance after transfer — printable as PDF.

### 6. Fraud Scoring
After each successful transfer, fraud analysis runs asynchronously on a dedicated thread pool. Five rules are evaluated:

| Rule | Trigger | Score |
|---|---|---|
| Large Amount | > 10× wallet average | 30 |
| Velocity | > 5 transfers in 60 seconds | 25 |
| Odd Hours | 1AM–4AM and amount > $5,000 | 20 |
| New Account | Account < 7 days and amount > $1,000 | 20 |
| Round Number | Exactly $500, $1K, $5K, $10K | 10 |

Score ≥ 40 → FraudAlert created, transaction queued for review.  
Score ≥ 70 → Wallet auto-frozen immediately.

### 7. Wallet Freeze and Admin Review
Wallets can be frozen automatically by the fraud engine or manually by an admin. A frozen wallet returns an immediate error on any transfer attempt — checked before any locking occurs. Admins can unfreeze wallets and review or dismiss fraud alerts via the admin panel.

---

## Concurrency and Safety

FinFlow handles multiple threads hitting the same wallet simultaneously using a layered defence:

### Layer 1 — Semaphore (Application Level)
```java
// Max 3 concurrent operations per wallet
Semaphore semaphore = new Semaphore(3, true); // fair
semaphore.tryAcquire(5, TimeUnit.SECONDS);
```
Prevents a single wallet from being hammered by hundreds of simultaneous requests.

### Layer 2 — Ordered ReentrantLock (Application Level)
```java
// Always lock lower wallet ID first — eliminates circular wait
Long firstId  = Math.min(fromWalletId, toWalletId);
Long secondId = Math.max(fromWalletId, toWalletId);
walletLockManager.lockOrdered(firstId, secondId);
```
Prevents deadlocks. Thread 1 and Thread 2 always acquire locks in the same order.

### Layer 3 — Optimistic Locking (Database Level)
```java
@Version
private Long version; // JPA checks version on every UPDATE
// UPDATE wallets SET balance=?, version=version+1 WHERE id=? AND version=?
```
Used for normal transfers. If two threads read the same version and both try to write, one fails and retries (up to 3 times with exponential backoff).

### Layer 4 — Pessimistic Locking (Database Level)
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT w FROM Wallet w WHERE w.id = :id")
Wallet findByIdWithPessimisticLock(Long id);
// Becomes: SELECT ... FOR UPDATE
```
Used for high-value transfers (> $10,000). Blocks other transactions at the database level until the current one commits.

### Layer 5 — Idempotency
```java
Optional<Transaction> existing =
    transactionRepository.findByIdempotencyKey(key);
if (existing.isPresent()) return cachedResponse; // never double-process
```
Network retries from the client never result in duplicate transfers.

### Layer 6 — @Transactional Boundary
The entire transfer — balance deduction, wallet credit, ledger entries, status update — executes within a single `@Transactional(isolation = READ_COMMITTED)` boundary. Any exception triggers a full rollback.

---

## Testing and Validation

### Manual API Testing — Postman
All endpoints were manually tested using Postman:

| Test | Verified |
|---|---|
| Register / Login (USER and ADMIN) | ✅ |
| JWT token validation and expiry | ✅ |
| Wallet creation and balance check | ✅ |
| Cross-user wallet transfer | ✅ |
| Insufficient balance rejection | ✅ |
| Daily limit enforcement | ✅ |
| Fraud rules triggered individually | ✅ |
| Wallet freeze / unfreeze | ✅ |
| Admin alert review and dismiss | ✅ |
| Idempotency key deduplication | ✅ |

### Concurrency and Load Testing — Apache JMeter

Six JMeter test plans were executed. Screenshots of results are included in the `/jmeter-results` folder.

| Test | Description | Result |
|---|---|---|
| Same Wallet Concurrent Transfer | Multiple threads sending from one wallet simultaneously | Zero balance inconsistency |
| Opposite Direction Deadlock Test | Thread A: Wallet1→Wallet2, Thread B: Wallet2→Wallet1 simultaneously | No deadlocks — ordered locking held |
| Idempotency Safety Under Load | Same idempotency key sent by 50 concurrent threads | Exactly 1 transaction created |
| Frozen Wallet Transfer Blocked | Transfers to frozen wallet under concurrent load | 100% rejected correctly |
| Parallel Transfer Test | Multiple wallets transferring simultaneously | All balances correct after completion |
| Simple Load Test | High throughput baseline — transactions per second | Measured TPS recorded |

---

## 🚀 Deployment

| Component | Platform | Details |
|---|---|---|
| Backend API | Render | Docker container, free tier, Singapore region |
| Frontend | Render | Static site, global CDN |
| Database | Railway | MySQL 8.0, Southeast Asia region |


---

## 🐳 Deployment Architecture & Techniques

### Containerization with Docker
The Spring Boot backend is fully containerized using Docker. Rather than deploying a raw JAR file, the entire application — including its Java runtime — is packaged into a portable container image that runs identically in any environment.

### Multi-Stage Docker Build
A multi-stage build pattern is used to keep the final image lean:

```dockerfile
# Stage 1 — Build: Maven + JDK compiles the source into a JAR
FROM maven:3.9.6-eclipse-temurin-17 AS build

# Stage 2 — Run: Only the JRE is included, Maven is discarded
FROM eclipse-temurin:17-jre
```

**Why this matters:** The build stage uses a full Maven + JDK image (~500MB). The runtime stage uses only a JRE (~200MB). Maven is not needed at runtime so it is discarded — resulting in a smaller, faster, more secure production image. This is an industry best practice for Java deployments.

### Environment-Based Configuration
All sensitive credentials — database host, port, username, password, JWT secret — are injected at runtime via environment variables. No secrets are hardcoded or committed to GitHub.

```properties
spring.datasource.url=jdbc:mysql://${MYSQLHOST}:${MYSQLPORT}/${MYSQLDATABASE}
spring.datasource.username=${MYSQLUSER}
spring.datasource.password=${MYSQLPASSWORD}
jwt.secret=${JWT_SECRET}
```

The application follows several *12-Factor App principles* — 
environment-based configuration, explicit dependency declaration 
via Maven, stateless processes with JWT, and separated build/run 
stages via Docker multi-stage builds.

### Automated Deployment (CD)
Both Render services are connected directly to the GitHub repository. 
Every `git push` to the `main` branch automatically triggers a new 
deployment on Render — no manual steps required. This is a basic 
continuous deployment setup.

### Static Site Deployment
The HTML/CSS/JS frontend requires no build step and is deployed as a static site on Render's global CDN — meaning it is served from edge locations worldwide for fast load times regardless of the user's location.

### Cross-Origin Resource Sharing (CORS)
Since the frontend and backend run on different domains, CORS is configured in Spring Boot to explicitly allow requests from the frontend origin — a standard security requirement for any separated frontend/backend architecture.

### Database as a Service (DBaaS)
MySQL is provisioned on Railway as a managed database service. The backend connects over a public TCP proxy, Railway's public networking endpoint — since the backend and database run on different cloud platforms and cannot communicate over a private network.

## API Endpoints

### Auth
```
POST /api/auth/register          Register a new user
POST /api/auth/login             Login and receive JWT
POST /api/auth/register/admin    Create admin account (requires secret)
```

### Wallets
```
POST   /api/wallets              Create a new wallet
GET    /api/wallets/my-wallets   Get all wallets for logged-in user
GET    /api/wallets/{id}         Get wallet by ID
GET    /api/wallets/{id}/balance Get wallet balance
GET    /api/wallets/lookup       Find wallet by wallet number
PUT    /api/wallets/{id}/limit   Update daily transfer limit
```

### Transfers
```
POST  /api/transfers             Submit a transfer
GET   /api/transfers/{id}        Get transfer by ID
GET   /api/transfers/metrics     System transfer metrics (ADMIN only)
```

### Transactions
```
GET /api/transactions/wallet/{id}             Full transaction history
GET /api/transactions/wallet/{id}/filter      Date range filter
GET /api/transactions/wallet/{id}/statement   Statement with summary totals
GET /api/transactions/{id}/slip               Transfer slip / receipt
```

### Fraud (ADMIN only)
```
GET   /api/fraud/alerts                    All fraud alerts
GET   /api/fraud/alerts/wallet/{walletId}  Alerts by wallet
GET   /api/fraud/queue/size                Current queue depth
PATCH /api/fraud/alerts/{id}/review        Mark alert reviewed
PATCH /api/fraud/alerts/{id}/dismiss       Dismiss alert
POST  /api/fraud/freeze/{walletId}         Freeze a wallet
POST  /api/fraud/unfreeze/{walletId}       Unfreeze a wallet
```

---

## Project Structure

### Backend
```
src/main/java/com/finflow/
├── controller/
│   ├── AuthController.java
│   ├── WalletController.java
│   ├── TransferController.java
│   ├── TransactionController.java
│   └── FraudController.java
├── service/
│   ├── AuthService.java
│   ├── WalletService.java
│   ├── TransferService.java          ← Core transfer logic
│   ├── TransactionService.java
│   ├── FraudDetectionService.java
│   ├── LedgerService.java
│   └── NotificationService.java
├── concurrency/
│   ├── WalletLockManager.java        ← ReentrantLock + ConcurrentHashMap
│   ├── SemaphoreManager.java         ← Semaphore per wallet
│   └── AccountFreezeManager.java     ← In-memory freeze state
├── async/
│   ├── AsyncConfig.java              ← ThreadPoolTaskExecutor beans
│   └── TransferMetrics.java          ← AtomicLong counters
├── fraud/
│   ├── FraudRulesEngine.java         ← 5 detection rules
│   ├── FraudScoreCalculator.java
│   └── FraudQueueProcessor.java      ← BlockingQueue consumer
├── entity/
│   ├── User.java
│   ├── Wallet.java
│   ├── Transaction.java
│   ├── LedgerEntry.java
│   └── FraudAlert.java
├── dto/
│   ├── request/
│   └── response/
├── repository/
├── security/
│   ├── JwtUtil.java
│   ├── JwtFilter.java
│   ├── SecurityConfig.java
│   └── CustomUserDetailsService.java
├── exception/
│   └── GlobalExceptionHandler.java
├── config/
│   └── CorsConfig.java
└── FinFlowApplication.java
```

### Frontend
```
finflow-frontend/
├── index.html              Login / Register
├── dashboard.html          Wallet overview and stats
├── transfer.html           Transfer form with live preview
├── history.html            Transaction history with date filter
├── slip.html               Transfer receipt / slip
├── admin/
│   ├── admin-dashboard.html    Metrics + alert preview
│   └── admin-fraud.html        Full fraud management panel
├── css/
│   └── style.css
└── js/
    ├── auth.js
    ├── wallet.js
    ├── transfer.js
    ├── history.js
    ├── slip.js
    └── admin.js
```

## ⚙️ Setup Instructions

### Prerequisites
- Java 17
- MySQL 8.0
- Maven
- Docker (optional — for containerized run)

---

### Backend (Local)

```bash
# 1. Clone the repository
git clone https://github.com/abhinavasd2005/FinFlow-.git
cd FinFlow-

# 2. Create the database in MySQL
CREATE DATABASE finflow;

# 3. Update application.properties with your local values
spring.datasource.url=jdbc:mysql://localhost:3306/finflow
spring.datasource.username=your_username
spring.datasource.password=your_password
jwt.secret=any_long_secret_key_here
server.port=8080

# 4. Run the application
mvn spring-boot:run
# Server starts at http://localhost:8080
# Hibernate auto-creates all tables on first run
```

---

### Backend (Docker)

```bash
docker build -t finflow .
docker run -p 8080:8080 \
  -e MYSQLHOST=your_host \
  -e MYSQLPORT=3306 \
  -e MYSQLDATABASE=finflow \
  -e MYSQLUSER=root \
  -e MYSQLPASSWORD=your_password \
  -e JWT_SECRET=your_secret \
  finflow
```

---

### Frontend

```bash
# Open directly in browser
# Or use VS Code Live Server extension
open finflow-frontend/index.html

# For production — change API_BASE in all JS files to your backend URL
```

---

### Create Admin Account

```bash
curl -X POST "http://localhost:8080/api/auth/register/admin?adminSecret=finflow-admin-secret" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","email":"admin@finflow.com","password":"admin123"}'
```

## Future Improvements

- **Kafka integration** — publish transaction events to a Kafka topic for downstream ETL pipeline and analytics warehouse
- **Notification service** — email/SMS alerts via SMTP or third-party provider
- **Refresh tokens** — sliding JWT sessions instead of hard 1hr expiry
- **Data warehouse layer** — ETL/ELT pipeline into DuckDB for analytics (planned as separate project)

---

## Conclusion

FinFlow demonstrates production-relevant backend engineering skills — specifically the kind of concurrency safety, transactional correctness, and system design thinking that real fintech systems require. Every design choice, from lock ordering to idempotency keys to async fraud queuing, reflects how these problems are solved in industry. The project is fully functional end-to-end, from login to transfer slip, with a working admin panel and load-tested concurrency guarantees.

---

*Built with Java 17 · Spring Boot 3.2 · MySQL · JWT · Apache JMeter*
