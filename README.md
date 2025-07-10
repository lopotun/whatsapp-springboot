# WhatsApp Chat Viewer Spring Boot Application

A Spring Boot application for processing and searching WhatsApp exported chats with comprehensive database storage and search capabilities.

## Features

- **Chat Processing**: Parse WhatsApp exported chat files and extract multimedia attachments
- **Database Storage**: Store chat entries and attachment metadata in PostgreSQL database
- **Advanced Search**: Search chat entries by multiple criteria including:
  - Date ranges
  - Author
  - Message type (TEXT, FILE, DOCUMENT, IMAGE, VIDEO, AUDIO, etc.)
  - Keywords in message content
  - Attachment presence
  - Combinations of the above
- **Content-Addressed Storage**: Multimedia files stored using SHA-256 hashes with two-level directory structure
- **REST API**: Comprehensive API endpoints for all search and management operations
- **Streaming Support**: Process large chat files efficiently with streaming

## Database Schema

### ChatEntry Entity
- `id`: Primary key
- `timestamp`: Original timestamp string from WhatsApp
- `payload`: Message content
- `author`: Message author
- `fileName`: Original filename for attachments
- `type`: Message type (TEXT, FILE, DOCUMENT, IMAGE, VIDEO, AUDIO, etc.)
- `localDateTime`: Parsed timestamp as LocalDateTime
- `attachmentHash`: Reference to attachment file hash
- `createdAt`: Record creation timestamp
- `updatedAt`: Record update timestamp

### Attachment Entity
- `hash`: SHA-256 content hash (primary key)
- `lastAdded`: Timestamp when first added
- `status`: Attachment status
- `reserved1`, `reserved2`: Reserved columns for future use

### Location Entity
- `id`: Primary key
- `realFileName`: Original filename
- `clientId`: Client identifier
- `lastAdded`: Timestamp when added
- `status`: Location status
- `attachmentHash`: Reference to Attachment entity

## API Endpoints

### Chat Entry Search

#### Basic Search
```
GET /api/chat-entries/search?author=John&type=TEXT&startDate=2023-12-25T00:00:00&endDate=2023-12-25T23:59:59&hasAttachment=false&page=0&size=20
```

#### Keyword Search
```
GET /api/chat-entries/search/keyword?keyword=hello&page=0&size=20
```

#### Advanced Search
```
GET /api/chat-entries/search/advanced?keyword=hello&author=John&type=TEXT&startDate=2023-12-25T00:00:00&endDate=2023-12-25T23:59:59&page=0&size=20
```

#### Specific Searches
```
GET /api/chat-entries/author/{author}
GET /api/chat-entries/type/{type}
GET /api/chat-entries/date-range?start=2023-12-25T00:00:00&end=2023-12-25T23:59:59
GET /api/chat-entries/author/{author}/type/{type}
GET /api/chat-entries/author/{author}/date-range?start=2023-12-25T00:00:00&end=2023-12-25T23:59:59
GET /api/chat-entries/type/{type}/date-range?start=2023-12-25T00:00:00&end=2023-12-25T23:59:59
GET /api/chat-entries/attachment/{hash}
```

#### Statistics
```
GET /api/chat-entries/stats/author/{author}
GET /api/chat-entries/stats/type/{type}
GET /api/chat-entries/stats/date-range?start=2023-12-25T00:00:00&end=2023-12-25T23:59:59
```

#### Management
```
GET /api/chat-entries/{id}
GET /api/chat-entries?page=0&size=20
PUT /api/chat-entries/{id}/attachment?attachmentHash=hash
DELETE /api/chat-entries/{id}
```

### Attachment Management
```
GET /api/attachments
GET /api/attachments/{hash}
GET /api/attachments/search?status=ACTIVE
PUT /api/attachments/{hash}/status?status=INACTIVE
GET /api/attachments/{hash}/locations
POST /api/attachments/{hash}/locations
```

## Setup and Installation

### Prerequisites
- Java 21
- Maven 3.6+
- Docker (for PostgreSQL)

### Database Setup

1. Start PostgreSQL using Docker Compose:
```bash
docker-compose up -d postgres
```

2. The application will automatically create the database schema on startup.

### Application Configuration

The application uses different configurations for development and testing:

- **Production/Development**: PostgreSQL database
- **Testing**: H2 in-memory database

Configuration files:
- `application.properties`: Production configuration with PostgreSQL
- `application-test.properties`: Test configuration with H2

### Running the Application

1. Start the database:
```bash
docker-compose up -d postgres
```

2. Run the application:
```bash
./mvnw spring-boot:run
```

3. The application will be available at `http://localhost:8080`

### Running Tests

```bash
# Run all tests
./mvnw test

# Run specific test classes
./mvnw test -Dtest=ChatEntryServiceTest
./mvnw test -Dtest=ChatEntryControllerTest
```

## Database Choice: PostgreSQL

PostgreSQL was chosen as the primary database for this application because:

1. **Full-Text Search**: Excellent full-text search capabilities with GIN indexes
2. **JSON Support**: Native JSON/JSONB support for complex data structures
3. **Advanced Indexing**: Support for B-tree, GIN, and GiST indexes
4. **Date/Time Optimization**: Excellent performance for date range queries
5. **Scalability**: Handles large datasets efficiently
6. **ACID Compliance**: Ensures data integrity
7. **Open Source**: Free and community-driven

## Search Performance

The application includes comprehensive indexing for optimal search performance:

- **Single Column Indexes**: `author`, `type`, `localDateTime`, `createdAt`
- **Composite Indexes**: `author + type`, `localDateTime + author`
- **Full-Text Search**: Keyword search in `payload` and `author` fields
- **Pagination**: All search endpoints support pagination for large result sets

## File Storage

Multimedia files are stored using a content-addressed approach:

- **Hash-based Naming**: Files named using SHA-256 content hash
- **Two-Level Directory Structure**: `hash[0:2]/hash[2:4]/hash[4:]`
- **Deduplication**: Identical files stored only once
- **Extension Preservation**: Original file extensions preserved

## Development

### Project Structure
```
src/
├── main/java/net/kem/whatsapp/chatviewer/whatsappspringboot/
│   ├── controller/          # REST controllers
│   ├── model/              # Data models and entities
│   ├── repository/         # Data access layer
│   ├── service/            # Business logic
│   └── WhatsappSpringbootApplication.java
├── test/java/              # Test classes
└── resources/
    ├── application.properties
    └── application-test.properties
```

### Key Components

- **ChatEntryEntity**: JPA entity for chat entries with comprehensive indexing
- **ChatEntryRepository**: Repository with custom query methods for search
- **ChatEntryService**: Service layer with business logic and transaction management
- **ChatEntryController**: REST controller exposing search and management endpoints
- **ChatService**: Enhanced to save chat entries to database during processing

## API Examples

### Search for messages from a specific author in a date range
```bash
curl "http://localhost:8080/api/chat-entries/search?author=John%20Doe&startDate=2023-12-25T00:00:00&endDate=2023-12-25T23:59:59"
```

### Search for messages containing a keyword
```bash
curl "http://localhost:8080/api/chat-entries/search/keyword?keyword=hello"
```

### Get statistics for message types
```bash
curl "http://localhost:8080/api/chat-entries/stats/type/TEXT"
```

### Advanced search with multiple criteria
```bash
curl "http://localhost:8080/api/chat-entries/search/advanced?keyword=meeting&author=John%20Doe&type=TEXT&startDate=2023-12-25T00:00:00&endDate=2023-12-25T23:59:59"
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Run the test suite
6. Submit a pull request

## License

This project is licensed under the MIT License. 