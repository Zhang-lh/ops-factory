# Operation Intelligence Service

Operation Intelligence service for QoS (Quality of Service) monitoring and call chain mining.

## Features

### QoS Monitoring

- **Health Curve Collection**: Collects availability, performance, and resource metrics from DV systems
- **Health Score Calculation**: Calculates composite health scores using weighted dimensions
- **Multi-Environment Support**: Supports multiple DV environments with configurable weights
- **Data Retention**: Rotating data storage with configurable retention policies

### Call Chain Mining

- **Four Chain Types**: Supports BES (Business Execution System), API (External Interface), BPM (Business Process Management), and JOB (Scheduled Job)
- **TraceLog Query**: Queries DV tracelog API with time range splitting support
- **Call Chain Building**: Builds complete call chain trees from trace logs
- **Statistical Analysis**: Provides IP, cluster, and cost statistics at both flow and node levels
- **Configuration Import**: Supports menuId mapping and chain type configuration import

## Configuration

### Configuration File

The service loads configuration from `config.yaml` (can be overridden via `OI_CONFIG_PATH` environment variable).

### Key Configuration Sections

```yaml
operation-intelligence:
  # Authentication
  secret-key: ""                        # Required: x-secret-key for API access
  
  # QoS Configuration
  qos:
    enabled: true
    collection-interval-ms: 300000      # 5 minutes
    weights:
      availability: 0.4
      performance: 0.4
      resource: 0.2
  
  # Call Chain Configuration
  call-chain:
    enabled: true
    collection-interval-ms: 300000      # 5 minutes
    query-size: 100                     # DV query page size
    dv-environments: []                 # DV environments
```

### Environment Variables

| Variable | Description |
|----------|-------------|
| `OI_CONFIG_PATH` | Path to configuration file |
| `OI_PORT` | Service port (default: 8096) |
| `OI_SECRET_KEY` | Secret key for authentication |

## API Endpoints

### QoS Endpoints

- `POST /operation-intelligence/qos/getHealthIndicator` - Get health indicator curve
- `POST /operation-intelligence/qos/getAvailableIndicatorDetail` - Get availability indicator details
- `POST /operation-intelligence/qos/getPerformanceIndicatorDetail` - Get performance indicator details
- `POST /operation-intelligence/qos/getResourceIndicatorDetail` - Get resource indicator details
- `POST /operation-intelligence/qos/getContributionData` - Get contribution data
- `POST /operation-intelligence/qos/getAlarmIndicatorDetail` - Get alarm indicator details
- `GET /operation-intelligence/qos/getEnvironments` - Get configured environments

### Call Chain Endpoints

- `POST /operation-intelligence/call-chain/query` - Query call chains
- `POST /operation-intelligence/call-chain/config/menu-mapping` - Import menuId mappings
- `POST /operation-intelligence/call-chain/config/chain-type` - Import chain type configurations
- `GET /operation-intelligence/call-chain/environments` - Get DV environments

## Call Chain Query Example

```bash
curl -X POST http://127.0.0.1:8096/operation-intelligence/call-chain/query \
  -H "Content-Type: application/json" \
  -H "x-secret-key: your-secret-key" \
  -d '{
    "chainType": "BES",
    "conditionType": "menuId",
    "conditionValue": "604015020",
    "startTime": 1714324800000,
    "endTime": 1714328400000
  }'
```

## Chain Types

| Type | Description | Condition Key | Entry Field |
|------|-------------|---------------|-------------|
| BES | Business Execution System | menuId | url |
| API | External Interface | serviceName | url, serviceName |
| BPM | Business Process Management | AppendInfo | busiCode, processName |
| JOB | Scheduled Job | jobDefinedId | jobDefinedId |

## Data Storage

### Storage Structure

```
data/
├── qos/
│   ├── raw/              # Raw QoS data (7 days)
│   ├── detail/           # Detailed QoS data (30 days)
│   └── normalize/        # Normalized QoS data (90 days)
└── call-chain/
│   ├── normalize/        # Call chain trees (90 days)
│   └── config/
│       ├── menu_mapping.json
│       └── chain_type_config.json
```

### Data Rotation

- Raw data: 7 days retention
- Detail data: 30 days retention
- Normalize data: 90 days retention
- Rotation interval: 1 hour (default)

## Building and Running

### Build

```bash
cd operation-intelligence
mvn clean package
```

### Run

```bash
java -jar target/operation-intelligence.jar
```

Or using the control script:

```bash
./scripts/ctl.sh startup
```

### Health Check

```bash
curl http://127.0.0.1:8096/actuator/health
```

## Development

### Project Structure

```
operation-intelligence/
├── config.yaml.example     # Configuration template
├── src/main/java/com/huawei/opsfactory/operationintelligence/
│   ├── CallChainApplication.java    # Main application class
│   ├── config/                     # Configuration classes
│   ├── controller/                 # REST controllers
│   ├── service/                    # Business logic
│   │   ├── QosService.java
│   │   ├── QosCalculationService.java
│   │   ├── CallChainService.java    # Call chain service
│   │   ├── CallChainBuilder.java    # Call chain builder
│   │   └── CallChainStatistics.java # Statistics calculator
│   ├── qos/                        # QoS module
│   │   ├── model/                   # Data models
│   │   ├── parser/                  # Parsers
│   │   │   ├── TraceLogParser.java
│   │   ├── dv/                      # DV client
│   │   ├── store/                   # Data stores
│   │   └── scheduler/               # Scheduled tasks
│   └── common/                     # Common utilities
└── scripts/
    └── ctl.sh                       # Control script
```

### Testing

```bash
mvn test
```

## Logging

Logs are written to the `logs/` directory with automatic rotation:

- `operation-intelligence.log` - Application logs
- `operation-intelligence-yyyy-MM-dd-HH-i.log.gz` - Rotated logs

Log levels can be configured in `config.yaml`:

```yaml
logging:
  level:
    root: INFO
    com.huawei.opsfactory.operationintelligence: INFO
```

## License

Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
