# WhatsApp Chat Viewer - Microservice Architecture

## Overview

This document describes the new microservice architecture that separates ZIP processing from the main WhatsApp chat viewer application. The architecture introduces a dedicated **Zipper** microservice that handles all ZIP file processing asynchronously.

## Architecture Benefits

### 1. **Scalability**

- Main service remains responsive during heavy ZIP processing
- Zipper service can be scaled independently based on processing load
- Better resource utilization and parallel processing

### 2. **Reliability**

- Processing failures don't affect main service availability
- Automatic retry mechanisms for failed jobs
- Better error handling and monitoring

### 3. **Maintainability**

- Clear separation of concerns
- Independent deployment and updates
- Easier testing and debugging

### 4. **Performance**

- Async processing prevents blocking user uploads
- Queue-based job management
- Configurable concurrency limits

## Architecture Components

### 1. **Main Service** (Current WhatsApp Spring Boot)

- **Port**: 8080
- **Responsibilities**:
  - User authentication and management
  - File upload handling and validation
  - Chat data storage and retrieval
  - User interface and notifications
  - Receiving processing results from Zipper

### 2. **Zipper Microservice** (New)

- **Port**: 8081
- **Responsibilities**:
  - ZIP file processing and extraction
  - Chat data parsing and deduplication
  - Multimedia file handling
  - Job queue management
  - Notifying main service of completion

### 3. **Shared Infrastructure**

- **Database**: Shared PostgreSQL database
- **File Storage**: Shared file system or object storage
- **Message Queue**: RabbitMQ for async communication (optional)

## Data Flow

### 1. **File Upload Process**

```
User → Main Service → Validate ZIP → Store in Temp Storage → Create Job → Notify User (Accepted)
```

### 2. **ZIP Processing Process**

```
Zipper Service → Pick Job → Process ZIP → Extract Data → Save to Database → Notify Main Service
```

### 3. **User Notification Process**

```
Main Service → Receive Result → Update Status → Send Email → Update UI
```

## Implementation Steps

### Phase 1: Create Zipper Microservice

1. ✅ Create Maven project structure
2. ✅ Implement core services (ZipProcessingService, JobQueueService)
3. ✅ Create REST endpoints for job management
4. ✅ Set up configuration and Docker support

### Phase 2: Modify Main Service

1. ✅ Add Zipper notification controller
2. ✅ Implement notification handling service
3. 🔄 Update ChatUploadService to delegate ZIP processing
4. 🔄 Add job status tracking

### Phase 3: Integration and Testing

1. 🔄 Set up shared database schema
2. 🔄 Configure inter-service communication
3. 🔄 Implement end-to-end testing
4. 🔄 Performance testing and optimization

## Configuration

### Main Service Configuration

```yaml
app:
  zipper:
    service-url: http://localhost:8081
    api-key: your-secret-api-key
    notification-endpoint: /api/v1/zipper/notifications
```

### Zipper Service Configuration

```yaml
app:
  main-service:
    notification-url: http://localhost:8080/api/v1/zipper/notifications
    api-key: your-secret-api-key
  processing:
    max-concurrent-jobs: 3
    job-timeout-minutes: 30
    retry-attempts: 3
```

## Database Schema Changes

### Consolidated Migration Approach

The project now uses a **consolidated database migration approach** that combines all database migrations, upgrades, and fixes into a single comprehensive script. This ensures consistent schemas across all environments.

### Key Tables

The consolidated schema includes:

- **`users`** - User accounts and authentication
- **`chat_entries`** - WhatsApp chat messages and metadata
- **`attachments`** - File attachment metadata and references
- **`zip_processing_jobs`** - ZIP processing job queue

### Migration Scripts

- **`database_migration_complete.sql`** - Main consolidated migration script
- **`docker/init-scripts/01-init.sql`** - Docker initialization script (contains complete migration)

**Note**: All previous individual migration scripts have been consolidated. Use the consolidated script for all database operations.

## Security Considerations

### 1. **API Key Authentication**

- Main service and Zipper service communicate using shared API keys
- API keys should be rotated regularly
- Use environment variables for sensitive configuration

### 2. **User Isolation**

- All database queries include userId parameter
- Jobs are scoped to specific users
- No cross-user data access possible

### 3. **File Access Control**

- Temporary storage uses secure, isolated directories
- File cleanup after processing
- Access logging for audit purposes

## Monitoring and Observability

### 1. **Health Checks**

- `/zipper/api/v1/jobs/health` - Zipper service health
- `/api/v1/zipper/notifications/health` - Main service notification endpoint

### 2. **Metrics**

- Job processing times
- Success/failure rates
- Queue lengths
- Resource utilization

### 3. **Logging**

- Structured logging for all operations
- Correlation IDs for request tracing
- Error logging with context

## Deployment

### Docker Compose

```bash
# Start Zipper service with dependencies
docker-compose -f compose-zipper.yaml up -d

# Start main service
docker-compose up -d
```

### Production Considerations

- Use proper secrets management
- Set up monitoring and alerting
- Configure auto-scaling policies
- Implement proper backup strategies

## Testing Strategy

### 1. **Unit Tests**

- Service layer testing
- Repository layer testing
- Controller testing

### 2. **Integration Tests**

- Inter-service communication
- Database operations
- File processing workflows

### 3. **Performance Tests**

- Large file processing
- Concurrent job processing
- Database performance under load

## Migration Plan

### 1. **Backward Compatibility**

- Maintain existing API endpoints
- Gradual migration of ZIP processing
- Feature flags for new functionality

### 2. **Data Migration**

- No data loss during migration
- Incremental processing of existing files
- Rollback procedures

### 3. **User Experience**

- Minimal disruption during migration
- Clear status updates
- Progress indicators for long-running operations

## Future Enhancements

### 1. **Advanced Queue Management**

- Priority-based job scheduling
- Resource-based job allocation
- Dynamic scaling based on load

### 2. **Enhanced Monitoring**

- Real-time job status updates
- Predictive failure detection
- Performance analytics dashboard

### 3. **Multi-format Support**

- Support for other archive formats (RAR, 7Z)
- Direct file uploads (non-archived)
- Streaming processing for very large files

## Troubleshooting

### Common Issues

1. **Service Communication Failures**

   - Check network connectivity
   - Verify API keys
   - Check service health endpoints

2. **Job Processing Failures**

   - Review error logs
   - Check file permissions
   - Verify database connectivity

3. **Performance Issues**
   - Monitor resource usage
   - Check queue lengths
   - Review database performance

### Debug Commands

```bash
# Check Zipper service status
curl http://localhost:8081/zipper/api/v1/jobs/health

# Check main service notification endpoint
curl http://localhost:8080/api/v1/zipper/notifications/health

# View job queue status
curl http://localhost:8081/zipper/api/v1/jobs/user/1
```

## Conclusion

The microservice architecture provides significant benefits for the WhatsApp Chat Viewer application, particularly in terms of scalability, reliability, and maintainability. The separation of concerns allows each service to focus on its core responsibilities while maintaining clear interfaces for communication.

The implementation follows Spring Boot best practices and includes comprehensive error handling, monitoring, and security measures. The architecture is designed to be easily extensible for future enhancements and can be deployed using standard containerization technologies.
