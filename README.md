# Gistify: AI_Powered_Document_Summarization

AI-powered document summarization application with Google OAuth2 authentication.

## Features

- Google Sign-In authentication
- Multi-format document upload (PDF, DOCX, TXT)
- AI-powered summarization using Ollama (Phi-3/TinyLlama)
- MySQL database persistence
- User-specific document management
- Responsive web interface

## Technologies Used

- **Backend:** Java 21, Spring Boot 3.4.3, Spring Security, Spring Data JPA
- **Frontend:** HTML5, CSS3, JavaScript, Thymeleaf
- **Database:** MySQL 9.5
- **AI:** Ollama, Spring AI
- **Authentication:** OAuth2 (Google)
- **Document Processing:** Apache Tika, Apache POI

## Prerequisites

- Java 21+
- MySQL 9.x
- Ollama (with phi3 or tinyllama model)
- Google Cloud Console OAuth2 credentials
- Maven 3.6+

## Setup Instructions

### 1. Install MySQL
Download and install MySQL from https://dev.mysql.com/downloads/mysql/

### 2. Create Database
```sql
CREATE DATABASE seconize_doc_summarizer;
```

### 3. Install Ollama
Download from https://ollama.ai
Pull a model:
```bash
ollama pull tinyllama
# OR
ollama pull phi3
```

### 4. Google OAuth2 Setup
1. Go to https://console.cloud.google.com/
2. Create a new project
3. Enable Google+ API
4. Create OAuth 2.0 credentials
5. Set authorized redirect URI: `http://localhost:8080/login/oauth2/code/google`
6. Copy Client ID and Client Secret

### 5. Configure Application
Edit `src/main/resources/application.properties`:
```properties
# Update these values
spring.datasource.password=YOUR_MYSQL_PASSWORD
spring.security.oauth2.client.registration.google.client-id=YOUR_CLIENT_ID
spring.security.oauth2.client.registration.google.client-secret=YOUR_CLIENT_SECRET
spring.ai.ollama.chat.model=tinyllama
```

### 6. Build and Run
```bash
mvn clean install
mvn spring-boot:run
```

### 7. Access Application
Open browser: http://localhost:8080

## Usage

1. Click "Sign in with Google"
2. Authorize the application
3. Upload a document (PDF, DOCX, or TXT)
4. Wait for AI to generate summary
5. View all your uploaded documents and summaries

## Project Structure
```
src/
├── main/
│   ├── java/com/techie/springai/rag/
│   │   ├── controller/
│   │   │   └── WebController.java
│   │   ├── entity/
│   │   │   ├── User.java
│   │   │   ├── Document.java
│   │   │   └── Summary.java
│   │   ├── repository/
│   │   │   ├── UserRepository.java
│   │   │   ├── DocumentRepository.java
│   │   │   └── SummaryRepository.java
│   │   ├── security/
│   │   │   ├── SecurityConfig.java
│   │   │   └── CustomOAuth2UserService.java
│   │   ├── service/
│   │   │   └── DocumentService.java
│   │   ├── SummaryController.java
│   │   └── SpringAiRagTutorialApplication.java
│   └── resources/
│       ├── templates/
│       │   ├── index.html
│       │   └── home.html
│       └── application.properties
└── uploads/  (generated)
```

## Database Schema

- **users**: Stores Google OAuth2 user information
- **documents**: Stores uploaded file metadata
- **summaries**: Stores AI-generated summaries

## Limitations

- Maximum file size: 50MB
- Supported formats: PDF, DOCX, TXT only
- AI model requires sufficient RAM (adjust model in config)
- Summary quality depends on document quality and Ollama model used

## Author

[Akash Gutta]  
M. S. Ramaiah Institute of Technology, Bengaluru

## Submission Date

January 2026
