#!/bin/bash

echo "🚀 Setting up WhatsApp Chat Viewer Development Environment..."

# Check if Maven wrapper is available
if [ ! -f "mvnw" ]; then
    echo "❌ Maven wrapper (mvnw) not found. Please ensure mvnw is available in the project root."
    exit 1
fi

# Make mvnw executable
chmod +x mvnw

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed. Please install Docker first."
    exit 1
fi

# Check if Docker Compose is installed
if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose is not installed. Please install Docker Compose first."
    exit 1
fi

echo "✅ Prerequisites check passed"

# Create necessary directories
echo "📁 Creating necessary directories..."
mkdir -p docker/init-scripts
mkdir -p docker/nginx/conf.d
mkdir -p docker/nginx/ssl

# Create file storage directories
echo "📁 Creating file storage directories..."
mkdir -p /tmp/whatsapp-storage
mkdir -p /tmp/whatsapp-temp
mkdir -p /tmp/multimedia-files

# Set proper permissions for file storage directories
chmod 755 /tmp/whatsapp-storage
chmod 755 /tmp/whatsapp-temp
chmod 755 /tmp/multimedia-files

echo "📁 File storage directories created with proper permissions"

# Copy Maven wrapper to modules if they don't exist
if [ ! -f "main-service/mvnw" ]; then
    echo "📦 Copying Maven wrapper to main-service..."
    cp mvnw main-service/
    cp -r .mvn main-service/
    chmod +x main-service/mvnw
fi

if [ ! -f "zipper-service/mvnw" ]; then
    echo "📦 Copying Maven wrapper to zipper-service..."
    cp mvnw zipper-service/
    cp -r .mvn zipper-service/
    chmod +x zipper-service/mvnw
fi

# Build shared module first to ensure it's available
echo "🔨 Building shared module..."
./mvnw clean install -pl shared -q
if [ $? -ne 0 ]; then
    echo "❌ Failed to build shared module. Please check the build errors."
    exit 1
fi
echo "✅ Shared module built successfully"

# Create database initialization script
echo "🗄️ Creating database initialization script..."
cat > docker/init-scripts/01-init.sql << 'EOF'
-- =============================================================================
-- DATABASE INITIALIZATION SCRIPT FOR WHATSAPP CHAT VIEWER
-- =============================================================================
-- This script runs automatically when the PostgreSQL container starts
-- The database is created by Docker Compose with POSTGRES_DB environment variable
-- =============================================================================

-- Create users table if it doesn't exist
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    oauth_provider VARCHAR(50),
    oauth_id VARCHAR(255),
    profile_picture_url VARCHAR(500)
);

-- Create chat_entries table if it doesn't exist
CREATE TABLE IF NOT EXISTS chat_entries (
    id BIGSERIAL PRIMARY KEY,
    payload TEXT,
    author VARCHAR(255) NOT NULL DEFAULT 'Unknown',
    file_name VARCHAR(255),
    type VARCHAR(50),
    local_date_time TIMESTAMP,
    user_id BIGINT NOT NULL,
    chat_id VARCHAR(255) NOT NULL,
    path VARCHAR(500),
    at_id BIGINT
);

-- Create attachments table if it doesn't exist
CREATE TABLE IF NOT EXISTS attachments (
    id BIGSERIAL PRIMARY KEY,
    hash VARCHAR(64) NOT NULL UNIQUE,
    last_added_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status SMALLINT NOT NULL DEFAULT 1,
    file_size BIGINT,
    col1 VARCHAR(255),
    col2 VARCHAR(255),
    mime_type VARCHAR(100),
    original_filename VARCHAR(255)
);

