package dev.digitaldragon;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.time.Instant;
import java.util.*;

public class Main {
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36 CrawlyProjectCrawler/0.0.4";
    public static final String CRAWLER = "crawlyprojectofficial 0.0.4";
    public static final String USERNAME = "DigitalDragon";
    public static final String TRACKER_HOST = "http://localhost:4567";

    public static void main(String[] args) {
        for (int i = 0; i < 5; i++) {
            System.out.println("Getting URLs from Tracker:");
            String url = getUrlFromTracker(1, USERNAME, TRACKER_HOST);
            assert url != null; assert !url.isEmpty();
            //String url = "https://simeontrust.org/wp-json/oembed/1.0/embed?url=https%3A%2F%2Fsimeontrust.org%2Fworkshop%2Fprovidence-2023%2F";
            System.out.println(url);
            System.out.println("Crawling:");
            JSONObject data = grabPage(url);
            if (data == null) {
                System.out.println("Crawl failed.");
                continue;
            }
            System.out.println(data);
            System.out.println("Submitting:");
            sendDoneToTracker(data, TRACKER_HOST);
        }
    }

    /**
     * Retrieves the URL from a tracker server.
     *
     * @param amount the amount of URLs to fetch from the tracker.
     * @param username the username to use for authentication.
     * @param trackerHost the hostname of the tracker server.
     * @return the URL fetched from the tracker, or null if an error occurs.
     */
    public static String getUrlFromTracker(int amount, String username, String trackerHost) {
        try {
            String url = String.format("%s/jobs/queue?amount=%s&username=%s", trackerHost, amount, username);
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            String responseJson = readResponse(connection);
            JSONObject jsonResponse = new JSONObject(responseJson);
            JSONArray jsonUrls = jsonResponse.getJSONArray("urls");
            List<String> urls = new ArrayList<>();
            for (int i = 0; i < jsonUrls.length(); i++) {
                urls.add(jsonUrls.getString(i));
            }
            return urls.get(0);
        } catch (IOException e) {
            System.out.println("Error fetching from tracker.");
            return null;
        }
    }

    /**
     * Reads the response from an HTTP connection and returns it as a string.
     *
     * @param connection the HttpURLConnection object representing the connection to the server.
     * @return the response from the server as a string, or an empty string if there is no response.
     * @throws IOException if an error occurs while reading the response.
     */
    private static String readResponse(HttpURLConnection connection) throws IOException {
        Scanner scanner = new Scanner(connection.getInputStream());
        scanner.useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : "";
    }

