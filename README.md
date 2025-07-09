# WhatsApp Chat Viewer Spring Boot Application

This application processes WhatsApp exported chats and provides functionality to search chat entries by keywords, attachment types, and timestamp ranges.

## Features

- Upload and process WhatsApp chat text files
- Upload zip files containing multimedia files and chat text
- Search chat entries by various criteria
- REST API for programmatic access

## API Endpoints

### Upload Chat Text File
```
POST /api/chat/upload
Content-Type: multipart/form-data

Parameter: file (text file containing WhatsApp chat export)
```

### Upload Zip File with Multimedia
```
POST /api/chat/upload-zip
Content-Type: multipart/form-data

Parameter: file (zip file containing multimedia files and chat text)
```

The zip file should contain:
- One text file (`.txt`, `.text`, or `.log`) with WhatsApp chat export
- Multiple multimedia files (images, videos, audio, documents, etc.)

The endpoint will:
1. Extract all multimedia files to the configured storage directory (`./multimedia-files` by default)
2. Process the chat text file and stream the parsed entries
3. Return a summary of extracted files

## Configuration

The multimedia storage directory can be configured in `application.properties`:

```properties
app.multimedia.storage.path=./multimedia-files
```

## Running the Application

1. Build the project:
   ```bash
   ./mvnw clean install
   ```

2. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```

3. The application will be available at `http://localhost:8080`

## Example Usage

### Upload a zip file with curl:
```bash
curl -X POST \
  http://localhost:8080/api/chat/upload-zip \
  -H 'Content-Type: multipart/form-data' \
  -F 'file=@your-chat-archive.zip'
```

The zip file should contain your WhatsApp chat export text file and any associated multimedia files that were shared in the chat. 