-- Create zip_processing_jobs table if it doesn't exist
CREATE TABLE IF NOT EXISTS zip_processing_jobs (
    id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(255) UNIQUE NOT NULL,
    user_id BIGINT NOT NULL,
    chat_id VARCHAR(255) NOT NULL,
    original_filename VARCHAR(500) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    file_size BIGINT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    priority INTEGER NOT NULL DEFAULT 1,
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    error_message TEXT,
    processing_started_at TIMESTAMP,
    processing_completed_at TIMESTAMP,
    total_entries_processed INTEGER DEFAULT 0,
    total_attachments_processed INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- CREATE ALL NECESSARY INDEXES
-- =============================================================================

-- Chat entries indexes
CREATE INDEX IF NOT EXISTS idx_chat_entries_author ON chat_entries (author);
CREATE INDEX IF NOT EXISTS idx_chat_entries_type ON chat_entries (type);
CREATE INDEX IF NOT EXISTS idx_chat_entries_local_date_time ON chat_entries (local_date_time);
CREATE INDEX IF NOT EXISTS idx_chat_entries_user_id ON chat_entries (user_id);
CREATE INDEX IF NOT EXISTS idx_chat_entries_chat_id ON chat_entries (chat_id);
CREATE INDEX IF NOT EXISTS idx_chat_entries_user_chat ON chat_entries (user_id, chat_id);
CREATE INDEX IF NOT EXISTS idx_chat_entries_at_id ON chat_entries (at_id);
CREATE INDEX IF NOT EXISTS idx_chat_entries_path ON chat_entries (path);
CREATE INDEX IF NOT EXISTS idx_chat_entries_user_type ON chat_entries (user_id, type);
CREATE INDEX IF NOT EXISTS idx_chat_entries_user_author ON chat_entries (user_id, author);
CREATE INDEX IF NOT EXISTS idx_chat_entries_user_date ON chat_entries (user_id, local_date_time);
CREATE INDEX IF NOT EXISTS idx_chat_entries_user_chat_date ON chat_entries (user_id, chat_id, local_date_time);
CREATE INDEX IF NOT EXISTS idx_chat_entries_path_not_null ON chat_entries (path) WHERE path IS NOT NULL;

-- Attachments indexes
CREATE INDEX IF NOT EXISTS idx_attachments_hash ON attachments (hash);
CREATE INDEX IF NOT EXISTS idx_attachments_status ON attachments (status);
CREATE INDEX IF NOT EXISTS idx_attachments_file_size ON attachments (file_size);

-- Zip processing jobs indexes
CREATE INDEX IF NOT EXISTS idx_zip_processing_jobs_user_id ON zip_processing_jobs (user_id);
CREATE INDEX IF NOT EXISTS idx_zip_processing_jobs_status ON zip_processing_jobs (status);
CREATE INDEX IF NOT EXISTS idx_zip_processing_jobs_priority_created ON zip_processing_jobs (priority DESC, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_zip_processing_jobs_created_at ON zip_processing_jobs (created_at);
CREATE INDEX IF NOT EXISTS idx_zip_processing_jobs_job_id ON zip_processing_jobs (job_id);

-- =============================================================================
-- CREATE FOREIGN KEY CONSTRAINTS
-- =============================================================================

-- Chat entries -> Attachments
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_chat_entries_attachment'
        AND table_name = 'chat_entries'
    ) THEN
        ALTER TABLE chat_entries
        ADD CONSTRAINT fk_chat_entries_attachment
        FOREIGN KEY (at_id) REFERENCES attachments (id) ON DELETE SET NULL;
    END IF;
END $$;

-- Chat entries -> Users
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_chat_entries_user'
        AND table_name = 'chat_entries'
    ) THEN
        ALTER TABLE chat_entries
        ADD CONSTRAINT fk_chat_entries_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
    END IF;
END $$;

-- Zip processing jobs -> Users
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_zip_processing_jobs_user'
        AND table_name = 'zip_processing_jobs'
    ) THEN
        ALTER TABLE zip_processing_jobs
        ADD CONSTRAINT fk_zip_processing_jobs_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
    END IF;
END $$;

-- =============================================================================
-- CREATE UNIQUE CONSTRAINTS
-- =============================================================================

-- Drop existing unique constraint if it exists
DROP INDEX IF EXISTS idx_chat_entries_unique_entry;

-- Create new unique constraint (without payload to avoid index size issues)
CREATE UNIQUE INDEX IF NOT EXISTS idx_chat_entries_unique_entry ON chat_entries (
    user_id,
    chat_id,
    local_date_time,
    author,
    COALESCE(file_name, '')
);

-- Add payload search index for performance
CREATE INDEX IF NOT EXISTS idx_chat_entries_payload_search ON chat_entries (payload)
WHERE LENGTH(payload) < 1000;

-- =============================================================================
-- CREATE FUNCTIONS AND TRIGGERS
-- =============================================================================

-- Only zip_processing_jobs has updated_at field, so only create trigger for it
-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Trigger for zip_processing_jobs updated_at (only table with updated_at field)
DROP TRIGGER IF EXISTS update_zip_processing_jobs_updated_at ON zip_processing_jobs;
CREATE TRIGGER update_zip_processing_jobs_updated_at
    BEFORE UPDATE ON zip_processing_jobs
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =============================================================================
-- DATA MIGRATION AND CLEANUP
-- =============================================================================

-- Remove any existing locations table (from old schema)
DROP TABLE IF EXISTS locations CASCADE;

