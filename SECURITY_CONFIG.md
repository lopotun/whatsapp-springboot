# Security Configuration Guide

## 🔐 **Sensitive Data Protection**

This application has been configured to use environment variables for all sensitive configuration data. **Never commit real credentials to version control!**

## 📋 **Required Environment Variables**

### Database Configuration
```bash
export DB_USERNAME=your_database_username
export DB_PASSWORD=your_secure_database_password
```

### Admin User Configuration
```bash
export ADMIN_USERNAME=your_admin_username
export ADMIN_PASSWORD=your_secure_admin_password
```

### Google OAuth2 Configuration
```bash
export GOOGLE_CLIENT_ID=your-google-oauth2-client-id
export GOOGLE_CLIENT_SECRET=your-google-oauth2-client-secret
```

## 🚀 **Setup Instructions**

### Option 1: Environment Variables (Recommended)
```bash
# Set environment variables before starting the application
export DB_USERNAME=postgres
export DB_PASSWORD=my_secure_password
export ADMIN_USERNAME=admin
export ADMIN_PASSWORD=admin_secure_password
export GOOGLE_CLIENT_ID=123456789-abcdef.apps.googleusercontent.com
export GOOGLE_CLIENT_SECRET=GOCSPX-YourSecretHere

# Start the application
./mvnw spring-boot:run
```

### Option 2: .env File (Development Only)
```bash
# Create a .env file (add to .gitignore!)
cp env.example .env

# Edit .env with your real values
nano .env

# Source the file
source .env
```

### Option 3: System Properties
```bash
./mvnw spring-boot:run \
  -Dspring.datasource.username=postgres \
  -Dspring.datasource.password=my_password \
  -Dspring.security.oauth2.client.registration.google.client-id=your_id \
  -Dspring.security.oauth2.client.registration.google.client-secret=your_secret
```

## ⚠️ **Security Best Practices**

1. **Never commit real credentials** to version control
2. **Use strong, unique passwords** for each environment
3. **Rotate credentials regularly** (especially OAuth2 secrets)
4. **Use environment-specific configurations** (dev/staging/prod)
5. **Limit database user permissions** to minimum required
6. **Enable HTTPS** in production environments
7. **Use secrets management** services in production (AWS Secrets Manager, HashiCorp Vault, etc.)

## 🔍 **Verification**

To verify your configuration is working:

1. Check application logs for configuration errors
2. Test database connection on startup
3. Verify OAuth2 login flow works
4. Confirm admin user can authenticate

## 🆘 **Troubleshooting**

### Common Issues:
- **"Invalid client"**: Check GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET
- **"Database connection failed"**: Verify DB_USERNAME and DB_PASSWORD
- **"Authentication failed"**: Check ADMIN_USERNAME and ADMIN_PASSWORD

### Debug Mode:
```bash
# Enable debug logging for configuration issues
export LOGGING_LEVEL_NET_KEM_WHATSAPP=DEBUG
export LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY=DEBUG
```

## 📚 **Additional Resources**

- [Spring Boot External Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config)
- [Spring Security OAuth2](https://docs.spring.io/spring-security/site/docs/current/reference/html5/#oauth2login)
- [PostgreSQL Security](https://www.postgresql.org/docs/current/security.html)

