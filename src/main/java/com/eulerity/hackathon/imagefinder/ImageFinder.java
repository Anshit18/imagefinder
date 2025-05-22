package com.eulerity.hackathon.imagefinder;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

@WebServlet(
        name = "ImageFinder",
        urlPatterns = {"/main"}
)
public class ImageFinder extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final int MAX_PAGES = 20;
    private static final int THREAD_POOL_SIZE = 8;
    private static final int TIMEOUT_SECONDS = 120; // 2 minutes

    protected static final Gson GSON = new GsonBuilder().create();

    // This is just a test array
    public static final String[] testImages = {
            "https://images.pexels.com/photos/545063/pexels-photo-545063.jpeg?auto=compress&format=tiny",
            "https://images.pexels.com/photos/464664/pexels-photo-464664.jpeg?auto=compress&format=tiny",
            "https://images.pexels.com/photos/406014/pexels-photo-406014.jpeg?auto=compress&format=tiny",
            "https://images.pexels.com/photos/1108099/pexels-photo-1108099.jpeg?auto=compress&format=tiny"
    };

    @Override
    protected final void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        String path = req.getServletPath();
        String url = req.getParameter("url");

        System.out.println("Processing request for URL: " + url);

        // Return test images if URL is empty
        if (url == null || url.isEmpty()) {
            System.out.println("URL is empty, returning test images");
            resp.getWriter().print(GSON.toJson(testImages));
            return;
        }

        try {
            // Normalize URL
            System.out.println("Normalizing URL...");
            URL normalizedUrl = normalizeUrl(url);
            String domain = normalizedUrl.getHost();
            System.out.println("Normalized URL: " + normalizedUrl);
            System.out.println("Domain: " + domain);

            // Find images
            System.out.println("Starting image crawl...");
            List<String> images = findImagesFromUrl(normalizedUrl.toString(), domain);
            System.out.println("Crawl complete. Found " + images.size() + " images");

            // Return found images, even if empty
            resp.getWriter().print(GSON.toJson(images));

        } catch (Exception e) {
            System.out.println("Error processing URL: " + e.getMessage());
            e.printStackTrace();
            // Return empty array in case of error
            resp.getWriter().print(GSON.toJson(new ArrayList<String>()));
        }
    }

    /**
     * Normalizes a URL by ensuring it has a proper protocol
     */
    protected URL normalizeUrl(String url) throws MalformedURLException {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        return new URL(url);
    }

    /**
     * Finds images from a starting URL and within the same domain
     */
    protected List<String> findImagesFromUrl(String startUrl, String domain) {
        // Use ConcurrentHashMap for thread safety
        Set<String> imageUrls = ConcurrentHashMap.newKeySet();
        Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        // Start crawling from the initial URL
        executor.submit(() -> crawl(startUrl, domain, imageUrls, visitedUrls, executor));

        // Wait for all tasks to complete or timeout
        executor.shutdown();
        try {
            executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Crawl interrupted");
        }

        System.out.println("Crawler finished, visited " + visitedUrls.size() + " pages, found " + imageUrls.size() + " images");
        return new ArrayList<>(imageUrls);
    }

    /**
     * Crawls a page and extracts images and links
     */
    protected void crawl(String url, String domain, Set<String> imageUrls, Set<String> visitedUrls, ExecutorService executor) {
        System.out.println("Crawling URL: " + url);

        // Skip if URL is already visited or we've reached the limit
        if (visitedUrls.contains(url)) {
            System.out.println("URL already visited: " + url);
            return;
        }

        if (visitedUrls.size() >= MAX_PAGES) {
            System.out.println("Reached maximum page limit");
            return;
        }

        // Mark URL as visited
        visitedUrls.add(url);
        System.out.println("Total visited URLs: " + visitedUrls.size());

        try {
            // Get the page content
            System.out.println("Connecting to URL: " + url);
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; EulerityBot/1.0)")
                    .timeout(10000)
                    .get();

            // Extract images from the page
            Elements images = doc.select("img[src]");
            System.out.println("Found " + images.size() + " image tags on page");

            for (Element image : images) {
                String imageUrl = image.absUrl("src");
                if (!imageUrl.isEmpty() && isImageUrl(imageUrl)) {
                    imageUrls.add(imageUrl);
                    System.out.println("Added image: " + imageUrl);
                }
            }

            // Find links to other pages in the same domain
            Elements links = doc.select("a[href]");
            System.out.println("Found " + links.size() + " links on page");

            int newLinksCount = 0;
            for (Element link : links) {
                String nextUrl = link.absUrl("href");

                // Check if the link is in the same domain and not yet visited
                if (!nextUrl.isEmpty() && isSameDomain(nextUrl, domain) && !visitedUrls.contains(nextUrl)) {
                    // Submit a new task to process this URL
                    executor.submit(() -> crawl(nextUrl, domain, imageUrls, visitedUrls, executor));
                    newLinksCount++;
                }
            }
            System.out.println("Submitted " + newLinksCount + " new links for crawling");

        } catch (IOException e) {
            System.out.println("Error crawling " + url + ": " + e.getMessage());
        }
    }

    /**
     * Checks if a URL points to an image based on file extension
     */
    protected boolean isImageUrl(String url) {
        if (url == null || url.isEmpty() || !url.startsWith("http")) {
            return false;
        }

        String lowerCaseUrl = url.toLowerCase();
        return lowerCaseUrl.endsWith(".jpg") ||
                lowerCaseUrl.endsWith(".jpeg") ||
                lowerCaseUrl.endsWith(".png") ||
                lowerCaseUrl.endsWith(".gif") ||
                lowerCaseUrl.endsWith(".webp") ||
                lowerCaseUrl.endsWith(".svg") ||
                lowerCaseUrl.endsWith(".bmp");
    }

    /**
     * Checks if a URL is from the same domain
     */
    protected boolean isSameDomain(String url, String domain) {
        try {
            String urlDomain = new URL(url).getHost();
            return urlDomain.equals(domain) || urlDomain.endsWith("." + domain);
        } catch (MalformedURLException e) {
            return false;
        }
    }
}