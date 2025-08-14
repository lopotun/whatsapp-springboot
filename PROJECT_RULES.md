# WhatsApp Chat Viewer - Project Rules

## 🎯 Project Overview

This application is a Spring Boot-based system designed to parse, store, and manage WhatsApp chat exports. Users can upload zipped chat files, perform searches, and manage their chat data through both REST API and web interface.

## 🏗️ Architecture Principles

### Core Components
- **Spring Boot Application**: Main application framework
- **Database**: Stores user data, chat entries, and file mappings
- **File Storage**: Separate storage for multimedia files (local/NFS/S3/GCP)
- **REST API**: Backend services for mobile and web clients
- **Web UI**: Browser-based interface for chat management

### Data Flow
1. User uploads zipped WhatsApp chat export
2. System parses chat text and extracts multimedia files
3. Multimedia files are hashed (SHA256) and stored in file system
4. Chat entries and file mappings are stored in database
5. User can search and manage their chats through API/UI

## 🔒 Security Requirements

### User Isolation (CRITICAL)
- **Strict user isolation**: Users can ONLY access their own chats
- **No cross-user data access**: Impossible for users to access other users' data
- **User-scoped queries**: All database queries must include user ID filter
- **Authentication required**: All endpoints require valid user authentication
- **Authorization checks**: Verify user ownership before any data operation

### Implementation Rules
```java
// ✅ CORRECT - Always filter by userId
@Query("SELECT ce FROM ChatEntryEntity ce WHERE ce.userId = :userId AND ...")
Page<ChatEntryEntity> findByUserIdAndCriteria(@Param("userId") Long userId, ...);

// ❌ FORBIDDEN - No user filtering
@Query("SELECT ce FROM ChatEntryEntity ce WHERE ...")
Page<ChatEntryEntity> findByCriteria(...);
```

### Security Checklist
- [ ] All repository methods include userId parameter
- [ ] All service methods verify user ownership
- [ ] All controller endpoints require authentication
- [ ] No direct database access without user context
- [ ] Input validation and sanitization
- [ ] CSRF protection enabled
- [ ] Secure file upload handling

## 📊 Performance & Scalability

### Database Requirements
- **Capacity**: Support up to 50,000 users
- **Per-user limit**: Up to 1,000 different chats per user
- **Total capacity**: 50M potential chat entries
- **Performance**: Sub-second response times for searches

### Database Design Rules
```sql
-- Required indexes for performance
CREATE INDEX idx_chat_entries_user_id ON chat_entries(user_id);
CREATE INDEX idx_chat_entries_user_chat ON chat_entries(user_id, chat_id);
CREATE INDEX idx_chat_entries_author ON chat_entries(author);
CREATE INDEX idx_chat_entries_type ON chat_entries(type);
CREATE INDEX idx_chat_entries_local_date_time ON chat_entries(local_date_time);
CREATE INDEX idx_chat_entries_created_at ON chat_entries(created_at);
```

### Query Optimization
- Always use pagination for large result sets
- Implement database connection pooling
- Use appropriate database indexes
- Consider read replicas for search-heavy workloads

## 📁 File Storage Architecture

### Multimedia File Handling
- **Storage**: Separate from database (local/NFS/S3/GCP)
- **Naming**: SHA256 hash of file content
- **Deduplication**: Same content = same filename (saves storage)
- **Organization**: Hierarchical directory structure based on hash

### File Storage Rules
```java
// File naming convention
String hash = calculateSHA256(fileContent);
String fileName = hash.substring(0, 3) + "/" + hash.substring(3, 6) + "/" + hash;

// Example: abc123def456... -> abc/123/def456...
```

### Storage Providers
- **Local**: `./multimedia-files/`
- **NFS**: Mounted network storage
- **S3**: AWS S3 bucket
- **GCP**: Google Cloud Storage
- **Configurable**: Via application properties

## 🔍 Search & Query Capabilities

### Supported Search Types
- **Keyword search**: Full-text search in chat content
- **Time range**: Filter by date/time
- **Author filtering**: Search by message sender
- **Type filtering**: Text, image, video, document, etc.
- **Attachment filtering**: Messages with/without attachments
- **Combined searches**: Multiple criteria together

### Search Implementation
```java
// Example search method signature
Page<ChatEntryEntity> searchChatEntries(
    Long userId,           // Required for security
    String author,         // Optional filter
    ChatEntry.Type type,   // Optional filter
    LocalDateTime startDate, // Optional filter
    LocalDateTime endDate,   // Optional filter
    Boolean hasAttachment,   // Optional filter
    int page,              // Pagination
    int size               // Pagination
);
```

## 🚀 API Design

