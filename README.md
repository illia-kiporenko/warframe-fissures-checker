# Warframe Fissures Checker - Backend

A Spring Boot REST API that provides real-time Warframe fissure data with long-polling support for efficient real-time updates.

## 🚀 Features

- **Real-time Data**: Fetches live fissure data from Warframe API every 5 minutes
- **Long Polling**: Efficient real-time updates without constant client requests
- **Advanced Filtering**: Filter by mission types and difficulty (normal/hard mode)
- **Smart Caching**: Optimized performance with intelligent result caching
- **Robust Error Handling**: Graceful handling of network issues and client disconnects
- **Cross-Origin Support**: CORS configured for frontend integration
- **Monitoring Endpoints**: Health check and status monitoring

## 🛠️ Tech Stack

- **Java 17+**
- **Spring Boot 3.x**
- **Spring WebFlux** (for external API calls)
- **Spring Web MVC** (for REST endpoints)
- **Lombok** (for code generation)
- **SLF4J + Logback** (for logging)

## 📋 Prerequisites

- Java 17 or higher
- Maven 3.6+
- Internet connection (for fetching Warframe API data)

## 🏃‍♂️ Getting Started

### 1. Clone the Repository
```bash
git clone <your-repo-url>
cd warframe-fissures-backend
```

### 2. Build the Project
```bash
mvn clean install
```

### 3. Run the Application
```bash
mvn spring-boot:run
```

The application will start on `http://localhost:5050`

### 4. Verify Installation
```bash
curl http://localhost:5050/fissures/test
```

## 📚 API Endpoints

### Main Endpoints

#### `GET /fissures`
Long-polling endpoint for real-time fissure updates.

**Query Parameters:**
- `missionTypes` (optional): Comma-separated list of mission types to filter
- `isHard` (optional): `true` for hard mode only, `false` for normal only
- `knownIds` (optional): Comma-separated list of known fissure IDs (for change detection)

**Example:**
```bash
curl "http://localhost:5050/fissures?missionTypes=Disruption,Defense&isHard=false"
```

#### `GET /fissures/immediate`
Get current fissure data immediately (no long-polling).

**Query Parameters:**
- `missionTypes` (optional): Filter by mission types
- `isHard` (optional): Filter by difficulty

**Example:**
```bash
curl "http://localhost:5050/fissures/immediate?missionTypes=Capture"
```

### Monitoring Endpoints

#### `GET /fissures/status`
Get service status and statistics.

**Response:**
```json
{
  "message": "Fissure service is running",
  "activeListeners": 3,
  "currentFissures": 15,
  "timestamp": 1690123456789
}
```

#### `GET /fissures/test`
Simple health check endpoint.

**Response:**
```
Service is responding
```

## 🔧 Configuration

### Application Properties
Create `application.yml` or `application.properties`:

```yaml
server:
  port: 5050

logging:
  level:
    me.kiporenko.warframefissureschecker: INFO
    org.springframework.web.context.request.async: WARN
    org.apache.catalina.connector.CoyoteAdapter: WARN

spring:
  application:
    name: warframe-fissures-checker
```

### CORS Configuration
The application is configured to accept requests from any origin. For production, modify `CorsConfig.java`:

```java
@Override
public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**")
            .allowedOriginPatterns("https://yourdomain.com") // Restrict origins
            .allowedMethods("GET", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(false)
            .maxAge(3600);
}
```

## 📊 Data Format

### Fissure Object
```json
{
  "id": "fissure-unique-id",
  "node": "Earth/Gaia",
  "missionType": "Exterminate",
  "enemy": "Grineer",
  "tier": "Lith",
  "isHard": false,
  "isStorm": false,
  "eta": "2h 30m",
  "expired": false,
  "active": true
}
```

### Response Format
Long-polling endpoints return:
```json
{
  "fissures": [/* array of fissure objects */],
  "fissureIds": ["id1", "id2", "id3"] // for next request
}
```

## 🚀 How Long Polling Works

1. **Initial Request**: Client sends request to `/fissures`
2. **Immediate Response**: If no `knownIds` provided, returns current data immediately
3. **Change Detection**: If `knownIds` provided, compares with current data
4. **Hold Connection**: If data unchanged, holds connection open for up to 30 seconds
5. **Push Updates**: When data changes, immediately responds with new data
6. **Timeout Handling**: After 30 seconds, returns current data even if unchanged

## 🔍 Monitoring & Debugging

### Logging Levels
- `INFO`: Service lifecycle events, data updates
- `DEBUG`: Request details, listener management
- `WARN`: Recoverable errors, timeouts
- `ERROR`: Critical errors, service failures

### Key Log Messages
```
INFO  - Updating fissures. New count: 12, Previous count: 10
INFO  - Registering listener with criteria: FilterCriteria{missionTypes=[Disruption], isHard=null}
DEBUG - Data changed for listener. Expected: [id1, id2], Current: [id1, id3]
```

### Performance Monitoring
Monitor these metrics:
- Active listeners count (`/fissures/status`)
- Response times in logs
- Memory usage (filter cache size)
- External API call success rate

## 🛡️ Error Handling

### Client Disconnects
Client disconnects are handled gracefully and logged at DEBUG level to reduce noise.

### External API Failures
- Automatic retry with exponential backoff (3 attempts)
- Continues with empty data if API unavailable
- Detailed error logging for troubleshooting

### Timeout Handling
- Long-polling requests timeout after 30 seconds
- Fallback to current data on timeout
- Proper cleanup of resources

## 🏗️ Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Frontend      │───▶│  FissureController│───▶│  FissureService │
│  (Long Poll)    │    │  (REST Endpoints) │    │   (Business)    │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                                         │
                       ┌──────────────────┐             │
                       │  FissureUpdater  │◀────────────┘
                       │   (Scheduler)    │
                       └──────────────────┘
                                │
                       ┌──────────────────┐
                       │  Warframe API    │
                       │ (External Data)  │
                       └──────────────────┘
```

## 🧪 Testing

### Unit Tests
```bash
mvn test
```

### Integration Tests
```bash
mvn verify
```

### Manual Testing
```bash
# Test immediate endpoint
curl "http://localhost:5050/fissures/immediate"

# Test long polling (will wait for changes)
curl "http://localhost:5050/fissures?knownIds=existing-id-1,existing-id-2"

# Test filtering
curl "http://localhost:5050/fissures/immediate?missionTypes=Defense&isHard=true"
```

## 📦 Deployment

### JAR Deployment
```bash
mvn clean package
java -jar target/warframe-fissures-checker-*.jar
```

### Docker Deployment
```dockerfile
FROM openjdk:17-jre-slim
COPY target/warframe-fissures-checker-*.jar app.jar
EXPOSE 5050
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### Environment Variables
```bash
export SERVER_PORT=5050
export LOGGING_LEVEL_ROOT=INFO
java -jar app.jar
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- [Warframe](https://www.warframe.com/) for the amazing game
- [WarframeStat.us](https://docs.warframestat.us/) for the public API
- Spring Boot community for excellent documentation

## 📞 Support

If you have any questions or issues:

1. Check the [Issues](../../issues) page
2. Create a new issue with detailed information
3. Include logs and steps to reproduce

---

**Happy Farming, Tenno! 🎮**