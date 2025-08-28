# WhatsApp Chat Viewer - Multi-Module Architecture

## 🏗️ **Overview**

This document describes the new **multi-module Maven project** architecture that provides the benefits of microservices while maintaining easier development, testing, and deployment. Instead of completely separate projects, we now have a single project with multiple modules.

## 🎯 **Benefits of Multi-Module Approach**

### **1. Development Benefits**

- ✅ **Single Repository**: All code in one place
- ✅ **Shared Dependencies**: Centralized dependency management
- ✅ **Easier Testing**: Test all modules together
- ✅ **Consistent Versioning**: All modules use same version
- ✅ **Simplified CI/CD**: Single pipeline for all modules

### **2. Runtime Benefits**

- ✅ **Microservice Architecture**: Services run independently
- ✅ **Independent Scaling**: Each service can scale separately
- ✅ **Fault Isolation**: One service failure doesn't affect others
- ✅ **Technology Flexibility**: Different modules can use different tech stacks

### **3. Operational Benefits**

- ✅ **Unified Deployment**: Deploy all services together
- ✅ **Shared Infrastructure**: Common database, monitoring, logging
- ✅ **Easier Debugging**: Correlated logs across services
- ✅ **Simplified Configuration**: Centralized configuration management

## 🏛️ **Project Structure**

```
whatsapp-springboot/
├── pom.xml                           # Parent POM (pom packaging)
├── main-service/                     # Main WhatsApp service module
│   ├── pom.xml                      # Main service POM
│   ├── Dockerfile                   # Main service container
│   └── src/
│       ├── main/
│       │   ├── java/                # Java source code
│       │   ├── resources/           # Configuration files
│       │   └── webapp/              # Web resources
│       └── test/                    # Test code
├── zipper-service/                   # Zipper microservice module
│   ├── pom.xml                      # Zipper service POM
│   ├── Dockerfile                   # Zipper service container
│   └── src/
│       ├── main/
│       │   ├── java/                # Java source code
│       │   └── resources/           # Configuration files
│       └── test/                    # Test code
├── shared/                          # Shared code module
│   ├── pom.xml                      # Shared module POM
│   └── src/
│       └── main/
│           └── java/                # Shared models, utilities, etc.
├── docker/                          # Docker configurations
│   ├── docker-compose.yml          # Complete service orchestration
│   ├── init-scripts/               # Database initialization (consolidated)
│   └── nginx/                      # Reverse proxy configuration
├── scripts/                         # Development and deployment scripts
│   ├── dev-setup.sh               # Development environment setup
│   ├── start-dev.sh               # Development mode startup
│   └── start-prod.sh              # Production mode startup
└── docs/                           # Documentation
```

## 🔧 **Module Dependencies**

### **Dependency Graph**

```
main-service
├── shared (common models, utilities)
└── Spring Boot dependencies

zipper-service
├── shared (common models, utilities)
└── Spring Boot dependencies

shared
└── Spring Boot dependencies
```

### **Shared Module Contents**

- **Models**: Common entities (User, ChatEntry, etc.)
- **Utilities**: Common utility classes
- **Constants**: Shared constants and enums
- **DTOs**: Common data transfer objects
- **Exceptions**: Shared exception classes

## 🚀 **Running the Application**

### **Option 1: Development Mode (Recommended for Development)**

```bash
# Setup development environment
chmod +x scripts/dev-setup.sh
./scripts/dev-setup.sh

# Start in development mode
./start-dev.sh
```

**What this does:**

- Starts PostgreSQL, Redis, RabbitMQ in Docker
- Runs main service on port 8080
- Runs zipper service on port 8081
- Enables hot reloading for development

### **Option 2: Production Mode (Recommended for Production)**

```bash
# Start in production mode
./start-prod.sh
```

**What this does:**

- Builds all modules
- Starts all services in Docker containers
- Includes Nginx reverse proxy on port 80

### **Option 3: Individual Module Development**

```bash
# Start only dependencies
docker-compose -f docker/docker-compose.yml up -d postgres redis rabbitmq

# Run main service
cd main-service
./mvnw spring-boot:run

# In another terminal, run zipper service
cd zipper-service
./mvnw spring-boot:run
```

## 🐳 **Docker Architecture**

### **Service Containers**

- **main-service**: WhatsApp chat viewer (port 8080)
- **zipper-service**: ZIP processing microservice (port 8081)
- **postgres**: Shared database (port 25432)
- **redis**: Caching and session management (port 6379)
- **rabbitmq**: Message queue (ports 5672, 15672)
- **nginx**: Reverse proxy and load balancer (ports 80, 443)

### **Volume Management**

- **main-storage**: Main service file storage
- **main-temp**: Main service temporary files
- **zipper-storage**: Zipper service file storage
- **zipper-temp**: Zipper service temporary files
- **postgres-data**: Database persistence
- **redis-data**: Redis persistence
- **rabbitmq-data**: RabbitMQ persistence

## 🔄 **Data Flow in Multi-Module Architecture**

### **1. File Upload Process**

```
User → Nginx → Main Service → Validate ZIP → Store in Temp Storage → Create Job → Accept
```

