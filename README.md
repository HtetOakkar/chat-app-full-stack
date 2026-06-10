# Meow Chit Chat

A real-time messaging application allowing users to search global registries, establish contacts, and engage in both public and private discussions.

## Tech Stack

### Backend
- Spring Boot 3.5.6
- Java 21
- Spring Web and Spring Security
- Spring Data JPA
- MySQL
- Redis
- JWT (JSON Web Tokens) for authentication
- STOMP WebSockets for real-time message exchange
- Flyway for database migration
- Spring Batch

### Frontend
- Next.js
- React 19
- Tailwind CSS
- SockJS and STOMP client

## Environment Configuration

A template configuration is provided in .env.example. To configure the project, create a .env file in the root directory and define the following variables:

- DB_URL: JDBC connection URL for the MySQL database
- DB_USER: Database username
- DB_PASSWORD: Database password
- JWT_SECRET: Secret key for signing JSON Web Tokens
- JWT_EXPIRATION_MS: Expiration time for JWTs in milliseconds
- REDIS_HOST: Hostname for the Redis server
- REDIS_PORT: Port number for the Redis server
- APP_CORS_ALLOWED_ORIGINS: Allowed origins for CORS requests
- FLYWAY_BASELINE_ON_MIGRATE: Baseline migration configuration
- FLYWAY_BASELINE_VERSION: Baseline version configuration

## Getting Started

### Prerequisites
- Java Development Kit (JDK) 21
- Node.js (v18 or higher recommended) and npm
- MySQL Server
- Redis Server

### Running the Backend
Run the backend application using the Maven wrapper:
```bash
./mvnw spring-boot:run
```

### Running the Frontend
1. Navigate to the frontend directory:
   ```bash
   cd web
   ```
2. Install the dependencies:
   ```bash
   npm install
   ```
3. Start the development server:
   ```bash
   npm run dev
   ```
4. Open http://localhost:3000 in your browser to view the application.

## Testing
To run the static verification and mobile UI tests for the frontend:
1. Navigate to the frontend directory:
   ```bash
   cd web
   ```
2. Run the tests:
   ```bash
   npm test
   ```

## Repository Information
- Remote Repository: https://github.com/HtetOakkar/chat-app-full-stack.git
