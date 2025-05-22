package com.eulerity.hackathon.imagefinder;

import org.junit.Assert;
import org.junit.Test;

import com.google.gson.Gson;

import java.io.StringWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple test class for ImageFinder
 */
public class ImageFinderTest {

	/**
	 * A simple test to verify that the ImageFinder testImages array works correctly.
	 */
	@Test
	public void testImagesArray() {
		String json = new Gson().toJson(ImageFinder.testImages);
		String[] deserializedImages = new Gson().fromJson(json, String[].class);

		Assert.assertEquals(ImageFinder.testImages.length, deserializedImages.length);
		for (int i = 0; i < ImageFinder.testImages.length; i++) {
			Assert.assertEquals(ImageFinder.testImages[i], deserializedImages[i]);
		}
	}

	/**
	 * Test URL normalization
	 */
	@Test
	public void testNormalizeUrl() {
		ImageFinder finder = new ImageFinder();

		try {
			// Test adding https:// to URLs without protocol
			Assert.assertEquals("https://example.com",
					finder.normalizeUrl("example.com").toString());

			// Test URL that already has protocol
			Assert.assertEquals("https://secure.example.com",
					finder.normalizeUrl("https://secure.example.com").toString());
			Assert.assertEquals("http://old.example.com",
					finder.normalizeUrl("http://old.example.com").toString());
		} catch (Exception e) {
			Assert.fail("URL normalization threw an exception: " + e.getMessage());
		}
	}

	/**
	 * Test image URL detection
	 */
	@Test
	public void testIsImageUrl() {
		ImageFinder finder = new ImageFinder();

		// Test valid image URLs
		Assert.assertTrue(finder.isImageUrl("https://example.com/image.jpg"));
		Assert.assertTrue(finder.isImageUrl("https://example.com/image.jpeg"));
		Assert.assertTrue(finder.isImageUrl("https://example.com/image.png"));
		Assert.assertTrue(finder.isImageUrl("https://example.com/image.gif"));
		Assert.assertTrue(finder.isImageUrl("https://example.com/image.webp"));
		Assert.assertTrue(finder.isImageUrl("https://example.com/image.svg"));

		// Test invalid image URLs
		Assert.assertFalse(finder.isImageUrl("https://example.com/image.txt"));
		Assert.assertFalse(finder.isImageUrl("https://example.com/image.html"));
		Assert.assertFalse(finder.isImageUrl("https://example.com/image"));

		// Test null and empty URLs
		Assert.assertFalse(finder.isImageUrl(null));
		Assert.assertFalse(finder.isImageUrl(""));

		// Test non-HTTP URLs
		Assert.assertFalse(finder.isImageUrl("file:///image.jpg"));
	}

	/**
	 * Test domain checking
	 */
	@Test
	public void testIsSameDomain() {
		ImageFinder finder = new ImageFinder();

		// Test exact domain match
		Assert.assertTrue(finder.isSameDomain("https://example.com/page", "example.com"));

		// Test subdomain
		Assert.assertTrue(finder.isSameDomain("https://sub.example.com/page", "example.com"));

		// Test different domains
		Assert.assertFalse(finder.isSameDomain("https://different.com/page", "example.com"));

		// Test invalid URL
		Assert.assertFalse(finder.isSameDomain("invalid-url", "example.com"));
	}

	/**
	 * Test a simplified findImagesFromUrl - doesn't actually crawl web, just tests structure
	 */
	@Test
	public void testFindImagesFromUrlSimulated() {
		// Create a testable subclass to avoid actual web crawling
		class TestImageFinder extends ImageFinder {
			@Override
			protected List<String> findImagesFromUrl(String startUrl, String domain) {
				// Return a fixed list instead of crawling
				List<String> result = new ArrayList<>();
				result.add("https://example.com/test1.jpg");
				result.add("https://example.com/test2.png");
				result.add("https://example.com/test3.gif");
				return result;
			}
		}

		// Test with our subclass
		TestImageFinder finder = new TestImageFinder();
		List<String> images = finder.findImagesFromUrl("https://example.com", "example.com");

		Assert.assertEquals(3, images.size());
		Assert.assertEquals("https://example.com/test1.jpg", images.get(0));
		Assert.assertEquals("https://example.com/test2.png", images.get(1));
		Assert.assertEquals("https://example.com/test3.gif", images.get(2));
	}
}