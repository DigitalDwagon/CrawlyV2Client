package dev.digitaldragon;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class CrawlyClient {
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36 CrawlyProjectCrawler/0.0.4 (email crawlyproject@digitaldragon.dev)";
    public static final String CRAWLER = "crawlyprojectofficial 0.0.6";
    public static final String USERNAME = "DigitalDragon";
    public static final String TRACKER_HOST = "http://localhost:4567";
    private static final int TOTAL_URLS = 2000;

    // Fixed thread pool executor service
    private static final ExecutorService executorService = Executors.newFixedThreadPool(200);
    private static JSONArray sendCache = new JSONArray();

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!sendCache.isEmpty()) {
                submitSendCache(TRACKER_HOST);
            }
        }));

        try {
            // Keep track of Futures
            List<Future<?>> futures = new ArrayList<>();

            while (true) {
                // Remove completed tasks from futures list
                futures.removeIf(Future::isDone);

                if (futures.isEmpty()) {
                    System.out.println("Running on empty.");
                }

                // If there are no tasks left, then continue fetching new URLs, else sleep
                if (futures.size() < 200){
                    List<String> urls = getUrlFromTracker(TOTAL_URLS, USERNAME, TRACKER_HOST);
                    if (urls == null) {
                        System.out.println("null, continuing");
                        continue;
                    }
                    for (String url : urls) {
                        // Submit returns a Future, keep track of these
                        futures.add(executorService.submit(new CrawlTask(url)));
                    }
                }


            }
        } catch (Exception e) {
            System.out.println("An exception occurred: " + e.getMessage());
        } finally {
            executorService.shutdown();
        }
    }

    final static class CrawlTask implements Callable<Void> {

        private final String url;

        private CrawlTask(String url) {
            this.url = url;
        }

        @Override
        public Void call() {
            if (url == null || url.isEmpty()) {
                System.out.println("No Url Found");
                return null;
            }

            System.out.println(url);
            JSONObject data = crawlPage(url);
            if (data == null) {
                //System.out.println("Crawl failed.");
                return null;
            }

            //System.out.println("Crawled Data:");
            //System.out.println(data);

            //System.out.println("Submitting crawled data:");
            sendDoneToTracker(data, TRACKER_HOST);
            System.out.println(data.get("url"));
            return null;
        }
    }

    public static JSONObject crawlPage(String url) {
        //System.out.println("Crawling:");
        return grabPage(url);
    }

    /**
     * Retrieves the URL from a tracker server.
     *
     * @param amount the amount of URLs to fetch from the tracker.
     * @param username the username to use for authentication.
     * @param trackerHost the hostname of the tracker server.
     * @return the URL fetched from the tracker, or null if an error occurs.
     */
    public static List<String> getUrlFromTracker(int amount, String username, String trackerHost) {
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
            return urls;
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
                //System.out.println("Unsupported content type.");
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
                    .timeout(15000) //15 seconds
                    .get();
        } catch (UnsupportedMimeTypeException | SSLHandshakeException e) {
            //should release the claim
            //for now does nothing
            return null;
        } catch (ConnectException e) {
            //should send a 000 status to tracker
            //for now does nothing.
            return null;
        } catch (SocketTimeoutException e) {
            //should send 000
            //does nothing for now
            return null;
        } catch (UnknownHostException e) {
            //should do something idk
            //does nothing for now
            return null;
        } catch (SocketException e) {
            //should do something
            //for now does not
            return null;
        } catch (IOException e) {
            //unfortunately generic ioexceptions can appear.
            //example: Underlying input stream returned zero bytes
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
        JSONObject headers = new JSONObject();
        document.connection().response().headers().forEach(headers::put); //TODO this may overwrite headers with the same name (eg multiple Set-Cookie headers)

        JSONArray discoveredOutlinks = extractAndCleanURLs(document.select("a[href]"), grabUrl);
        discoveredOutlinks = appendLocationHeader(document, discoveredOutlinks);


        JSONObject data = new JSONObject();
        data.put("time", Instant.now());
        data.put("url", grabUrl);
        data.put("user_agent", USER_AGENT);
        data.put("response", document.connection().response().statusCode());
        data.put("username", USERNAME);
        data.put("client", CRAWLER);
        data.put("headers", headers);
        data.put("meta", getMeta(document.select("meta")).put("html_title", document.title()));
        data.put("discovered_outlinks", discoveredOutlinks);
        data.put("discovered_embeds", extractAndCleanURLs(document.select(":not(a)[href]"), grabUrl));

        return data;
    }

    public static JSONObject getMeta(Elements elements) {
        JSONObject meta = new JSONObject();
        for (Element tag : elements) {
            String name = tag.attr("name");
            String content = tag.attr("content");
            if (!name.isEmpty() && !content.isEmpty()) {
                meta.put(name, content);
            }
        }
        return meta;
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
            try {
                url = cleanUrl(url, baseUrl);
            } catch (MalformedURLException malformedURLException) {
                continue;
            }

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
     * @throws MalformedURLException if no protocol is specified, or an unknown protocol is found, or spec is null.
     */
    private static String cleanUrl(String url, String baseUrl) throws MalformedURLException {
        if (url.startsWith("#") || url.isEmpty()) {
            return null;
        }

        if (url.startsWith("//")) {
            return new URL("https:" + url).toExternalForm();
        }

        URL baseURL = new URL(baseUrl);

        // If the URL starts with a "/", it's taken as relative to the domain.
        if (url.startsWith("/")) {
            return new URL(baseURL.getProtocol(), baseURL.getHost(), url).toExternalForm();
        }

        if (url.startsWith("./")) {
            String fileBaseUrl = baseURL.getFile();
            int lastSlashIndex = fileBaseUrl.lastIndexOf("/");
            String directoryBaseUrl = fileBaseUrl.substring(0, lastSlashIndex + 1);
            return new URL(baseURL, directoryBaseUrl + url.substring(2)).toExternalForm();
        } else {
            // Attempts to create a URL directly and falls back to creating a URL relative to the base URL
            try {
                return new URL(url).toExternalForm();
            } catch (MalformedURLException relativeUrlEx) {
                return new URL(baseURL, url).toExternalForm();
            }
        }
    }


    /**
     * Sends a POST request to the tracker with the provided data.
     *
     * @param data        the JSON data to send.
     * @param trackerHost the hostname and port of the tracker.
     */
    public static void sendDoneToTracker(JSONObject data, String trackerHost) {
        try {
            sendCache.put(data);

            if (sendCache.length() >= 5) {
                submitSendCache(trackerHost);
            }
        } catch (JSONException e) {
            System.out.println("Error parsing JSON");
        }
    }

    public static void submitSendCache(String trackerHost) {
        final JSONArray items = new JSONArray(sendCache);
        sendCache.clear();
        try {
            JSONObject send = new JSONObject();
            send.put("items", items);

            String url = String.format("%s/jobs/submit?username=%s", trackerHost, USERNAME);
            String requestBody = send.toString();
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Content-Length", String.valueOf(requestBody.length()));
            connection.setDoOutput(true);
            connection.getOutputStream().write(requestBody.getBytes());
            String responseJson = readResponse(connection);
            //System.out.println("Tracker response: " + responseJson);
            // Parse the JSON response
            JSONObject jsonResp = new JSONObject(responseJson);
            boolean success = jsonResp.getBoolean("success");


            if (!success) {
                JSONArray error = jsonResp.getJSONArray("error");
                System.out.println("Error: " + error.toString());
                for (int i = 0; i < error.length(); i++) {
                    if (error.getString(i).equals("BAD_DATA") || error.getString(i).equals("INVALID_TIME")) {
                        System.out.println("Encountered " + error.getString(i) + " error");
                    }
                }
            }
        } catch (IOException ioException) {
            ioException.printStackTrace();
            System.out.println("Error submitting to tracker");
        } catch (JSONException jsonException) {
            jsonException.printStackTrace();
            System.out.println("error submitting to tracker");
        }

    }
}