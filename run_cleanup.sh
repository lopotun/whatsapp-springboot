#!/bin/bash

# Database cleanup script for WhatsApp Chat Viewer
# This script will clean up duplicate chat entries that violate the unique constraint

echo "Starting database cleanup for duplicate chat entries..."

# Check if we're in the right directory
if [ ! -f "pom.xml" ]; then
    echo "Error: Please run this script from the project root directory"
    exit 1
fi

# Check if PostgreSQL is running
if ! pg_isready -q; then
    echo "Error: PostgreSQL is not running. Please start PostgreSQL first."
    exit 1
fi

# Get database connection details from application.properties
DB_URL=$(grep "spring.datasource.url" src/main/resources/application.properties | cut -d'=' -f2 | tr -d ' ')
DB_USERNAME=$(grep "spring.datasource.username" src/main/resources/application.properties | cut -d'=' -f2 | tr -d ' ')
DB_PASSWORD=$(grep "spring.datasource.password" src/main/resources/application.properties | cut -d'=' -f2 | tr -d ' ')

if [ -z "$DB_URL" ] || [ -z "$DB_USERNAME" ]; then
    echo "Error: Could not read database configuration from application.properties"
    exit 1
fi

# Extract database name from URL
DB_NAME=$(echo "$DB_URL" | sed 's/.*\///')

echo "Database: $DB_NAME"
echo "Username: $DB_USERNAME"

# Run the cleanup script
echo "Running cleanup script..."
PGPASSWORD="$DB_PASSWORD" psql -h localhost -U "$DB_USERNAME" -d "$DB_NAME" -f cleanup_duplicates.sql

echo "Cleanup completed!"
echo "Please restart your application to ensure the changes take effect."
