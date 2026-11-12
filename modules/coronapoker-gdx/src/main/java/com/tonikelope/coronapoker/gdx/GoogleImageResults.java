/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.utils.JsonReader;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Extracts thumbnail/original pairs from a rendered Google Images document. */
final class GoogleImageResults {

    private static final String SEARCH = "https://www.google.com/search";
    static final int NO_MORE_RESULTS = -1;
    private static final int PROVIDER_STEP = 10;
    private static final String URL_STRING = "\"https?://[^\"\\r\\n]{1,4096}\"";
    private static final Pattern RATE_LIMIT_FORM = Pattern.compile(
            "<form\\b[^>]{0,1024}\\s(?:id\\s*=\\s*['\"]captcha-form['\"]"
            + "|action\\s*=\\s*['\"](?:https://(?:www|sorry)\\.google\\.com)?/sorry/index(?:[?'\"]))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IMAGE = Pattern.compile(
            "\\[\\s*(" + URL_STRING + ")\\s*,\\s*\\d+\\s*,\\s*\\d+\\s*\\]"
            + "\\s*,\\s*\\[\\s*(" + URL_STRING + ")\\s*,\\s*\\d+\\s*,\\s*\\d+\\s*\\]"
            + "(?:\\s*,\\s*null\\s*,\\s*\\d+\\s*,\\s*\"rgb\\([^\"]{1,24}\\)\""
            + "\\s*,\\s*null\\s*,\\s*([01])\\s*,)?");

    private GoogleImageResults() { }

    static String searchUrl(String term, boolean english) {
        return searchUrl(term, english, 0);
    }

    static String searchUrl(String term, boolean english, int offset) {
        String url = SEARCH + "?udm=2&safe=off&hl=" + (english ? "en" : "es")
                + "&q=" + URLEncoder.encode(term, StandardCharsets.UTF_8);
        return offset == 0 ? url : url + "&start=" + offset;
    }

    static int nextOffset(int offset) {
        return offset > Integer.MAX_VALUE - PROVIDER_STEP ? NO_MORE_RESULTS : offset + PROVIDER_STEP;
    }

    static long retryAt(String retryAfter, long receivedAt) {
        if (retryAfter == null || retryAfter.isBlank()) return 0;
        String value = retryAfter.strip();
        try {
            long deadline = value.chars().allMatch(digit -> digit >= '0' && digit <= '9')
                    ? Math.addExact(receivedAt, Math.multiplyExact(Long.parseLong(value), 1000L))
                    : ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
            return deadline > receivedAt ? deadline : 0;
        } catch (NumberFormatException | ArithmeticException | DateTimeException invalid) {
            return 0;
        }
    }

    static boolean isSearch(String url) {
        URI uri = parse(url);
        return uri != null && "https".equals(uri.getScheme())
                && "www.google.com".equals(uri.getHost()) && "/search".equals(uri.getPath());
    }

    static boolean isSearchFor(String url, String term) {
        return isSearchFor(url, term, 0);
    }

    static boolean isSearchFor(String url, String term, int offset) {
        if (!isSearch(url)) return false;
        String query = URI.create(url).getRawQuery();
        if (query == null) return false;
        String foundTerm = null;
        int foundOffset = 0;
        for (String parameter : query.split("&")) {
            if (parameter.startsWith("q=")) {
                foundTerm = URLDecoder.decode(parameter.substring(2), StandardCharsets.UTF_8);
            } else if (parameter.startsWith("start=")) {
                String value = URLDecoder.decode(parameter.substring(6), StandardCharsets.UTF_8);
                try {
                    foundOffset = value.isEmpty() ? 0 : Integer.parseInt(value);
                } catch (NumberFormatException invalid) {
                    return false;
                }
            }
        }
        return term.equals(foundTerm) && offset == foundOffset;
    }

    static boolean isGoogleRetry(String url) {
        URI uri = parse(url);
        return uri != null && "https".equals(uri.getScheme())
                && "www.google.com".equals(uri.getHost())
                && uri.getPath().startsWith("/httpservice/retry/");
    }

    static boolean isGoogleInterruption(String url) {
        URI uri = parse(url);
        return (uri != null && "https".equals(uri.getScheme()) && "consent.google.com".equals(uri.getHost()))
                || isGoogleRateLimited(uri);
    }

    static boolean isGoogleRateLimited(String url) {
        return isGoogleRateLimited(parse(url));
    }

    private static boolean isGoogleRateLimited(URI uri) {
        return uri != null && "https".equals(uri.getScheme())
                && ("www.google.com".equals(uri.getHost()) || "sorry.google.com".equals(uri.getHost()))
                && uri.getPath().startsWith("/sorry/");
    }

    static boolean isEmptyDocument(String html) {
        return html.contains("id=\"topstuff\"></div><div id=\"search\"></div>")
                && html.contains("id=\"botstuff\"") && html.contains("gws-wiz-serp");
    }

    static boolean isRateLimitedDocument(String html) {
        return RATE_LIMIT_FORM.matcher(html).find();
    }

    static boolean isThumbnail(String url) {
        URI uri = parse(url);
        return uri != null && "https".equals(uri.getScheme())
                && uri.getHost().matches("encrypted-tbn[0-9]+\\.gstatic\\.com")
                && "/images".equals(uri.getPath())
                && uri.getRawQuery() != null && uri.getRawQuery().startsWith("q=tbn:");
    }

    static List<Image> parseDocument(String html) {
        LinkedHashMap<String, Image> images = new LinkedHashMap<>();
        var pairs = IMAGE.matcher(html);
        JsonReader json = new JsonReader();
        while (pairs.find()) {
            try {
                String thumb = json.parse(pairs.group(1)).asString();
                String original = json.parse(pairs.group(2)).asString();
                Image image = image(original, thumb, "1".equals(pairs.group(3)));
                if (image != null) images.putIfAbsent(original, image);
            } catch (RuntimeException invalid) {
                // Google can include incomplete image data beside complete results.
            }
        }
        return List.copyOf(images.values());
    }

    static List<Image> appendNew(List<Image> previous, List<Image> received) {
        var seen = new HashSet<String>();
        for (Image image : previous) seen.add(image.original());
        var images = new ArrayList<>(previous);
        for (Image image : received) {
            if (seen.add(image.original())) images.add(image);
        }
        return List.copyOf(images);
    }

    static Image image(String original, String thumbnail, boolean animated) {
        if (original == null || thumbnail == null || original.length() > 4096 || thumbnail.length() > 4096) {
            return null;
        }
        URI uri = parse(original);
        if (uri == null || !isThumbnail(thumbnail) || isThumbnail(original)) return null;
        boolean gifExtension = uri.getPath().toLowerCase(Locale.ROOT).endsWith(".gif");
        return new Image(original, thumbnail, uri.getHost(), animated || gifExtension);
    }

    private static URI parse(String value) {
        if (value == null) return null;
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            String scheme = uri.getScheme();
            return ("https".equals(scheme) || "http".equals(scheme))
                    && host != null && uri.getRawUserInfo() == null
                    && (uri.getPort() < 0 || uri.getPort() == ("https".equals(scheme) ? 443 : 80))
                    ? uri : null;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    record Image(String original, String thumbnail, String host, boolean animated) { }
}
