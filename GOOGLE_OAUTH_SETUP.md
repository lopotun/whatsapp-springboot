# Google OAuth2 Setup Guide

This guide explains how to set up Google OAuth2 authentication for the WhatsApp Chat Viewer application.

## Prerequisites

1. A Google Cloud Platform account
2. Access to Google Cloud Console
3. The application running locally or deployed

## Step 1: Create a Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Enable the Google+ API (if not already enabled)

## Step 2: Configure OAuth Consent Screen

1. In the Google Cloud Console, go to **APIs & Services** > **OAuth consent screen**
2. Choose **External** user type (unless you have a Google Workspace organization)
3. Fill in the required information:
   - App name: "WhatsApp Chat Viewer"
   - User support email: Your email
   - Developer contact information: Your email
4. Add the following scopes:
   - `openid`
   - `profile`
   - `email`
5. Add test users if you're in testing mode
6. Save and continue

## Step 3: Create OAuth 2.0 Credentials

1. Go to **APIs & Services** > **Credentials**
2. Click **Create Credentials** > **OAuth 2.0 Client IDs**
3. Choose **Web application** as the application type
4. Set the following redirect URIs:
   - For local development: `http://localhost:8080/login/oauth2/code/google`
   - For production: `https://yourdomain.com/login/oauth2/code/google`
5. Click **Create**
6. Note down the **Client ID** and **Client Secret**

## Step 4: Configure Environment Variables

Set the following environment variables in your system or deployment environment:

```bash
export GOOGLE_CLIENT_ID="your-google-client-id"
export GOOGLE_CLIENT_SECRET="your-google-client-secret"
```

### For Local Development

Create a `.env` file in your project root:

```bash
GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret
```

### For Production

Set the environment variables in your production environment or use your deployment platform's configuration.

## Step 5: Update Application Properties (Alternative)

If you prefer not to use environment variables, you can directly update `src/main/resources/application.properties`:

```properties
spring.security.oauth2.client.registration.google.client-id=your-google-client-id
spring.security.oauth2.client.registration.google.client-secret=your-google-client-secret
```

**Note**: This approach is not recommended for production as it exposes sensitive credentials in your code.

## Step 6: Test the Integration

1. Start your application
2. Navigate to the login page
3. Click the "Sign in with Google" button
4. Complete the Google OAuth flow
5. You should be redirected to the dashboard upon successful authentication

## Security Considerations

1. **Never commit credentials to version control**
2. **Use environment variables for production deployments**
3. **Regularly rotate your OAuth client secrets**
4. **Monitor OAuth usage in Google Cloud Console**
5. **Implement proper session management**

## Troubleshooting

### Common Issues

1. **"Invalid redirect URI" error**

   - Ensure the redirect URI in Google Cloud Console matches exactly with your application
   - Check for trailing slashes or protocol mismatches

2. **"OAuth consent screen not configured" error**

   - Make sure you've completed the OAuth consent screen setup
   - Verify that the Google+ API is enabled

3. **"Client ID not found" error**

   - Double-check your environment variables
   - Ensure the application has been restarted after setting environment variables

4. **"Scope not allowed" error**
   - Verify that all required scopes are added to the OAuth consent screen
   - Check that your test users (if applicable) have access

### Debug Mode

Enable debug logging by adding this to your `application.properties`:

```properties
logging.level.org.springframework.security.oauth2=DEBUG
logging.level.org.springframework.security=DEBUG
```

## API Endpoints

The following OAuth2 endpoints are automatically configured:

- **Authorization**: `/oauth2/authorization/google`
- **Callback**: `/login/oauth2/code/google`
- **Login page**: `/login`

## User Management

When users authenticate via Google OAuth2:

1. A new user account is automatically created if it doesn't exist
2. User information (name, email, profile picture) is extracted from Google
3. The user is assigned the `USER` role by default
4. Users can still use traditional username/password authentication

## Future Enhancements

The current implementation supports:

- Google OAuth2 authentication
- Automatic user creation
- Profile information synchronization

Potential future enhancements:

- Additional OAuth providers (Facebook, Twitter, Apple)
- Role-based access control for OAuth users
- Profile picture synchronization
- Account linking (OAuth + local accounts)
