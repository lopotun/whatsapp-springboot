# start-prod.sh
#!/bin/bash
export SPRING_PROFILES_ACTIVE=prod

# Check if .env.prod exists, if not exit with error
if [ -f ".env.prod" ]; then
    source .env.prod
    echo "✅ Loaded environment from .env.prod"
else
    echo "❌ Error: .env.prod not found!"
    echo "Please create .env.prod file with production environment variables"
    echo "You can copy from .env.example and update with production values"
    exit 1
fi

echo "Starting production environment..."
echo "Profile: $SPRING_PROFILES_ACTIVE"
echo "Database: $MAIN_SERVICE_DB_URL"

# Verify critical environment variables
if [ -z "$MAIN_SERVICE_DB_PASSWORD" ] || [ "$MAIN_SERVICE_DB_PASSWORD" = "your_secure_production_password" ]; then
    echo "❌ Error: Please set a secure production database password in .env.prod"
    exit 1
fi

if [ -z "$WHATSAPPCHATVIEWER_GOOGLE_CLIENT_ID" ] || [ "$WHATSAPPCHATVIEWER_GOOGLE_CLIENT_ID" = "your-production-google-client-id" ]; then
    echo "❌ Error: Please set production Google OAuth credentials in .env.prod"
    exit 1
fi

# Start main service
cd main-service
./mvnw spring-boot:run -Dspring.profiles.active=prod &
MAIN_PID=$!

# Start zipper service
cd ../zipper-service
./mvnw spring-boot:run -Dspring.profiles.active=prod &
ZIPPER_PID=$!

echo "Services started with PIDs: Main=$MAIN_PID, Zipper=$ZIPPER_PID"
echo "Main service: http://localhost:8080"
echo "Zipper service: http://localhost:8081"

# Wait for both services
wait $MAIN_PID $ZIPPER_PID
