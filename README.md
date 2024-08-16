# Groovy Script Documentation: AppDynamics to InfluxDB Data Pipeline

## Overview

This Groovy script is executed within a JMeter JSR223 Sampler to automate the process of querying data from AppDynamics' Analytics module and pushing the retrieved data into an InfluxDB instance. The data is later visualized in Grafana as a dashboard. This setup allows for continuous monitoring of UI analytics metrics extracted from AppDynamics and presented in a time-series format in InfluxDB.

## Prerequisites

Before running this script, ensure the following prerequisites are met:

- **JMeter**: Installed and configured to run the JSR223 Sampler.
- **AppDynamics API Access**: Valid API key and account name for accessing AppDynamics Analytics data.
- **InfluxDB**: A running InfluxDB instance with the appropriate bucket and access configurations.
- **Grafana**: Configured to visualize the data stored in InfluxDB.
- **Proxy Settings**: If running behind a corporate firewall, appropriate proxy settings are required.

## Script Workflow

1. **Setup Logging and HTTP Client**:
  - The script initializes logging and sets up an HTTP client with proxy settings to interact with external APIs.
  - A `FileWriter` is also initialized to log the results to a file.

2. **Time Configuration**:
  - The script defines the current time and a time window (e.g., the last 7 minutes) for which data will be queried from AppDynamics.

3. **Query Construction**:
  - A SQL-like query is constructed to fetch specific UI analytics metrics from the AppDynamics Analytics module.
  - The query is designed to filter and retrieve data points such as `End User Response Time`, `Visually Complete Time`, and other UI-related metrics.

4. **HTTP POST Request to AppDynamics**:
  - The script sends the constructed query to the AppDynamics Analytics API via an HTTP POST request.
  - The response from AppDynamics is captured and processed.

5. **Data Processing**:
  - The JSON response from AppDynamics is parsed.
  - Each record is processed to extract relevant metrics, which are then normalized and converted into InfluxDB's line protocol format.
  - Special handling is done to sanitize URLs and convert country names to codes.

6. **Data Ingestion to InfluxDB**:
  - The formatted data (in line protocol) is sent to InfluxDB via an HTTP POST request.
  - The script logs the success or failure of the data insertion process.

7. **Cleanup**:
  - The script closes the HTTP client and the `FileWriter`, ensuring all resources are properly released.

## Detailed Explanation

### 1. **Logging and HTTP Client Setup**
  - Initializes logging and configures an HTTP client with proxy settings to handle requests to external APIs (AppDynamics and InfluxDB).

### 2. **Time Setup**
  - Configures the time range for data querying. The script fetches data from the last 7 minutes to the current time.

### 3. **SQL Query Construction**
  - Constructs a SQL-like query for the AppDynamics Analytics API to retrieve relevant metrics like `End User Response Time` and `Visually Complete Time`.

### 4. **HTTP POST Request**
  - The query is sent via HTTP POST to the AppDynamics Analytics API. The response is parsed and processed.

### 5. **Data Processing**
  - Parses the JSON response, extracts key metrics, normalizes URLs, and converts country names to codes.
  - Constructs InfluxDB line protocol strings from the extracted data.

### 6. **Data Ingestion to InfluxDB**
  - The accumulated data in line protocol format is sent to InfluxDB via an HTTP POST request.

### 7. **Cleanup**
  - Ensures all resources (HTTP client and file writer) are properly closed after execution.

## Usage and Execution

- **Integration with JMeter**:  
 This Groovy script is executed within a JMeter JSR223 Sampler, allowing for automated data retrieval and processing during performance testing or monitoring.

- **Data Visualization**:  
 The data pushed into InfluxDB can be visualized in Grafana, providing insights into UI performance metrics such as response times and user experience.