    /**
     * Retrieves information from a web page specified by the given URL.
     *
     * @param grabUrl the URL of the web page to retrieve.
     * @return a JSONObject containing the retrieved information, or null if an error occurs.
     */
    public static JSONObject grabPage(String grabUrl) {
        JSONObject data;
        try {
            Document document = fetchDocument(grabUrl);

            if (document != null) {
                data = buildJsonData(document, grabUrl);
            } else {
                System.out.println("Unsupported content type.");
                data = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
            data = null;
        }

        return data;
    }

    /**
     * Retrieves the HTML document of a web page specified by the given URL.
     *
     * @param grabUrl the URL of the web page to retrieve.
     * @return the HTML document of the web page, or null if an error occurs or the content type is not supported.
     * @throws IOException if an I/O error occurs while connecting to the web page.
     */
    public static Document fetchDocument(String grabUrl) throws IOException {
        try {
            return Jsoup.connect(grabUrl)
                    .followRedirects(false)
                    .userAgent(USER_AGENT)
                    .ignoreHttpErrors(true)
                    .get();
        } catch (UnsupportedMimeTypeException e) {
            return null;
        }
    }

    /**
     * Builds a JSON object containing data from a web page.
     *
     * @param document the HTML document of the web page.
     * @param grabUrl the URL of the web page.
     * @return a JSON object containing the data from the web page.
     */
    public static JSONObject buildJsonData(Document document, String grabUrl) {
        JSONObject data = new JSONObject();
        data.put("time", Instant.now());
        data.put("url", grabUrl);
        data.put("user_agent", USER_AGENT);
        data.put("response", document.connection().response().statusCode());
        data.put("username", USERNAME);
        data.put("client", CRAWLER);

        JSONArray discoveredOutlinks = extractAndCleanURLs(document.select("a[href]"), grabUrl);
        discoveredOutlinks = appendLocationHeader(document, discoveredOutlinks);

        data.put("discovered_outlinks", discoveredOutlinks);
        data.put("discovered_embeds", extractAndCleanURLs(document.select(":not(a)[href]"), grabUrl));

        return data;
    }

    /**
     * Appends the "Location" header values to the discovered outlinks array.
     *
     * @param document           the HTML document of the web page.
     * @param discoveredOutlinks the array of discovered outlinks.
     * @return the updated array of discovered outlinks with "Location" header values appended.
     */
    public static JSONArray appendLocationHeader(Document document, JSONArray discoveredOutlinks) {
        Map<String, String> headers = document.connection().response().headers();
        for (String key : headers.keySet()) {
            if (key.equalsIgnoreCase("location")) {
                discoveredOutlinks.put(headers.get(key));
            }
        }
        return discoveredOutlinks;
    }

    /**
     * Extracts and cleans the URLs from the provided Elements.
     *
     * @param elements the Elements to extract the URLs from.
     * @param baseUrl  the base URL to resolve relative URLs.
     * @return the JSONArray containing the extracted and cleaned URLs.
     */
    public static JSONArray extractAndCleanURLs(Elements elements, String baseUrl) {
        JSONArray urlArray = new JSONArray();
        Set<String> urls = getURLs(elements);

        for (String url : urls) {
            url = cleanUrl(url, baseUrl);

            if (url != null) {
                urlArray.put(url);
            }
        }

        return urlArray;
    }

    /**
     * Extracts URLs from the provided Elements.
     *
     * @param elements the Elements to extract the URLs from.
     * @return the set of URLs extracted from the Elements.
     */
    private static Set<String> getURLs(Elements elements) {
        Set<String> urls = new HashSet<>();

        for (Element element : elements) {
            urls.add(element.attr("href"));
            urls.add(element.attr("src"));
        }

        return urls;
    }

    /**
     * Cleans and normalizes a URL based on the base URL.
     *
     * @param url     the URL to clean.
     * @param baseUrl the base URL used to resolve relative URLs.
     * @return the cleaned and normalized URL, or null if the URL is empty or starts with "#".
     */
    private static String cleanUrl(String url, String baseUrl) {
        if (url.startsWith("#") || url.isEmpty()) {
            return null;
        }

        if (url.startsWith("//")) {
            url = "https:" + url;
        }

        if (url.startsWith("/") || url.startsWith("./")) {
            url = url.replaceFirst("(^\\/|\\.\\/)","");
            url = baseUrl + url;
        }

        if (!url.startsWith("http://") && !url.startsWith("https://") && !url.contains("://")) {
            int lastSlashIndex = baseUrl.lastIndexOf("/");
            String domainBaseUrl = baseUrl.substring(0, lastSlashIndex + 1);
            url = domainBaseUrl + url;
        }

        return url;
    }

    /**
     * Sends a POST request to the tracker with the provided data.
     *
     * @param data        the JSON data to send.
     * @param trackerHost the hostname and port of the tracker.
     */
    public static void sendDoneToTracker(JSONObject data, String trackerHost) {
        try {
            String url = String.format("%s/jobs/submit", trackerHost);
            String requestBody = data.toString();
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Content-Length", String.valueOf(requestBody.length()));
            connection.setDoOutput(true);
            connection.getOutputStream().write(requestBody.getBytes());
            String responseJson = readResponse(connection);
            System.out.println("Tracker response: " + responseJson);
        } catch (IOException e) {
            System.out.println("Error submitting to tracker");
        }
    }
}