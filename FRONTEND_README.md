# WhatsApp Chat Viewer - Frontend

A modern, responsive web interface for the WhatsApp Chat Viewer application built with Spring Boot, Thymeleaf, Bootstrap, and JavaScript.

## Features

### 🏠 Home Page
- **Welcome Dashboard**: Overview of the application with quick action buttons
- **Statistics Cards**: Display total messages, participants, attachments, and date range
- **Recent Messages**: Show the latest 5 chat entries with author, type, and timestamp
- **Feature Overview**: Highlight key capabilities of the application
- **Quick Actions**: Direct links to upload, search, attachments, and statistics

### 📤 Upload Page
- **Dual Upload Modes**: Support for both chat text files (.txt) and ZIP archives
- **Drag & Drop Interface**: Modern file upload with visual feedback
- **Progress Tracking**: Real-time upload progress with percentage and status
- **Results Display**: Show processed entries count and extracted files
- **Error Handling**: User-friendly error messages and validation

### 🔍 Search Page
- **Advanced Search Filters**:
  - Keyword search in message content and author names
  - Author filter for specific participants
  - Message type filter (Text, Image, Video, Audio, Document)
  - Date range selection with datetime pickers
  - Attachment presence filter
  - Results per page selection
- **Quick Search Presets**:
  - Recent messages (last 24 hours)
  - Media messages (with attachments)
  - Images only
  - Videos only
- **Results Display**:
  - Paginated results with navigation
  - Message cards with author, type, timestamp, and content
  - Attachment information when available
  - Responsive design for mobile and desktop

### 📎 Attachments Page
- **Attachment Management**:
  - Search by file hash
  - Filter by status (Active, Inactive, Deleted)
  - Filter by client ID
  - View attachment details in modal
- **Statistics Overview**:
  - Total attachments count
  - Active attachments
  - File locations count
  - Unique clients
- **File Locations Table**:
  - Real filename display
  - Client ID information
  - Status management
  - Last added timestamp

### 📊 Statistics Page
- **Overview Statistics**:
  - Total messages count
  - Unique participants
  - Total attachments
  - Date range coverage
- **Interactive Charts**:
  - Message types distribution (pie chart)
  - Top participants by message count (bar chart)
  - Message activity over time (line chart)
  - Attachment types breakdown (doughnut chart)
- **Detailed Breakdowns**:
  - Message type statistics
  - Participant activity analysis
  - Attachment statistics

## Technology Stack

### Backend
- **Spring Boot 3.5.0**: Main application framework
- **Spring Security**: Security configuration (disabled for demo)
- **Spring Data JPA**: Database access layer
- **Thymeleaf**: Server-side templating engine
- **H2 Database**: In-memory database for development
- **PostgreSQL**: Production database (configured)

### Frontend
- **Bootstrap 5.3.0**: CSS framework for responsive design
- **Bootstrap Icons**: Icon library
- **Chart.js**: Interactive charts and visualizations
- **Vanilla JavaScript**: Custom functionality
- **CSS3**: Custom styling with CSS variables

### Key Features
- **Responsive Design**: Works on desktop, tablet, and mobile devices
- **Modern UI**: Clean, professional interface with WhatsApp-inspired colors
- **Real-time Updates**: Live progress tracking and dynamic content loading
- **Accessibility**: Proper ARIA labels and keyboard navigation
- **Error Handling**: Comprehensive error messages and validation
- **Performance**: Optimized loading and efficient data handling

## Getting Started

### Prerequisites
- Java 21 or higher
- Maven 3.6 or higher
- PostgreSQL (optional, H2 is used by default)

### Running the Application

1. **Clone the repository**:
   ```bash
   git clone <repository-url>
   cd whatsapp-springboot
   ```

2. **Start the application**:
   ```bash
   ./mvnw spring-boot:run
   ```

3. **Access the web interface**:
   Open your browser and navigate to `http://localhost:8080`

### Using the Frontend

1. **Upload Chat Files**:
   - Go to the Upload page
   - Choose between chat text file or ZIP archive
   - Drag and drop files or click to browse
   - Monitor upload progress
   - View processing results

2. **Search Messages**:
   - Navigate to the Search page
   - Use filters to narrow down results
   - Try quick search presets
   - Browse paginated results
   - Click on messages for details

3. **Manage Attachments**:
   - Visit the Attachments page
   - Search and filter attachments
   - View attachment details
   - Manage file locations

4. **View Statistics**:
   - Go to the Statistics page
   - Explore interactive charts
   - Review detailed breakdowns
   - Analyze chat patterns

## API Endpoints

The frontend communicates with the following REST API endpoints:

### Chat Management
- `POST /api/chat/upload` - Upload chat text file
- `POST /api/chat/upload-zip` - Upload ZIP archive with multimedia

### Chat Entries
- `GET /api/chat-entries` - Get all entries with pagination
- `GET /api/chat-entries/search` - Search with multiple criteria
- `GET /api/chat-entries/search/keyword` - Keyword search
- `GET /api/chat-entries/search/advanced` - Advanced search

### Attachments
- `GET /api/attachments/hash/{hash}` - Get attachment by hash
- `GET /api/attachments/status/{status}` - Get attachments by status
- `GET /api/attachments/locations/client/{clientId}` - Get locations by client
- `PUT /api/attachments/hash/{hash}/status/{status}` - Update attachment status

## Customization

### Styling
The application uses CSS variables for easy customization:

```css
:root {
    --primary-color: #25D366;    /* WhatsApp green */
    --secondary-color: #128C7E;  /* Dark green */
    --accent-color: #34B7F1;     /* Blue */
    --dark-color: #075E54;       /* Very dark green */
    --light-color: #DCF8C6;      /* Light green */
}
```

### Adding New Features
1. Create new Thymeleaf templates in `src/main/resources/templates/`
2. Add corresponding controller methods in `WebController.java`
3. Update navigation in `layout.html`
4. Add JavaScript functionality as needed

## Browser Support

- Chrome 90+
- Firefox 88+
- Safari 14+
- Edge 90+

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details. 