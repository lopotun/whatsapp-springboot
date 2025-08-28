#!/bin/bash
export SPRING_PROFILES_ACTIVE=dev

# Check if .env.dev exists, if not create default values
if [ -f ".env.dev" ]; then
    source .env.dev
    echo "✅ Loaded environment from .env.dev"
else
    echo "⚠️  Warning: .env.dev not found, using default development values"
    export MAIN_SERVICE_DB_URL="jdbc:postgresql://localhost:25432/whatsapp_chatviewer_dev"
    export ZIPPER_SERVICE_DB_URL="jdbc:postgresql://localhost:25432/whatsapp_chatviewer_dev"
    export MAIN_SERVICE_DB_USERNAME="postgres"
    export MAIN_SERVICE_DB_PASSWORD="password"
    export ZIPPER_SERVICE_URL="http://localhost:8081"
    export ZIPPER_SERVICE_API_KEY="dev-api-key"
    export FILE_STORAGE_PATH="./dev-storage"
    export TEMP_STORAGE_PATH="./dev-temp"
    export MULTIMEDIA_STORAGE_PATH="./dev-multimedia"
    export LOG_LEVEL_APP="DEBUG"
    export JPA_SHOW_SQL="true"
    export HIBERNATE_FORMAT_SQL="true"
    export JPA_DDL_AUTO="create-drop"
fi

echo "Starting development environment..."
echo "Profile: $SPRING_PROFILES_ACTIVE"
echo "Database: $MAIN_SERVICE_DB_URL"

# Create development directories
mkdir -p ./dev-storage ./dev-temp ./dev-multimedia

# Start main service with environment variables and remote debugging
cd main-service || exit
echo "🚀 Starting Main Service with remote debugging on port 5005..."
MAIN_SERVICE_DB_URL="$MAIN_SERVICE_DB_URL" \
MAIN_SERVICE_DB_USERNAME="$MAIN_SERVICE_DB_USERNAME" \
MAIN_SERVICE_DB_PASSWORD="$MAIN_SERVICE_DB_PASSWORD" \
ZIPPER_SERVICE_URL="$ZIPPER_SERVICE_URL" \
ZIPPER_SERVICE_API_KEY="$ZIPPER_SERVICE_API_KEY" \
FILE_STORAGE_PATH="$FILE_STORAGE_PATH" \
TEMP_STORAGE_PATH="$TEMP_STORAGE_PATH" \
MULTIMEDIA_STORAGE_PATH="$MULTIMEDIA_STORAGE_PATH" \
LOG_LEVEL_APP="$LOG_LEVEL_APP" \
LOG_LEVEL_SECURITY="$LOG_LEVEL_SECURITY" \
JPA_SHOW_SQL="$JPA_SHOW_SQL" \
HIBERNATE_FORMAT_SQL="$HIBERNATE_FORMAT_SQL" \
JPA_DDL_AUTO="$JPA_DDL_AUTO" \
WHATSAPPCHATVIEWER_GOOGLE_CLIENT_ID="$WHATSAPPCHATVIEWER_GOOGLE_CLIENT_ID" \
WHATSAPPCHATVIEWER_GOOGLE_CLIENT_SECRET="$WHATSAPPCHATVIEWER_GOOGLE_CLIENT_SECRET" \
./mvnw spring-boot:run \
  -Dspring.profiles.active=dev \
  -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" &
MAIN_PID=$!

# Start zipper service with environment variables and remote debugging
cd ../zipper-service || exit
echo "🚀 Starting Zipper Service with remote debugging on port 5006..."
MAIN_SERVICE_DB_URL="$MAIN_SERVICE_DB_URL" \
MAIN_SERVICE_DB_USERNAME="$MAIN_SERVICE_DB_USERNAME" \
MAIN_SERVICE_DB_PASSWORD="$MAIN_SERVICE_DB_PASSWORD" \
ZIPPER_SERVICE_URL="$ZIPPER_SERVICE_URL" \
ZIPPER_SERVICE_API_KEY="$ZIPPER_SERVICE_API_KEY" \
FILE_STORAGE_PATH="$FILE_STORAGE_PATH" \
TEMP_STORAGE_PATH="$TEMP_STORAGE_PATH" \
MULTIMEDIA_STORAGE_PATH="$MULTIMEDIA_STORAGE_PATH" \
LOG_LEVEL_APP="$LOG_LEVEL_APP" \
LOG_LEVEL_SECURITY="$LOG_LEVEL_SECURITY" \
JPA_SHOW_SQL="$JPA_SHOW_SQL" \
HIBERNATE_FORMAT_SQL="$HIBERNATE_FORMAT_SQL" \
JPA_DDL_AUTO="$JPA_DDL_AUTO" \
./mvnw spring-boot:run \
  -Dspring.profiles.active=dev \
  -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5006" &
ZIPPER_PID=$!

echo ""
echo "🎯 Services started with PIDs: Main=$MAIN_PID, Zipper=$ZIPPER_PID"
echo "🌐 Main service: http://localhost:8080"
echo "🌐 Zipper service: http://localhost:8081"
echo ""
echo "🔍 Remote Debugging Enabled:"
echo "   Main Service: localhost:5005"
echo "   Zipper Service: localhost:5006"
echo ""
echo "💡 To connect debugger in your IDE:"
echo "   - Main Service: localhost:5005"
echo "   - Zipper Service: localhost:5006"
echo ""

# Wait for both services
wait $MAIN_PID $ZIPPER_PID
