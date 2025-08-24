#!/bin/bash

# WhatsApp Chat Viewer - Environment Setup Script
# This script helps set up environment variables for development

echo "🔐 WhatsApp Chat Viewer - Environment Setup"
echo "============================================="

# Check if .env file exists
if [ -f ".env" ]; then
    echo "⚠️  .env file already exists. Backing up to .env.backup"
    cp .env .env.backup
fi

# Create .env file from template
if [ -f "env.example" ]; then
    cp env.example .env
    echo "✅ Created .env file from template"
else
    echo "❌ env.example not found. Creating basic .env file"
    cat > .env << EOF
# Database Configuration
WHATSAPPCHATVIEWER_DATABASE_URL=jdbc:postgresql://localhost:5432/whatsapp_chatviewer
WHATSAPPCHATVIEWER_DB_USERNAME=postgres
WHATSAPPCHATVIEWER_DB_PASSWORD=password

# Admin User Configuration
WHATSAPPCHATVIEWER_ADMIN_USERNAME=admin
WHATSAPPCHATVIEWER_ADMIN_PASSWORD=password

# Google OAuth2 Configuration
WHATSAPPCHATVIEWER_GOOGLE_CLIENT_ID=your-google-client-id-here
WHATSAPPCHATVIEWER_GOOGLE_CLIENT_SECRET=your-google-client-secret-here

# File Storage Configuration
MULTIMEDIA_STORAGE_PATH=./multimedia-files

# Server Configuration
SERVER_PORT=8080
SSL_ENABLED=false
SESSION_TIMEOUT=30m
EOF
fi

echo ""
echo "📝 Please edit the .env file with your actual values:"
echo "   nano .env"
echo ""
echo "🔑 Required values to set:"
echo "   - WHATSAPPCHATVIEWER_DB_PASSWORD: Your database password"
echo "   - WHATSAPPCHATVIEWER_ADMIN_PASSWORD: Your admin user password"
echo "   - WHATSAPPCHATVIEWER_GOOGLE_CLIENT_ID: Your Google OAuth2 client ID"
echo "   - WHATSAPPCHATVIEWER_GOOGLE_CLIENT_SECRET: Your Google OAuth2 client secret"
echo ""
echo "💡 After editing, source the file:"
echo "   source .env"
echo ""
echo "🚀 Then start the application:"
echo "   ./mvnw spring-boot:run"
echo ""
echo "⚠️  Remember: Never commit .env files to version control!"