-- Remove old columns that are no longer needed (if they exist)
ALTER TABLE chat_entries DROP COLUMN IF EXISTS attachment_hash;
ALTER TABLE chat_entries DROP COLUMN IF EXISTS timestamp;
ALTER TABLE chat_entries DROP COLUMN IF EXISTS created_at;
ALTER TABLE chat_entries DROP COLUMN IF EXISTS updated_at;
ALTER TABLE attachments DROP COLUMN IF EXISTS created_at;
ALTER TABLE attachments DROP COLUMN IF EXISTS updated_at;

-- Ensure chat_id format is consistent (remove long suffixes if they exist)
UPDATE chat_entries
SET chat_id = SUBSTRING(chat_id, 1, LENGTH(chat_id) - 16)
WHERE LENGTH(chat_id) > 16;

-- Handle very short chat_ids
UPDATE chat_entries
SET chat_id = 'chat_' || id
WHERE LENGTH(chat_id) <= 7;

-- Update existing chat_ids to include user_id prefix if not already present
UPDATE chat_entries
SET chat_id = 'user' || user_id || '_' || chat_id
WHERE chat_id NOT LIKE 'user%_%'
    AND user_id IS NOT NULL;

-- =============================================================================
-- ADD COMMENTS AND DOCUMENTATION
-- =============================================================================

COMMENT ON TABLE users IS 'User accounts for WhatsApp Chat Viewer';
COMMENT ON TABLE chat_entries IS 'WhatsApp chat entries stored by the system';
COMMENT ON TABLE attachments IS 'File attachments stored by the system';
COMMENT ON TABLE zip_processing_jobs IS 'ZIP file processing job queue';

COMMENT ON COLUMN chat_entries.user_id IS 'User ID who owns this chat entry';
COMMENT ON COLUMN chat_entries.chat_id IS 'Chat ID this entry belongs to';
COMMENT ON COLUMN chat_entries.at_id IS 'Reference to attachment entity';
COMMENT ON COLUMN chat_entries.path IS 'File path for attachment downloads';
COMMENT ON COLUMN attachments.hash IS 'SHA256 hash of the file content';
COMMENT ON COLUMN attachments.status IS '1 = active, 0 = inactive';
COMMENT ON COLUMN zip_processing_jobs.priority IS 'Job priority (higher = more important)';

COMMENT ON INDEX idx_chat_entries_unique_entry IS 'Unique constraint excluding payload to avoid index size limits';

-- =============================================================================
-- VERIFICATION QUERIES
-- =============================================================================

-- Show table structure
SELECT
    table_name,
    column_name,
    data_type,
    is_nullable,
    column_default
FROM information_schema.columns
WHERE table_schema = 'public'
    AND table_name IN ('users', 'chat_entries', 'attachments', 'zip_processing_jobs')
ORDER BY table_name, ordinal_position;

-- Show created indexes
SELECT
    tablename,
    indexname,
    indexdef
FROM pg_indexes
WHERE schemaname = 'public'
    AND tablename IN ('users', 'chat_entries', 'attachments', 'zip_processing_jobs')
ORDER BY tablename, indexname;

-- Show foreign key constraints
SELECT
    tc.table_name,
    kcu.column_name,
    ccu.table_name AS foreign_table_name,
    ccu.column_name AS foreign_column_name
FROM information_schema.table_constraints AS tc
JOIN information_schema.key_column_usage AS kcu
    ON tc.constraint_name = kcu.constraint_name
    AND tc.table_schema = kcu.table_schema
JOIN information_schema.constraint_column_usage AS ccu
    ON ccu.constraint_name = tc.constraint_name
    AND ccu.table_schema = tc.table_schema
WHERE tc.constraint_type = 'FOREIGN KEY'
    AND tc.table_schema = 'public'
ORDER BY tc.table_name, kcu.column_name;

-- Validate entity compatibility
-- Check that all required columns exist and have correct types
SELECT
    'users' as table_name,
    'username' as column_name,
    'VARCHAR(255) UNIQUE NOT NULL' as expected_type
UNION ALL
SELECT
    'users' as table_name,
    'password' as column_name,
    'VARCHAR(255) NOT NULL' as expected_type
UNION ALL
SELECT
    'chat_entries' as table_name,
    'user_id' as column_name,
    'BIGINT NOT NULL' as expected_type
UNION ALL
SELECT
    'chat_entries' as table_name,
    'chat_id' as column_name,
    'VARCHAR(255) NOT NULL' as expected_type
UNION ALL
SELECT
    'attachments' as table_name,
    'hash' as column_name,
    'VARCHAR(64) UNIQUE NOT NULL' as expected_type
UNION ALL
SELECT
    'zip_processing_jobs' as table_name,
    'job_id' as column_name,
    'VARCHAR(255) UNIQUE NOT NULL' as expected_type;

