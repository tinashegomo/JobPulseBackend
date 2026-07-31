# JobPulse API - Spring Boot Backend

## Overview

This is the Spring Boot backend API for JobPulse, a job alert PWA. It runs alongside the existing React + Firebase frontend (which remains untouched during development).

## Tech Stack

- Java 21
- Spring Boot 3.4.x
- Spring Security + JWT Authentication
- MySQL (configurable for local or Aiven-hosted)
- MapStruct for entity-to-DTO mapping
- Lombok for boilerplate reduction
- AES encryption for API keys

## Project Structure

```
com.TinasheGomo.JobPulse
├── config/          # Security, CORS configuration
├── controller/      # REST endpoints
├── dto/             # Request/Response DTOs
├── entity/          # JPA entities
├── exception/       # Global exception handler
├── mapper/          # MapStruct mappers
├── repository/      # Spring Data JPA repositories
├── security/        # JWT, Auth filter, User details
├── service/         # Service interfaces
│   └── impl/        # Service implementations
└── util/            # Encryption utilities
```

## Setup

### 1. Prerequisites

- Java 21 or higher
- MySQL database (local or Aiven)
- Maven

### 2. Configure Database

Edit `src/main/resources/application-dev.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/jobpulse?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: your_password
```

### 3. Run the Application

```bash
# Using Maven wrapper
./mvnw spring-boot:run

# Or using Maven directly
mvn spring-boot:run
```

The API will start on `http://localhost:8080`

### 4. Environment Variables (Production)

Set these environment variables for production deployment:

```bash
DB_URL=jdbc:mysql://your-aiven-host:3306/jobpulse?useSSL=true
DB_USERNAME=your_db_username
DB_PASSWORD=your_db_password
JWT_SECRET=your_64_byte_secret_key
ENCRYPTION_SECRET=your_32_byte_secret_key
PORT=8080
```

## API Endpoints

### Authentication

| Method | Endpoint          | Description              | Auth Required |
|--------|-------------------|--------------------------|---------------|
| POST   | `/api/auth/register` | Register new user        | No            |
| POST   | `/api/auth/login`    | Login and get JWT token  | No            |
| GET    | `/api/auth/me`       | Get current user info    | Yes           |
| POST   | `/api/auth/refresh`  | Refresh JWT token        | Yes           |

### Alerts

| Method | Endpoint              | Description              | Auth Required |
|--------|-----------------------|--------------------------|---------------|
| POST   | `/api/alerts`         | Create new alert         | Yes           |
| GET    | `/api/alerts`         | Get all user alerts      | Yes           |
| GET    | `/api/alerts/:id`     | Get alert by ID          | Yes           |
| PUT    | `/api/alerts/:id`     | Update alert             | Yes           |
| DELETE | `/api/alerts/:id`     | Delete alert             | Yes           |

### Resume Profiles

| Method | Endpoint                   | Description                  | Auth Required |
|--------|----------------------------|------------------------------|---------------|
| POST   | `/api/resume-profiles`     | Create or update profile     | Yes           |
| GET    | `/api/resume-profiles/me`  | Get current user's profile   | Yes           |
| DELETE | `/api/resume-profiles/me`  | Delete current user's profile| Yes           |

### API Keys

| Method | Endpoint             | Description              | Auth Required |
|--------|----------------------|--------------------------|---------------|
| POST   | `/api/api-keys`      | Save API key             | Yes           |
| GET    | `/api/api-keys`      | Get all user API keys    | Yes           |
| DELETE | `/api/api-keys/:id`  | Delete API key           | Yes           |

### Jobs

| Method | Endpoint         | Description              | Auth Required |
|--------|------------------|--------------------------|---------------|
| GET    | `/api/jobs`      | Get all jobs             | Yes           |
| GET    | `/api/jobs/:id`  | Get job by ID            | Yes           |

### User Jobs (Matched Jobs)

| Method | Endpoint                   | Description                  | Auth Required |
|--------|----------------------------|------------------------------|---------------|
| GET    | `/api/user-jobs/me`        | Get user's matched jobs      | Yes           |
| GET    | `/api/user-jobs/me/unnotified` | Get unnotified matched jobs | Yes           |

## Request/Response Examples

### Register

```http
POST /api/auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password123"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "id": "uuid-here",
  "email": "user@example.com"
}
```

### Login

```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password123"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "id": "uuid-here",
  "email": "user@example.com"
}
```

### Create Alert

```http
POST /api/alerts
Authorization: Bearer <token>
Content-Type: application/json

{
  "keywords": "Java, Spring Boot",
  "workType": "remote",
  "seniority": "mid",
  "location": "Zimbabwe"
}
```

**Response:**
```json
{
  "id": "uuid-here",
  "keywords": "Java, Spring Boot",
  "workType": "remote",
  "seniority": "mid",
  "location": "Zimbabwe",
  "active": true,
  "createdAt": "2026-07-30T10:00:00",
  "updatedAt": "2026-07-30T10:00:00"
}
```

### Create/Update Resume Profile

```http
POST /api/resume-profiles
Authorization: Bearer <token>
Content-Type: application/json

{
  "skills": "Java, Spring Boot, React",
  "preferredRoles": "Software Engineer, Backend Developer",
  "level": "mid",
  "workPreference": "remote"
}
```

**Response:**
```json
{
  "id": "uuid-here",
  "skills": "Java, Spring Boot, React",
  "preferredRoles": "Software Engineer, Backend Developer",
  "level": "mid",
  "workPreference": "remote",
  "resumeText": null,
  "createdAt": "2026-07-30T10:00:00",
  "updatedAt": "2026-07-30T10:00:00"
}
```

### Save API Key

```http
POST /api/api-keys
Authorization: Bearer <token>
Content-Type: application/json

{
  "provider": "opencode",
  "apiKey": "your-api-key-here"
}
```

**Response:**
```json
{
  "id": "uuid-here",
  "provider": "opencode",
  "maskedKey": "your-***",
  "active": true,
  "createdAt": "2026-07-30T10:00:00",
  "updatedAt": "2026-07-30T10:00:00"
}
```

## Error Handling

All errors return a consistent format:

```json
{
  "timestamp": "2026-07-30T10:00:00",
  "errorMessage": "Error message here",
  "errorDetails": "/api/endpoint",
  "errorCode": "400_BAD_REQUEST"
}
```

## Security

- JWT tokens expire after 90 days
- Passwords are hashed with BCrypt
- API keys are encrypted with AES-256 before storage
- All endpoints except `/api/auth/**` require authentication
- CORS configured for localhost:5173 (Vite dev server) and jobpulse.vercel.app

## Database Tables

| Table          | Description                          |
|----------------|--------------------------------------|
| `users`        | User accounts                        |
| `alerts`       | Job search alerts per user           |
| `resume_profiles` | User resume/skills profiles      |
| `api_keys`     | Encrypted API keys per user          |
| `jobs`         | Scraped job listings                 |
| `user_jobs`    | User-job matching scores             |

## Development Notes

- This API runs alongside the existing Firebase backend
- Firebase remains untouched as a fallback
- Frontend will be updated to call this API instead of Firestore in Phase 8
- FCM (Firebase Cloud Messaging) will remain for push notifications even after migration