### **2. ZIP Processing Process**

```
Zipper Service → Pick Job → Process ZIP → Extract Data → Save to Shared Database → Notify Main Service
```

### **3. User Notification Process**

```
Main Service → Receive Result → Update Status → Send Email → Update UI
```

## 📊 **Development Workflow**

### **1. Building the Project**

```bash
# Build all modules
./mvnw clean package

# Build specific module
./mvnw -pl main-service clean package
./mvnw -pl zipper-service clean package
./mvnw -pl shared clean package
```

### **2. Running Tests**

```bash
# Run all tests
./mvnw test

# Run tests for specific module
./mvnw -pl main-service test
./mvnw -pl zipper-service test
```

### **3. Running Individual Services**

```bash
# Main service
cd main-service
./mvnw spring-boot:run

# Zipper service
cd zipper-service
./mvnw spring-boot:run
```

## 🔒 **Security and Configuration**

### **Environment Variables**

```bash
# Database
DB_HOST=localhost
DB_PORT=25432
DB_USERNAME=postgres
DB_PASSWORD=password

# Service Communication
ZIPPER_SERVICE_URL=http://localhost:8081
ZIPPER_API_KEY=your-secret-api-key

# File Storage
FILE_STORAGE_PATH=/tmp/whatsapp-storage
TEMP_STORAGE_PATH=/tmp/whatsapp-temp
```

### **API Key Authentication**

- Services communicate using shared API keys
- Keys stored in environment variables
- Rotate keys regularly for security

## 📈 **Scaling and Performance**

### **Horizontal Scaling**

```bash
# Scale main service
docker-compose -f docker/docker-compose.yml up -d --scale main-service=3

# Scale zipper service
docker-compose -f docker/docker-compose.yml up -d --scale zipper-service=5
```

### **Load Balancing**

- Nginx automatically load balances between service instances
- Round-robin distribution by default
- Configurable load balancing strategies

## 🧪 **Testing Strategy**

### **1. Unit Tests**

- Each module has its own test suite
- Tests run independently
- Mock external dependencies

### **2. Integration Tests**

- Test inter-module communication
- Test database operations
- Test file processing workflows

### **3. End-to-End Tests**

- Test complete user workflows
- Test service communication
- Test error handling and recovery

## 🔍 **Monitoring and Observability**

### **Health Checks**

- **Main Service**: `/api/v1/zipper/notifications/health`
- **Zipper Service**: `/zipper/api/v1/jobs/health`
- **Database**: PostgreSQL health check
- **Redis**: Redis health check
- **RabbitMQ**: RabbitMQ health check

### **Logging**

- Structured logging across all modules
- Correlation IDs for request tracing
- Centralized log collection

### **Metrics**

- Spring Boot Actuator endpoints
- Custom business metrics
- Performance monitoring

## 🚨 **Troubleshooting**

### **Common Issues**

#### **1. Module Build Failures**

```bash
# Clean and rebuild
./mvnw clean install

# Check module dependencies
./mvnw dependency:tree
```

#### **2. Service Communication Issues**

```bash
# Check service health
curl http://localhost:8080/api/v1/zipper/notifications/health
curl http://localhost:8081/zipper/api/v1/jobs/health

# Check Docker logs
docker-compose -f docker/docker-compose.yml logs main-service
docker-compose -f docker/docker-compose.yml logs zipper-service
```

#### **3. Database Connection Issues**

```bash
# Check database status
docker-compose -f docker/docker-compose.yml exec postgres pg_isready -U postgres

# Check database logs
docker-compose -f docker/docker-compose.yml logs postgres
```

## 🔮 **Future Enhancements**

### **1. Additional Modules**

- **notification-service**: Email/SMS notifications
- **analytics-service**: Chat analytics and reporting
- **search-service**: Advanced search capabilities

### **2. Advanced Features**

- **Service Mesh**: Istio or Linkerd integration
- **API Gateway**: Kong or AWS API Gateway
- **Event Streaming**: Apache Kafka integration

### **3. Cloud Native**

- **Kubernetes**: K8s deployment manifests
- **Helm Charts**: Package management
- **Cloud Providers**: AWS, GCP, Azure support

## 📋 **Migration Checklist**

### **From Separate Projects**

- [ ] Move source code to appropriate modules
- [ ] Update import statements
- [ ] Test module dependencies
- [ ] Verify build process
- [ ] Test runtime behavior

### **From Monolithic**

- [ ] Extract shared code to shared module
- [ ] Split services into modules
- [ ] Update configuration
- [ ] Test service communication
- [ ] Verify data consistency

## 🎉 **Conclusion**

The multi-module architecture provides the best of both worlds:

- **Microservice benefits**: Independent scaling, fault isolation, technology flexibility
- **Monorepo benefits**: Easier development, unified testing, simplified deployment

This approach is particularly well-suited for:

- **Medium-sized teams** (5-20 developers)
- **Projects with clear service boundaries**
- **Organizations wanting microservices without operational complexity**
- **Teams transitioning from monolithic to microservice architecture**

The architecture scales with your needs and can evolve from a simple multi-module setup to a full microservice deployment as your requirements grow.