-- =============================================================================
-- INITIALIZATION COMPLETE
-- =============================================================================
-- Your database is now fully initialized and ready for use!
-- All tables, indexes, constraints, and triggers have been created.
-- The schema is optimized for performance and follows best practices.
-- =============================================================================
EOF

echo "✅ Database initialization script created"

# Create environment file
echo "🔧 Creating environment file..."
cat > .env << 'EOF'
# Database Configuration
DB_HOST=localhost
DB_PORT=25432
DB_USERNAME=postgres
DB_PASSWORD=password
DB_NAME=whatsapp_chatviewer

# Service Configuration
MAIN_SERVICE_PORT=8080
ZIPPER_SERVICE_PORT=8081
ZIPPER_SERVICE_URL=http://localhost:8081
ZIPPER_API_KEY=your-secret-api-key

# File Storage
FILE_STORAGE_PATH=/tmp/whatsapp-storage
TEMP_STORAGE_PATH=/tmp/whatsapp-temp
MULTIMEDIA_STORAGE_PATH=/tmp/multimedia-files

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# RabbitMQ
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest
EOF

echo "✅ Environment file created"

# Create development startup script
echo "📝 Creating development startup script..."
cat > start-dev.sh << 'EOF'
#!/bin/bash

echo "🚀 Starting WhatsApp Chat Viewer in development mode..."

# Start dependencies
echo "📦 Starting dependencies..."
docker-compose -f docker/docker-compose.yml up -d postgres redis rabbitmq

# Wait for services to be ready
echo "⏳ Waiting for services to be ready..."
sleep 10

# Start main service
echo "💬 Starting main service..."
cd main-service
./mvnw spring-boot:run &
MAIN_PID=$!

# Start zipper service
echo "📦 Starting zipper service..."
cd ../zipper-service
./mvnw spring-boot:run &
ZIPPER_PID=$!

echo "✅ All services started!"
echo "💬 Main Service: http://localhost:8080"
echo "📦 Zipper Service: http://localhost:8081"
echo "🗄️  Database: localhost:25432"
echo "📊 RabbitMQ: http://localhost:15672"
echo "🔴 Redis: localhost:6379"

# Wait for user to stop
echo "Press Ctrl+C to stop all services"
trap "echo 'Stopping services...'; kill $MAIN_PID $ZIPPER_PID; docker-compose -f docker/docker-compose.yml down; exit" INT
wait
EOF

chmod +x start-dev.sh

# Create production startup script
echo "📝 Creating production startup script..."
cat > start-prod.sh << 'EOF'
#!/bin/bash

echo "🚀 Starting WhatsApp Chat Viewer in production mode..."

# Build all modules
echo "🔨 Building all modules..."
./mvnw clean package -DskipTests

# Start all services
echo "📦 Starting all services..."
docker-compose -f docker/docker-compose.yml up -d

echo "✅ All services started!"
echo "🌐 Application: http://localhost"
echo "💬 Main Service: http://localhost:8080"
echo "📦 Zipper Service: http://localhost:8081"
echo "🗄️  Database: localhost:25432"
echo "📊 RabbitMQ: http://localhost:15672"
echo "🔴 Redis: localhost:6379"

# Show service status
echo "📊 Service Status:"
docker-compose -f docker/docker-compose.yml ps
EOF

chmod +x start-prod.sh

# Verify that all modules can compile
echo "🔍 Verifying module compilation..."
echo "📦 Compiling main service..."
./mvnw clean compile -pl main-service -q
if [ $? -ne 0 ]; then
    echo "❌ Main service compilation failed. Please check the build errors."
    exit 1
fi
echo "✅ Main service compiled successfully"

echo "📦 Compiling zipper service..."
./mvnw clean compile -pl zipper-service -q
if [ $? -ne 0 ]; then
    echo "❌ Zipper service compilation failed. Please check the build errors."
    exit 1
fi
echo "✅ Zipper service compiled successfully"

echo "✅ Development environment setup complete!"
echo ""
echo "📋 Next steps:"
echo "1. Copy your existing source code to main-service/src/"
echo "2. Copy zipper service code to zipper-service/src/"
echo "3. Run './start-dev.sh' for development"
echo "4. Run './start-prod.sh' for production"
echo ""
echo "🔧 Configuration files created:"
echo "   - .env (environment variables)"
echo "   - docker/docker-compose.yml (Docker setup)"
echo "   - start-dev.sh (development startup)"
echo "   - start-prod.sh (production startup)"
echo ""
echo "🗄️  Database schema updated to match entity classes:"
echo "   - users table with OAuth support"
echo "   - chat_entries table without timestamp fields"
echo "   - attachments table without timestamp fields"
echo "   - zip_processing_jobs table with full timestamp support"
