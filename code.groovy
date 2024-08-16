import org.apache.http.HttpHost
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.config.RequestConfig
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import groovy.json.JsonSlurper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.charset.StandardCharsets
import org.apache.jorphan.logging.LoggingManager

// Logging setup
def log = org.apache.jorphan.logging.LoggingManager.getLoggerForClass()
def httpClient = null // Define httpClient outside try-catch block
FileWriter writer = new FileWriter("fit_ui.txt", false) // Open the file in append mode
String filePath = "CountryCodes.txt" // Country name to code config - Keep a comma separated list of country names and codes.

try {
   // Proxy settings
   HttpHost proxy = new HttpHost("host", port)
   RequestConfig config = RequestConfig.custom().setProxy(proxy).build()
   httpClient = HttpClients.custom().setDefaultRequestConfig(config).build()

   // Time setup
   Calendar cal = Calendar.getInstance()
   SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
   sdf.setTimeZone(TimeZone.getTimeZone("UTC"))
   String now = sdf.format(cal.time)
   cal.add(Calendar.MINUTE, -15)
   String fifteenMinutesAgo = sdf.format(cal.time)

   // SQL query
   String query = "SELECT eventTimestamp, pagename, device, browser, geocountry, georegion, deviceos, pageexperience, geocity, hasresource, metrics.`End User Response Time (ms)`, metrics.`Visually Complete Time (ms)`, pageparenturl, pageurl, referrer FROM browser_records WHERE appkey = 'App-Key' AND metrics.`End User Response Time (ms)` is not NULL AND (pagetype = 'VIRTUAL_PAGE' OR pagetype = 'BASE_PAGE' ) AND eventTimestamp BETWEEN '" + fifteenMinutesAgo + "' AND '" + now + "' ORDER BY eventTimestamp desc LIMIT 10000"

   // HTTP POST setup
   HttpPost postRequest = new HttpPost("https://analytics.api.company.com/events/query")
   postRequest.addHeader("X-Events-API-Key", "API-Key")
   postRequest.addHeader("X-Events-API-AccountName", "AccountName")
   postRequest.addHeader("Content-Type", "application/vnd.appd.events+text;v=2")
   postRequest.addHeader("Accept", "application/vnd.appd.events+json;v=2")
   postRequest.setEntity(new StringEntity("[{\"query\": \"" + query + "\", \"mode\": \"scroll\"}]"))

   // Execute HTTP request
   def response = httpClient.execute(postRequest)
   def responseBody = EntityUtils.toString(response.getEntity())

   // Parse JSON response
   def jsonSlurper = new JsonSlurper()
   def jsonData = jsonSlurper.parseText(responseBody)

   // Initialize StringBuilder to accumulate line protocol data
   StringBuilder lineProtocolData = new StringBuilder()

   // Process results if available
   if (jsonData && jsonData[0]?.results) {
       def results = jsonData[0].results
       results.each { result ->
           def eventTimestampString = result[0]
           def eventTimestamp = sdf.parse(eventTimestampString)
           def endUserResponseTime = result[10]
           def visuallyCompleteTime = result[11]

           if (visuallyCompleteTime == null) {
               visuallyCompleteTime = 0
           }

           def pageparenturl = result[12].toString()
           def pageurl = result[13].toString()
           def referrer = result[14].toString()

           // Convert country name to code
           List<String> lines = Files.readAllLines(Paths.get(filePath), StandardCharsets.UTF_8)
           Map<String, String> countryCodes = lines.collectEntries { line ->
               def (country, code) = line.split(':')
               [(country.trim()): code.trim()]
           }
           String CntryCD = countryCodes.get(result[4])
           if (CntryCD == null) {
               CntryCD = "null"
           }

           // Constructing tags and applying escaping directly here
           def tags = [
               pagename: result[1].toString().replace(",", "\\\\,").replace("=", "\\\\=").replace(" ", "\\\\ "),
               device: result[2].toString().replace(",", "\\\\,").replace("=", "\\\\=").replace(" ", "\\\\ "),
               browser: result[3].toString().replace(",", "\\\\,").replace("=", "\\\\=").replace(" ", "\\\\ "),
               geocountry: result[4].toString().replace(",", "\\\\,").replace("=", "\\\\=").replace(" ", "\\\\ "),
               georegion: result[5].toString().replace(",", "\\\\,").replace("=", "\\\\=").replace(" ", "\\\\ "),
               deviceos: result[6].toString().replace(",", "\\\\,").replace("=", "\\\\=").replace(" ", "\\\\ "),
               pageexperience: result[7].toString().replace(",", "\\\\,").replace("=", "\\\\=").replace(" ", "\\\\ "),
               geocity: result[8].toString().replace(",", "\\\\,").replace("=", "\\\\=").replace(" ", "\\\\ "),
               hasresource: result[9].toString().replace(",", "\\\\,").replace("=", "\\\\=").replace(" ", "\\\\ "),
               pageurl: pageurl.replace(",", "\\\\,").replace("=", "\\\\=").replace(" ", "\\\\ "),
               pageparenturl: pageparenturl.replace(",", "\\\\,").replace("=", "\\\\=").replace(" ", "\\\\ "),
               referrer: referrer.replace(",", "\\\\,").replace("=", "\\\\=").replace(" ", "\\\\ "),
               countrycode: CntryCD.replace(",", "\\\\,").replace("=", "\\\\=").replace(" ", "\\\\ ")
           ]

           // Constructing InfluxDB Line Protocol string
           def lineProtocol = "browser_records,"
           lineProtocol += tags.collect { k, v -> "$k=$v" }.join(',')
           lineProtocol += " endUserResponseTime=${endUserResponseTime},visuallyCompleteTime=${visuallyCompleteTime}"
           lineProtocol += " ${eventTimestamp.time}"

           lineProtocolData.append(lineProtocol).append("\n")
       }

       writer.write("Line Protocol Data: " + lineProtocolData.toString() + "\n")
       log.info("Data prepared for InfluxDB")

       // Send the accumulated line protocol data to InfluxDB
       HttpPost influxPost = new HttpPost("http://influxHost:influxPort/api/v2/write?bucket=DB_NAME&precision=ms")
       influxPost.setEntity(new StringEntity(lineProtocolData.toString()))

       def influxResponse = httpClient.execute(influxPost)
       def statusCode = influxResponse.getStatusLine().getStatusCode()
       def responseEntity = influxResponse.getEntity()
       def influxResponseBody = responseEntity != null ? EntityUtils.toString(responseEntity) : "No response body"

       // Log the status code and response body
       log.info("Response from InfluxDB: HTTP Status Code: " + statusCode + ", Response Body: " + influxResponseBody)
   } else {
       log.info("No data to process.")
   }
} catch (Exception e) {
   log.error("Error during HTTP request: " + e.message)
} finally {
   if (httpClient != null) {
       httpClient.close()
   }
   writer.close() // Make sure to close the FileWriter
}