### REST API Principles
- **RESTful design**: Use HTTP methods appropriately
- **Consistent naming**: Use kebab-case for URLs
- **Versioning**: Include API version in URL path
- **Pagination**: Always support pagination for list endpoints
- **Error handling**: Consistent error response format

### API Endpoints Structure
```
/api/v1/upload/text          - Upload text chat file
/api/v1/upload/zip           - Upload zipped chat file
/api/v1/chat-entries         - List/search chat entries
/api/v1/chat-entries/{id}    - Get specific chat entry
/api/v1/attachments          - List attachments
/api/v1/stats                - Get statistics
```

### Response Format
```json
{
  "success": true,
  "data": { ... },
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 150,
    "totalPages": 8
  },
  "timestamp": "2024-01-15T10:30:00Z"
}
```

## 🎨 User Interface

### Web UI Requirements
- **Responsive design**: Works on desktop and mobile
- **Modern interface**: Clean, intuitive design
- **Real-time feedback**: Progress indicators for uploads
- **Search interface**: Advanced search with filters
- **File management**: Upload, view, delete chats

### UI Components
- **Upload area**: Drag & drop file upload
- **Search panel**: Multiple filter options
- **Results view**: Paginated chat entry display
- **Statistics dashboard**: User activity overview
- **Settings page**: User preferences and account management

## 📱 Future Mobile Support

### API Preparation
- **Mobile-friendly endpoints**: Optimize for mobile clients
- **Authentication**: JWT tokens for mobile apps
- **Push notifications**: Prepare for future notification system
- **Offline support**: Consider offline data synchronization

### Mobile Considerations
- **Efficient data transfer**: Minimize payload sizes
- **Image optimization**: Thumbnails for mobile display
- **Battery optimization**: Efficient API calls
- **Caching strategy**: Local data caching

## 🧪 Testing Requirements

### Test Coverage
- **Unit tests**: All service methods
- **Integration tests**: API endpoints
- **Security tests**: User isolation verification
- **Performance tests**: Load testing for 50K users
- **File storage tests**: All storage providers

### Security Testing
```java
// Test that users cannot access other users' data
@Test
void userCannotAccessOtherUserData() {
    // Given: User A and User B with separate chats
    // When: User A tries to access User B's chat
    // Then: Access should be denied
}
```

## 📋 Development Guidelines

### Code Standards
- **Java 17+**: Use modern Java features
- **Spring Boot 3.x**: Latest stable version
- **Lombok**: Reduce boilerplate code
- **Consistent naming**: Follow Java conventions
- **Documentation**: Javadoc for public methods

### Database Conventions
- **Naming**: snake_case for tables and columns
- **Timestamps**: Include created_at and updated_at
- **Soft deletes**: Use status field instead of hard deletes
- **Foreign keys**: Proper relationships with constraints

### Error Handling
- **Consistent exceptions**: Use custom exception types
- **User-friendly messages**: Clear error descriptions
- **Logging**: Appropriate log levels and context
- **Monitoring**: Track errors and performance metrics

## 🔧 Configuration

### Environment Variables
```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:15432/whatsapp_chat
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}

# File Storage
app.storage.type=local|nfs|s3|gcp
app.storage.path=./multimedia-files
app.storage.s3.bucket=whatsapp-chat-files
app.storage.gcp.bucket=whatsapp-chat-files

# Security
app.security.jwt.secret=${JWT_SECRET}
app.security.jwt.expiration=86400000

# Performance
spring.jpa.properties.hibernate.jdbc.batch_size=50
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
```

## 📈 Monitoring & Observability

### Metrics to Track
- **Upload success/failure rates**
- **Search response times**
- **Database query performance**
- **File storage usage**
- **User activity patterns**
- **Error rates and types**

### Logging Strategy
- **Structured logging**: JSON format for easy parsing
- **Correlation IDs**: Track requests across services
- **User context**: Include user ID in relevant logs
- **Performance logging**: Track slow operations

## 🚨 Critical Rules Summary

1. **NEVER** allow cross-user data access
2. **ALWAYS** include userId in database queries
3. **ALWAYS** validate user ownership before operations
4. **NEVER** store sensitive data in logs
5. **ALWAYS** use pagination for large datasets
6. **ALWAYS** handle file uploads securely
7. **NEVER** expose internal system details in errors
8. **ALWAYS** test security boundaries thoroughly

## 📚 Documentation Requirements

- **API documentation**: OpenAPI/Swagger specs
- **Database schema**: ERD and migration scripts
- **Deployment guide**: Environment setup and deployment
- **User manual**: End-user documentation
- **Developer guide**: Onboarding and contribution guidelines

---

*This document should be reviewed and updated regularly as the project evolves.* 
