/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.gdx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.tonikelope.coronapoker.gdx.GdxImageSearchHistory.History;
import com.tonikelope.coronapoker.gdx.GdxImageSearchHistory.Snapshot;
import java.awt.EventQueue;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.BiPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.browser.CefRequestContext;
import org.cef.browser.LibGdxCefBrowser;
import org.cef.callback.CefCallback;
import org.cef.callback.CefContextMenuParams;
import org.cef.callback.CefMenuModel;
import org.cef.callback.CefQueryCallback;
import org.cef.callback.CefResourceReadCallback;
import org.cef.handler.CefContextMenuHandlerAdapter;
import org.cef.handler.CefFocusHandlerAdapter;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.handler.CefResourceHandler;
import org.cef.handler.CefResourceHandlerAdapter;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.handler.CefResourceRequestHandlerAdapter;
import org.cef.misc.BoolRef;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;

/** Renders a local gallery and reads Google Images in a separate off-screen browser. */
final class PokerImageGalleryBrowser implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(PokerImageGalleryBrowser.class.getName());
    private static final String ORIGIN = "https://images.coronapoker.invalid";
    private static final String ROOT = "/image-search/";
    private static final String PREVIEW_URL = ORIGIN + ROOT + "preview/";
    private static final String SEARCH_PREVIEW_URL = ORIGIN + ROOT + "search-preview/";
    private static final String CSP = "default-src 'none'; script-src 'self'; style-src 'self'; "
            + "font-src 'self'; img-src 'self' https://*.gstatic.com; base-uri 'none'; "
            + "form-action 'none'; frame-ancestors 'none'";
    private static final long STARTUP_TIMEOUT = TimeUnit.SECONDS.toNanos(35);
    private static final long SEARCH_TIMEOUT = TimeUnit.SECONDS.toNanos(17);
    private static final int MAX_DOCUMENT_CHARS = 12 * 1024 * 1024;
    private static final int MAX_PENDING_DOWNLOADS = 4;
    private static final int IDLE_FRAME_RATE = 30;
    private static final int SCROLL_FRAME_RATE = 60;
    private static final long SCROLL_FRAME_RATE_NANOS = TimeUnit.MILLISECONDS.toNanos(250);
    private static final GdxImageSearchGate PROVIDER_GATE = new GdxImageSearchGate();

    private final boolean english;
    private final String pageUrl;
    private final Consumer<String> saveInGallery;
    private final BiPredicate<String, Boolean> sendToChat;
    private final Consumer<Boolean> receiveImages;
    private final Runnable clearGallery;
    private final Consumer<String> removeFromGallery;
    private final Consumer<History> saveSearch;
    private final Map<Integer, Snapshot> searches = new ConcurrentHashMap<>();
    private final Set<String> downloading = ConcurrentHashMap.newKeySet();
    private final PokerVideoFrameBuffer frames = new PokerVideoFrameBuffer();
    private final ScheduledThreadPoolExecutor worker = new ScheduledThreadPoolExecutor(2, task -> {
        Thread thread = new Thread(task, "CoronaPoker-image-search");
        thread.setDaemon(true);
        return thread;
    });
    private final long started = System.nanoTime();
    private final ScheduledThreadPoolExecutor previews = previewExecutor("search");
    private final ScheduledThreadPoolExecutor favoritePreviews = previewExecutor("favorites");

    private static ScheduledThreadPoolExecutor previewExecutor(String name) {
        var executor = new ScheduledThreadPoolExecutor(2, task -> {
            Thread thread = new Thread(task, "CoronaPoker-gallery-" + name + "-preview");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    private volatile boolean closed;
    private volatile boolean ready;
    private volatile boolean failed;
    private volatile LibGdxCefBrowser browser;
    private volatile LibGdxCefBrowser google;
    private volatile Search activeSearch;
    private volatile Search navigatingSearch;
    private volatile Search loadedSearch;
    private int selectedSearch;
    private int nextSearchId;
    private int selectionVersion;
    private int viewVersion;
    private volatile Gallery gallery;
    private CefClient client;
    private LibGdxCefBrowser closingBrowser;
    private final Set<LibGdxCefBrowser> pendingCreations = new HashSet<>();
    private final GdxBrowserKeyboard keyboard = new GdxBrowserKeyboard();
    private boolean pressed;
    private double scrollRemainder;
    private double queuedWheel;
    private float wheelX;
    private float wheelY;
    private int wheelModifiers;
    private long scrollUntil;
    private boolean scrolling;
    private int width;
    private int height;

    PokerImageGalleryBrowser(boolean english, int width, int height,
            List<String> existingImages, boolean autoReceive, Consumer<String> saveInGallery,
            BiPredicate<String, Boolean> sendToChat, Consumer<Boolean> receiveImages,
            Runnable clearGallery, Consumer<String> removeFromGallery,
            History previousSearch, Consumer<History> saveSearch) {
        this.english = english;
        this.width = width;
        this.height = height;
        this.saveInGallery = saveInGallery;
        this.sendToChat = sendToChat;
        this.receiveImages = receiveImages;
        this.clearGallery = clearGallery;
        this.removeFromGallery = removeFromGallery;
        this.saveSearch = saveSearch;
        gallery = new Gallery(1, List.copyOf(existingImages), autoReceive);
        for (Snapshot search : previousSearch.searches()) {
            int id = ++nextSearchId;
            searches.put(id, search);
            if (search.term().equals(previousSearch.selected())) selectedSearch = id;
        }
        if (selectedSearch == 0 && !searches.isEmpty()) selectedSearch = 1;
        pageUrl = ORIGIN + ROOT + "index.html?lang=" + (english ? "en" : "es");
        double scale = scale();
        worker.execute(() -> open(width, height, scale));
    }

    void setGallery(List<String> images, boolean autoReceive) {
        Gallery previous = gallery;
        if (previous.urls().equals(images) && previous.autoReceive() == autoReceive) return;
        int revision = previous.urls().equals(images) ? previous.revision() : previous.revision() + 1;
        gallery = new Gallery(revision, List.copyOf(images), autoReceive);
        LibGdxCefBrowser current = browser;
        if (ready && current != null) {
            current.executeJavaScript("window.refreshLocalGallery?.()", pageUrl, 0);
        }
    }

    private void open(int width, int height, double scale) {
        try {
            Map<String, Asset> assets = Map.of(
                    pageUrl, asset(ROOT + "index.html", "text/html"),
                    ORIGIN + ROOT + "gallery.css", asset(ROOT + "gallery.css", "text/css"),
                    ORIGIN + ROOT + "gallery.js", asset(ROOT + "gallery.js", "text/javascript"),
                    ORIGIN + "/ui-tooltip.js", asset("/ui-tooltip.js", "text/javascript"),
                    ORIGIN + "/ui-tooltip.css", asset("/ui-tooltip.css", "text/css"),
                    ORIGIN + "/fonts/McLaren-Regular.ttf", asset("/fonts/McLaren-Regular.ttf", "font/ttf"));
            if (closed) return;
            CefClient created = PokerChromium.createClient(null);
            synchronized (this) {
                if (closed) {
                    EventQueue.invokeLater(() -> PokerChromium.disposeClient(created));
                    return;
                }
                client = created;
                installHandlers(created, assets);
                browser = createBrowser(created, pageUrl, frames, width, height, IDLE_FRAME_RATE);
                launch(browser);
                browser.resizeBrowser(width, height, scale);
            }
        } catch (Exception | LinkageError failure) {
            if (!closed) {
                LOG.log(Level.WARNING, "Could not start image search browser", failure);
                failed = true;
            }
        }
    }

    private void launch(LibGdxCefBrowser created) {
        pendingCreations.add(created);
        try {
            created.createImmediately();
        } catch (RuntimeException | LinkageError failure) {
            if (pendingCreations.remove(created)) {
                if (created == browser) browser = null;
                if (created == google) google = null;
                created.onBeforeClose();
            }
            throw failure;
        }
    }

    private static LibGdxCefBrowser createBrowser(CefClient client, String url,
            PokerVideoFrameBuffer frames, int width, int height, int fps) {
        CefRequestContext context = CefRequestContext.createContext(null);
        if (context == null) throw new IllegalStateException("No isolated Chromium context");
        CefBrowserSettings settings = new CefBrowserSettings();
        settings.windowless_frame_rate = fps;
        try {
            return new LibGdxCefBrowser(client, url, context, settings, frames, width, height);
        } catch (RuntimeException | LinkageError failure) {
            context.dispose();
            throw failure;
        }
    }

    private void installHandlers(CefClient target, Map<String, Asset> assets) {
        CefResourceRequestHandler localAssets = new CefResourceRequestHandlerAdapter() {
            @Override
            public CefResourceHandler getResourceHandler(CefBrowser source, CefFrame frame,
                    CefRequest request) {
                if (!"GET".equals(request.getMethod())) return null;
                String url = request.getURL();
                Asset asset = assets.get(url);
                if (asset != null) return new AssetHandler(asset);
                if (source == browser && frame != null && frame.isMain()
                        && pageUrl.equals(frame.getURL())) {
                    return previewHandler(url);
                }
                return null;
            }
        };
        target.addRequestHandler(new CefRequestHandlerAdapter() {
            @Override
            public boolean onBeforeBrowse(CefBrowser source, CefFrame frame,
                    CefRequest request, boolean gesture, boolean redirect) {
                if (frame == null || !frame.isMain()) return true;
                String url = request.getURL();
                if (source == browser) return !pageUrl.equals(url);
                if (source != google) return true;
                if ("about:blank".equals(url)) return false;
                Search search = activeSearch;
                if (search == null) return true;
                if (GoogleImageResults.isSearchFor(url, search.term(), search.offset())) {
                    navigatingSearch = search;
                    return false;
                }
                if (navigatingSearch != search) return true;
                if (GoogleImageResults.isGoogleInterruption(url)) {
                    failSearch(search, GoogleImageResults.isGoogleRateLimited(url)
                            ? SearchFailure.UNUSUAL_TRAFFIC : SearchFailure.CONSENT);
                    return true;
                }
                return !GoogleImageResults.isGoogleRetry(url);
            }

            @Override
            public CefResourceRequestHandler getResourceRequestHandler(CefBrowser source,
                    CefFrame frame, CefRequest request, boolean navigation, boolean download,
                    String initiator, BoolRef disableDefaultHandling) {
                if (source == google) {
                    Search search = activeSearch;
                    String url = request.getURL();
                    boolean searchUrl = GoogleImageResults.isSearch(url);
                    if (!download && search != null && !unusedProviderResource(request)
                            && (!searchUrl || GoogleImageResults.isSearchFor(url, search.term(), search.offset()))) {
                        return searchUrl && navigation && frame != null && frame.isMain()
                                ? providerResponse(search) : null;
                    }
                    disableDefaultHandling.set(true);
                    return null;
                }
                if (source == browser && !navigation && !download
                        && GoogleImageResults.isThumbnail(request.getURL())) return null;
                disableDefaultHandling.set(true);
                return localAssets;
            }

            @Override
            public void onRenderProcessTerminated(CefBrowser source,
                    TerminationStatus status, int errorCode, String errorString) {
                if (source == browser) failed = true;
                Search search = activeSearch;
                if (source == google && search != null) failSearch(search, SearchFailure.UNAVAILABLE);
            }
        });
        target.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
            @Override
            public boolean onBeforePopup(CefBrowser source, CefFrame frame, String url, String targetFrame) {
                return true;
            }

            @Override
            public void onAfterCreated(CefBrowser created) {
                synchronized (PokerImageGalleryBrowser.this) {
                    pendingCreations.remove(created);
                    if (closed) {
                        // Let native creation unwind before closing the browser.
                        EventQueue.invokeLater(PokerImageGalleryBrowser.this::closeNextBrowser);
                        return;
                    }
                }
                if (created == browser && created instanceof LibGdxCefBrowser rendered) {
                    rendered.requestRepaint();
                }
            }

            @Override
            public void onBeforeClose(CefBrowser closing) {
                synchronized (PokerImageGalleryBrowser.this) {
                    pendingCreations.remove(closing);
                    if (closing == closingBrowser) closingBrowser = null;
                    if (closing == browser) {
                        browser = null;
                        if (!closed) {
                            failed = true;
                            if (activeSearch != null) stopProviderSearch(0);
                        }
                    } else if (closing == google) {
                        google = null;
                        Search search = activeSearch;
                        if (search != null) failSearch(search, SearchFailure.UNAVAILABLE);
                    }
                    if (closed && client != null) {
                        EventQueue.invokeLater(PokerImageGalleryBrowser.this::closeNextBrowser);
                    }
                }
            }
        });
        target.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadEnd(CefBrowser source, CefFrame frame, int statusCode) {
                Search search = activeSearch;
                if (source != google || frame == null || !frame.isMain() || search == null
                        || navigatingSearch != search
                        || !GoogleImageResults.isSearchFor(frame.getURL(), search.term(), search.offset())) return;
                if (statusCode == 429) {
                    // Fallback for 429 responses whose headers were unavailable.
                    reportRateLimit(search, null);
                } else if (statusCode >= 400) {
                    failProviderLoad(frame.getURL(), "HTTP " + statusCode, SearchFailure.UNAVAILABLE);
                } else if (statusCode >= 200 && statusCode < 300 && loadedSearch != search) {
                    loadedSearch = search;
                    pollDocument(search, 0);
                }
            }

            @Override
            public void onLoadError(CefBrowser source, CefFrame frame, ErrorCode errorCode,
                    String errorText, String failedUrl) {
                if (source == browser && pageUrl.equals(failedUrl) && !closed) failed = true;
                if (source == google && frame != null && frame.isMain()
                        && errorCode != ErrorCode.ERR_ABORTED) {
                    failProviderLoad(failedUrl, errorCode.name(), SearchFailure.UNAVAILABLE);
                }
            }
        });
        target.addFocusHandler(new CefFocusHandlerAdapter() {
            @Override
            public boolean onSetFocus(CefBrowser source, FocusSource focusSource) {
                return source != browser || focusSource == FocusSource.FOCUS_SOURCE_NAVIGATION;
            }
        });
        target.addContextMenuHandler(new CefContextMenuHandlerAdapter() {
            @Override
            public void onBeforeContextMenu(CefBrowser source, CefFrame frame,
                    CefContextMenuParams params, CefMenuModel menu) {
                menu.clear();
            }
        });
        CefMessageRouter router = CefMessageRouter.create();
        router.addHandler(new CefMessageRouterHandlerAdapter() {
            @Override
            public boolean onQuery(CefBrowser source, CefFrame frame, long id,
                    String request, boolean persistent, CefQueryCallback reply) {
                if (closed || source != browser || frame == null || !frame.isMain()
                        || !pageUrl.equals(frame.getURL()) || persistent
                        || request == null || request.length() > 170) return false;
                if ("ready".equals(request)) {
                    ready = true;
                    reply.success("");
                } else if (request.startsWith("search\t")) {
                    beginSearch(request.substring(7).strip(), reply);
                } else if (request.equals("search-state")) {
                    reply.success(json(searchView()));
                } else if (request.equals("image-cache-size")) {
                    imageCacheAction(false, reply);
                } else if (request.equals("purge-image-cache")) {
                    imageCacheAction(true, reply);
                } else if (request.startsWith("search-")) {
                    searchTabAction(request.split("\t", -1), reply);
                } else if (request.startsWith("save\t") || request.startsWith("send-result\t")) {
                    useResult(request.split("\t", -1), reply);
                } else if (request.startsWith("saved\t")) {
                    reportSavedResults(request.split("\t", -1), reply);
                } else if (request.equals("gallery")) {
                    Gallery snapshot = gallery;
                    reply.success(json(new GalleryResponse(snapshot.revision(),
                            snapshot.urls().size(), snapshot.autoReceive())));
                } else if (request.startsWith("send\t") || request.startsWith("remove\t")) {
                    galleryAction(request.split("\t", -1), reply);
                } else if (request.equals("receive") || request.equals("clear")) {
                    Gdx.app.postRunnable(() -> {
                        if (closed) return;
                        if (request.equals("clear")) clearGallery.run();
                        else receiveImages.accept(!gallery.autoReceive());
                        reply.success("");
                    });
                } else {
                    reply.failure(400, "Unknown image search action");
                }
                return true;
            }
        }, true);
        target.addMessageRouter(router);
    }

    private void imageCacheAction(boolean purge, CefQueryCallback reply) {
        try {
            worker.execute(() -> {
                try {
                    List<String> favorites = gallery.urls();
                    long bytes = purge ? GdxChatImageLoader.purgeImageCache(favorites)
                            : GdxChatImageLoader.imageCacheSize(favorites);
                    if (!closed) reply.success(Long.toString(bytes));
                } catch (IOException | RuntimeException failure) {
                    LOG.log(Level.WARNING, "Could not access the image cache", failure);
                    if (!closed) reply.failure(500, "Could not access the image cache");
                }
            });
        } catch (RejectedExecutionException closing) {
            reply.failure(503, "Image search is closing");
        }
    }

    private CefResourceRequestHandler providerResponse(Search search) {
        return new CefResourceRequestHandlerAdapter() {
            @Override
            public boolean onResourceResponse(CefBrowser source, CefFrame frame,
                    CefRequest request, CefResponse response) {
                if (response.getStatus() == 429) {
                    reportRateLimit(search, response.getHeaderByName("Retry-After"));
                }
                return false;
            }
        };
    }

    private void reportRateLimit(Search search, String retryAfter) {
        long retryAt = GoogleImageResults.retryAt(retryAfter, System.currentTimeMillis());
        if (retryAt > 0) search.retryAt().accumulateAndGet(retryAt, Math::max);
        try {
            worker.execute(() -> failSearch(search, SearchFailure.RATE_LIMITED));
        } catch (RejectedExecutionException closing) {
            // Closing the gallery releases its provider slot.
        }
    }

    private static boolean unusedProviderResource(CefRequest request) {
        var type = request.getResourceType();
        if (type == null) return false;
        return switch (type) {
            case RT_IMAGE, RT_MEDIA, RT_FONT_RESOURCE, RT_FAVICON, RT_PREFETCH, RT_PING -> true;
            default -> false;
        };
    }

    private void galleryAction(String[] fields, CefQueryCallback reply) {
        Gallery snapshot = gallery;
        String url;
        try {
            if (fields.length != 3 || Integer.parseInt(fields[1]) != snapshot.revision()) {
                throw new IllegalArgumentException("Stale gallery");
            }
            url = snapshot.urls().get(Integer.parseInt(fields[2]));
        } catch (IllegalArgumentException | IndexOutOfBoundsException invalid) {
            reply.failure(400, "Unknown gallery image");
            return;
        }
        if ("send".equals(fields[0])) {
            Gdx.app.postRunnable(() -> {
                if (closed) return;
                if (gallery.urls().contains(url) && sendToChat.test(url, true)) reply.success("");
                else reply.failure(409, "Image was not sent");
            });
        } else {
            Gdx.app.postRunnable(() -> {
                if (closed) return;
                if (gallery.urls().contains(url)) removeFromGallery.accept(url);
                reply.success("");
            });
        }
    }

    private CefResourceHandler previewHandler(String requestUrl) {
        if (closed) return null;
        boolean search = requestUrl.startsWith(SEARCH_PREVIEW_URL);
        String prefix = search ? SEARCH_PREVIEW_URL : PREVIEW_URL;
        if (!requestUrl.startsWith(prefix)) return null;
        int separator = requestUrl.indexOf('/', prefix.length());
        if (separator < 0) return null;
        try {
            int revision = Integer.parseInt(requestUrl.substring(prefix.length(), separator));
            int index = Integer.parseInt(requestUrl.substring(separator + 1));
            if (search) {
                Snapshot snapshot = searches.get(revision);
                if (snapshot == null) return null;
                var image = snapshot.images().get(index);
                return image.animated() ? new PreviewHandler(image.original(), true) : null;
            }
            Gallery snapshot = gallery;
            return revision == snapshot.revision()
                    ? new PreviewHandler(snapshot.urls().get(index), false) : null;
        } catch (IllegalArgumentException | IndexOutOfBoundsException invalid) {
            return null;
        }
    }

    private static Asset previewAsset(byte[] bytes) throws IOException {
        if (GdxChatImageLoader.isGif(bytes)) {
            return new Asset(bytes, "image/gif");
        }
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported gallery image");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int size = Math.max(reader.getWidth(0), reader.getHeight(0));
                if (size > 16384) throw new IOException("Gallery image is too large");
                int sample = Math.max(1, (size + 383) / 384);
                var settings = reader.getDefaultReadParam();
                settings.setSourceSubsampling(sample, sample, 0, 0);
                var preview = reader.read(0, settings);
                var output = new ByteArrayOutputStream();
                try (var encoded = new MemoryCacheImageOutputStream(output)) {
                    ImageIO.write(preview, "png", encoded);
                    encoded.flush();
                    return new Asset(output.toByteArray(), "image/png");
                } finally {
                    preview.flush();
                }
            } finally {
                reader.dispose();
            }
        }
    }

    private synchronized void beginSearch(String term, CefQueryCallback reply) {
        String key = GdxImageSearchHistory.key(term);
        if (closed || key.isEmpty() || term.length() > 160 || client == null) {
            reply.failure(400, "Image search unavailable");
            return;
        }
        var existing = searches.entrySet().stream()
                .filter(entry -> key.equals(GdxImageSearchHistory.key(entry.getValue().term())))
                .findFirst().orElse(null);
        if (existing != null) {
            Snapshot saved = existing.getValue();
            if (!saved.images().isEmpty() || saved.nextOffset() < 0) {
                selectSearch(existing.getKey());
                reply.success(json(searchView()));
                return;
            }
            term = saved.term();
        }
        if (activeSearch != null) {
            reply.failure(409, "An image search is already in progress");
            return;
        }
        int id = existing == null ? ++nextSearchId : existing.getKey();
        startProviderSearch(id, term, 0, false, reply);
    }

    private void failProviderLoad(String url, String reason, SearchFailure failure) {
        Search search = activeSearch;
        if (search != null && navigatingSearch == search
                && GoogleImageResults.isSearchFor(url, search.term(), search.offset())) {
            LOG.log(Level.FINE, "Image provider navigation failed: {0}", reason);
            failSearch(search, failure);
        }
    }

    private synchronized void searchTabAction(String[] fields, CefQueryCallback reply) {
        try {
            int id = Integer.parseInt(fields[1]);
            Snapshot current = searches.get(id);
            if (current == null) throw new IllegalArgumentException("Unknown search");
            switch (fields[0]) {
                case "search-select" -> selectSearch(id);
                case "search-delete" -> deleteSearch(id);
                case "search-page" -> {
                    int page = Integer.parseInt(fields[2]);
                    if (page < 0 || page >= current.pageCount() || id != selectedSearch) {
                        throw new IllegalArgumentException("Unknown search page");
                    }
                    selectSearchPage(id, current, page);
                }
                case "search-more" -> {
                    beginMoreResults(id, current, Integer.parseInt(fields[2]), reply);
                    return;
                }
                default -> throw new IllegalArgumentException("Unknown search action");
            }
            reply.success(json(searchView()));
        } catch (IllegalArgumentException | IndexOutOfBoundsException invalid) {
            reply.failure(400, "Unknown image search or page");
        }
    }

    private void selectSearch(int id) {
        selectionVersion++;
        if (selectedSearch == id) return;
        selectedSearch = id;
        persistSearches();
    }

    private void selectSearchPage(int id, Snapshot search, int page) {
        selectionVersion++;
        if (search.page() == page) return;
        searches.put(id, search.withPage(page));
        persistSearches();
    }

    private void deleteSearch(int id) {
        List<Integer> remaining = orderedSearchIds();
        int previous = remaining.indexOf(id);
        searches.remove(id);
        if (selectedSearch == id) {
            remaining = orderedSearchIds();
            selectedSearch = remaining.isEmpty() ? 0 : remaining.get(Math.min(previous, remaining.size() - 1));
            selectionVersion++;
        }
        Search pending = activeSearch;
        if (pending != null && pending.id() == id) failSearch(pending, SearchFailure.DELETED);
        persistSearches();
    }

    private void beginMoreResults(int id, Snapshot current, int page, CefQueryCallback reply) {
        if (id != selectedSearch || page != current.page()) {
            reply.success(json(searchView()));
            return;
        }
        if (page + 1 < current.pageCount()) {
            selectSearchPage(id, current, page + 1);
            reply.success(json(searchView()));
            return;
        }
        if (closed || client == null || activeSearch != null || current.nextOffset() < 0
                || current.images().isEmpty()) {
            reply.failure(409, "Additional image results are unavailable");
            return;
        }
        startProviderSearch(id, current.term(), current.nextOffset(), true, reply);
    }

    private void startProviderSearch(int id, String term, int offset, boolean append, CefQueryCallback reply) {
        Search search = new Search(id, term, offset, append, selectionVersion, reply,
                System.nanoTime() + SEARCH_TIMEOUT, new AtomicLong());
        long wait = PROVIDER_GATE.begin(search, System.currentTimeMillis());
        if (wait == GdxImageSearchGate.BUSY) {
            reply.failure(409, "An image search is already in progress");
            return;
        }
        if (wait > 0) {
            reply.failure(429, "retry-after:" + wait);
            return;
        }
        activeSearch = search;
        String url = GoogleImageResults.searchUrl(term, english, offset);
        try {
            if (google == null) {
                var ignoredFrames = new PokerVideoFrameBuffer();
                ignoredFrames.setAcceptingFrames(false);
                google = createBrowser(client, url, ignoredFrames, 1140, 700, 1);
                launch(google);
            } else {
                google.loadURL(url);
            }
        } catch (RuntimeException | LinkageError failure) {
            LOG.log(Level.WARNING, "Could not open Google Images", failure);
            failSearch(search, SearchFailure.UNAVAILABLE);
            return;
        }
        worker.schedule(() -> failSearch(search, SearchFailure.UNAVAILABLE), SEARCH_TIMEOUT, TimeUnit.NANOSECONDS);
    }

    private void pollDocument(Search search, long delay) {
        try {
            worker.schedule(() -> inspectDocument(search), delay, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException closing) {
            failSearch(search, SearchFailure.UNAVAILABLE);
        }
    }

    private void inspectDocument(Search search) {
        LibGdxCefBrowser provider = google;
        if (closed || provider == null || activeSearch != search || loadedSearch != search) return;
        if (System.nanoTime() >= search.deadline()) {
            failSearch(search, SearchFailure.UNAVAILABLE);
            return;
        }
        String url = provider.getURL();
        if (navigatingSearch != search || !GoogleImageResults.isSearchFor(url, search.term(), search.offset())) {
            pollDocument(search, 550);
        } else {
            provider.getSource(html -> {
                try {
                    worker.execute(() -> readDocument(search, html));
                } catch (RejectedExecutionException closing) {
                    failSearch(search, SearchFailure.UNAVAILABLE);
                }
            });
        }
    }

    private void readDocument(Search search, String html) {
        if (closed || activeSearch != search) return;
        if (html != null && html.length() <= MAX_DOCUMENT_CHARS) {
            if (GoogleImageResults.isRateLimitedDocument(html)) {
                failSearch(search, SearchFailure.UNUSUAL_TRAFFIC);
                return;
            }
            List<GoogleImageResults.Image> images = GoogleImageResults.parseDocument(html);
            if (!images.isEmpty() || GoogleImageResults.isEmptyDocument(html)) {
                finishSearch(search, images);
                return;
            }
        }
        pollDocument(search, 650);
    }

    private synchronized void failSearch(Search search, SearchFailure failure) {
        if (closed || activeSearch != search) return;
        long now = System.currentTimeMillis();
        long retryAt = search.retryAt().get();
        if (retryAt <= now && failure.cooldownSeconds > 0) retryAt = now + failure.cooldownSeconds * 1000L;
        long wait = stopProviderSearch(retryAt);
        if (failure != SearchFailure.DELETED) {
            LOG.log(Level.INFO, "Image search stopped: {0} (offset {1})",
                    new Object[]{failure.reason, search.offset()});
        }
        search.reply().failure(failure.code,
                failure.cooldownSeconds > 0 && wait > 0 ? failure.reason + ":" + wait : failure.reason);
    }

    private synchronized void finishSearch(Search search, List<GoogleImageResults.Image> images) {
        if (closed || activeSearch != search) return;
        stopProviderSearch(0);
        Snapshot previous = searches.get(search.id());
        int start = search.append() ? previous.images().size() : 0;
        List<GoogleImageResults.Image> combined = search.append()
                ? GoogleImageResults.appendNew(previous.images(), images) : images;
        // Offset zero also resumes searches saved before continuation was tracked.
        boolean more = !images.isEmpty() && (search.offset() == 0 || combined.size() > start);
        int next = more ? GoogleImageResults.nextOffset(search.offset()) : GoogleImageResults.NO_MORE_RESULTS;
        boolean stillSelected = search.selectionVersion() == selectionVersion;
        Snapshot completed = search.append()
                ? previous.append(combined, next, stillSelected && selectedSearch == search.id())
                : new Snapshot(search.term(), combined, next);
        searches.put(search.id(), completed);
        if (!search.append() && (stillSelected || selectedSearch == 0)) selectedSearch = search.id();
        persistSearches();
        search.reply().success(json(searchView()));
    }

    private long stopProviderSearch(long retryAt) {
        Search finished = activeSearch;
        activeSearch = null;
        navigatingSearch = null;
        loadedSearch = null;
        LibGdxCefBrowser provider = google;
        try {
            if (finished != null && provider != null) provider.stopLoad();
        } catch (RuntimeException | LinkageError failure) {
            LOG.log(Level.FINE, "Could not stop the image provider navigation", failure);
        }
        return PROVIDER_GATE.finish(finished, System.currentTimeMillis(), retryAt);
    }

    private List<Integer> orderedSearchIds() {
        return searches.keySet().stream().sorted().toList();
    }

    private synchronized SearchView searchView() {
        var tabs = orderedSearchIds().stream().map(id -> new SearchTab(id, searches.get(id).term())).toList();
        Snapshot current = searches.get(selectedSearch);
        return new SearchView(viewVersion, tabs, current == null ? null : response(selectedSearch, current),
                PROVIDER_GATE.remainingSeconds(System.currentTimeMillis()));
    }

    private Response response(int id, Snapshot snapshot) {
        List<String> local = gallery.urls();
        var cards = snapshot.pageImages().stream()
                .map(image -> new Card(image.thumbnail(), isSaved(image, local), image.animated())).toList();
        return new Response(id, snapshot.term(), snapshot.page(), snapshot.pageCount(),
                snapshot.firstIndex(), cards, snapshot.nextOffset() >= 0);
    }

    private void persistSearches() {
        viewVersion++;
        Snapshot current = searches.get(selectedSearch);
        saveSearch.accept(new History(current == null ? "" : current.term(),
                orderedSearchIds().stream().map(searches::get).toList()));
    }

    private void reportSavedResults(String[] fields, CefQueryCallback reply) {
        List<GoogleImageResults.Image> images;
        try {
            Snapshot snapshot = searches.get(Integer.parseInt(fields[1]));
            int page = Integer.parseInt(fields[2]);
            if (snapshot == null || page < 0 || page >= snapshot.pageCount()) {
                throw new IllegalArgumentException("Unknown search page");
            }
            images = snapshot.withPage(page).pageImages();
        } catch (IllegalArgumentException | IndexOutOfBoundsException invalid) {
            reply.failure(409, "Search has changed");
            return;
        }
        List<String> local = gallery.urls();
        boolean[] saved = new boolean[images.size()];
        for (int index = 0; index < saved.length; index++) {
            saved[index] = isSaved(images.get(index), local);
        }
        reply.success(json(saved));
    }

    private static boolean isSaved(GoogleImageResults.Image image, List<String> urls) {
        return urls.contains(image.original()) || urls.contains(image.thumbnail());
    }

    private static String json(Object response) {
        Json json = new Json(JsonWriter.OutputType.json);
        json.setTypeName(null);
        json.setUsePrototypes(false);
        return json.toJson(response);
    }

    private void useResult(String[] fields, CefQueryCallback reply) {
        GoogleImageResults.Image image;
        try {
            Snapshot snapshot = searches.get(Integer.parseInt(fields[1]));
            if (fields.length != 3 || snapshot == null) throw new IllegalArgumentException("Stale search");
            image = snapshot.images().get(Integer.parseInt(fields[2]));
        } catch (IllegalArgumentException | IndexOutOfBoundsException invalid) {
            reply.failure(400, "Unknown image result");
            return;
        }
        boolean save = "save".equals(fields[0]);
        if (save && isSaved(image, gallery.urls())) {
            reply.success("existing");
            return;
        }
        if (downloading.size() >= MAX_PENDING_DOWNLOADS || !downloading.add(image.original())) {
            reply.failure(429, "Image downloads are busy");
            return;
        }
        try {
            worker.execute(() -> download(image, save, reply));
        } catch (RejectedExecutionException closing) {
            downloading.remove(image.original());
            reply.failure(503, "Image search is closing");
        }
    }

    private void download(GoogleImageResults.Image image, boolean save, CefQueryCallback reply) {
        try {
            String url = prepareImage(image);
            Gdx.app.postRunnable(() -> {
                downloading.remove(image.original());
                if (closed) return;
                try {
                    if (!save) {
                        if (sendToChat.test(url, false)) reply.success("");
                        else reply.failure(409, "Image was not sent");
                    } else if (!gallery.urls().contains(url)) {
                        saveInGallery.accept(url);
                        reply.success("added");
                    } else {
                        reply.success("existing");
                    }
                } catch (RuntimeException failure) {
                    reply.failure(500, "Could not update gallery");
                }
            });
        } catch (IOException | RuntimeException invalid) {
            downloading.remove(image.original());
            if (!closed) reply.failure(502, "Image could not be downloaded");
        }
    }

    private static String prepareImage(GoogleImageResults.Image image) throws IOException {
        String url = image.original();
        byte[] bytes = GdxChatImageLoader.downloadPublic(url);
        if (GdxChatImageLoader.isWebp(bytes)) {
            // The existing chat wire format carries a URL. Use Google's compatible
            // preview for WebP so other players can open it with the current decoder.
            url = image.thumbnail();
            bytes = GdxChatImageLoader.downloadPublic(url);
        }
        GdxPreparedChatMedia.prepare(bytes, "image-search", 320).dispose();
        return url;
    }

    boolean failed() {
        return failed || !interactive() && System.nanoTime() - started > STARTUP_TIMEOUT;
    }

    private record Search(int id, String term, int offset, boolean append, int selectionVersion,
            CefQueryCallback reply, long deadline, AtomicLong retryAt) { }
    private record Card(String thumbnail, boolean saved, boolean animated) { }
    private record Response(int batch, String term, int page, int pageCount, int firstIndex,
            List<Card> images, boolean hasMore) { }
    private record SearchTab(int batch, String term) { }
    private record SearchView(int version, List<SearchTab> tabs, Response result, long retryAfter) { }
    private record Gallery(int revision, List<String> urls, boolean autoReceive) { }
    private record GalleryResponse(int revision, int size, boolean autoReceive) { }

    Texture texture(int width, int height) {
        this.width = width;
        this.height = height;
        LibGdxCefBrowser current = browser;
        if (current != null) {
            current.resizeBrowser(width, height, scale());
            boolean boost = scrollUntil != 0L && System.nanoTime() - scrollUntil < 0L;
            if (boost != scrolling) {
                scrolling = boost;
                current.setWindowlessFrameRate(boost ? SCROLL_FRAME_RATE : IDLE_FRAME_RATE);
            }
            flushWheel(current);
        }
        return ready ? frames.uploadPendingFrame() : null;
    }

    private static double scale() {
        return Math.min(Gdx.graphics.getBackBufferWidth() / (double) Math.max(1, Gdx.graphics.getWidth()),
                Gdx.graphics.getBackBufferHeight() / (double) Math.max(1, Gdx.graphics.getHeight()));
    }

    Cursor.SystemCursor cursor() {
        return switch (frames.cursorType()) {
            case java.awt.Cursor.HAND_CURSOR -> Cursor.SystemCursor.Hand;
            case java.awt.Cursor.TEXT_CURSOR -> Cursor.SystemCursor.Ibeam;
            default -> Cursor.SystemCursor.Arrow;
        };
    }

    boolean interactive() {
        return ready && frames.uploadedFrames() > 0;
    }

    void unfocus() {
        keyboard.clear();
        pressed = false;
        LibGdxCefBrowser current = browser;
        if (current != null) current.setFocus(false);
    }

    void mouse(int event, float x, float y) {
        LibGdxCefBrowser current = browser;
        if (!ready || current == null) return;
        if (event == MouseEvent.MOUSE_PRESSED) {
            keyboard.clear();
            current.setFocus(true);
            pressed = true;
        } else if (event == MouseEvent.MOUSE_RELEASED) {
            pressed = false;
        } else if (event == MouseEvent.MOUSE_DRAGGED) {
            scrollUntil = System.nanoTime() + SCROLL_FRAME_RATE_NANOS;
        }
        int button = event == MouseEvent.MOUSE_PRESSED || event == MouseEvent.MOUSE_RELEASED
                ? MouseEvent.BUTTON1 : MouseEvent.NOBUTTON;
        current.dispatchMouse(event, Math.round(x * width), Math.round(y * height),
                button, modifiers(), button == MouseEvent.BUTTON1 ? 1 : 0);
    }

    void wheel(float x, float y, float amount) {
        if (!ready || !Float.isFinite(amount) || amount == 0f) return;
        scrollUntil = System.nanoTime() + SCROLL_FRAME_RATE_NANOS;
        queuedWheel += amount * 80d;
        wheelX = x;
        wheelY = y;
        wheelModifiers = modifiers();
    }

    private void flushWheel(LibGdxCefBrowser current) {
        if (queuedWheel == 0d) return;
        // Coalesce precision wheel events before crossing into CEF and keep fractions.
        double distance = queuedWheel + scrollRemainder;
        queuedWheel = 0d;
        int pixels = (int) distance;
        scrollRemainder = distance - pixels;
        if (pixels != 0) {
            current.dispatchWheelPixels(Math.round(wheelX * width),
                    Math.round(wheelY * height), wheelModifiers, -pixels);
        }
    }

    void key(int event, int key, char character) {
        LibGdxCefBrowser current = browser;
        if (!ready || current == null) return;
        int modifiers = modifiers();
        keyboard.key(current, event, key, character, modifiers);
    }

    void updateKeyboard(float delta, boolean enabled) {
        keyboard.update(delta, enabled && ready ? browser : null, this::modifiers);
    }

    private int modifiers() {
        return LibGdxCefBrowser.awtModifiers(
                either(Input.Keys.SHIFT_LEFT, Input.Keys.SHIFT_RIGHT),
                either(Input.Keys.CONTROL_LEFT, Input.Keys.CONTROL_RIGHT),
                either(Input.Keys.ALT_LEFT, Input.Keys.ALT_RIGHT),
                Gdx.input.isKeyPressed(Input.Keys.SYM), pressed, false, false);
    }

    private static boolean either(int first, int second) {
        return Gdx.input.isKeyPressed(first) || Gdx.input.isKeyPressed(second);
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        ready = false;
        keyboard.clear();
        stopProviderSearch(0);
        downloading.clear();
        worker.shutdownNow();
        previews.shutdownNow();
        favoritePreviews.shutdownNow();
        frames.setAcceptingFrames(false);
        try {
            EventQueue.invokeLater(this::closeNextBrowser);
        } finally {
            frames.close();
        }
    }

    private void closeNextBrowser() {
        LibGdxCefBrowser next;
        CefClient disposing = null;
        synchronized (this) {
            if (!closed || client == null || closingBrowser != null) return;
            next = browser != null && !pendingCreations.contains(browser) ? browser
                    : google != null && !pendingCreations.contains(google) ? google : null;
            if (next != null) {
                closingBrowser = next;
            } else {
                if (!pendingCreations.isEmpty()) return;
                disposing = client;
                client = null;
            }
        }
        // CefClient.dispose iterates its browser map, which close callbacks can mutate.
        if (next == null) {
            PokerChromium.disposeClient(disposing);
        } else {
            next.setCloseAllowed();
            next.close(true);
        }
    }

    private record Asset(byte[] bytes, String mimeType) { }

    private static Asset asset(String path, String mimeType) throws IOException {
        try (InputStream source = PokerImageGalleryBrowser.class.getResourceAsStream(path)) {
            if (source == null) throw new IOException("Missing image search asset " + path);
            return new Asset(source.readAllBytes(), mimeType);
        }
    }

    private static class AssetHandler extends CefResourceHandlerAdapter {
        protected volatile Asset asset;
        private int offset;

        AssetHandler(Asset asset) {
            this.asset = asset;
        }

        @Override
        public boolean open(CefRequest request, BoolRef handleRequest, CefCallback callback) {
            handleRequest.set(true);
            return true;
        }

        @Override
        public boolean processRequest(CefRequest request, CefCallback callback) {
            callback.Continue();
            return true;
        }

        @Override
        public void getResponseHeaders(CefResponse response, IntRef length, StringRef redirect) {
            response.setStatus(200);
            response.setStatusText("OK");
            response.setMimeType(asset.mimeType);
            response.setHeaderMap(Map.of("Content-Security-Policy", CSP,
                    "X-Content-Type-Options", "nosniff", "Cache-Control", "no-store",
                    "Referrer-Policy", "no-referrer"));
            length.set(asset.bytes.length);
        }

        @Override
        public boolean read(byte[] output, int requested, IntRef count, CefResourceReadCallback callback) {
            return copy(output, requested, count);
        }

        @Override
        public boolean readResponse(byte[] output, int requested, IntRef count, CefCallback callback) {
            return copy(output, requested, count);
        }

        private boolean copy(byte[] output, int requested, IntRef count) {
            int size = Math.min(requested, asset.bytes.length - offset);
            count.set(size);
            if (size <= 0) return false;
            System.arraycopy(asset.bytes, offset, output, 0, size);
            offset += size;
            return true;
        }
    }

    private enum SearchFailure {
        UNUSUAL_TRAFFIC(429, "unusual-traffic", 60),
        RATE_LIMITED(429, "rate-limit", 30),
        CONSENT(412, "consent", 0),
        UNAVAILABLE(503, "unavailable", 0),
        DELETED(499, "deleted", 0);

        final int code;
        final String reason;
        final int cooldownSeconds;

        SearchFailure(int code, String reason, int cooldownSeconds) {
            this.code = code;
            this.reason = reason;
            this.cooldownSeconds = cooldownSeconds;
        }
    }

    private final class PreviewHandler extends AssetHandler {
        private final String url;
        private final boolean search;
        private final ScheduledThreadPoolExecutor executor;
        private Future<?> task;
        private volatile boolean cancelled;

        PreviewHandler(String url, boolean search) {
            super(null);
            this.url = url;
            this.search = search;
            executor = search ? previews : favoritePreviews;
        }

        @Override
        public boolean open(CefRequest request, BoolRef handleRequest, CefCallback callback) {
            handleRequest.set(false);
            if (queuePreview(callback)) return true;
            handleRequest.set(true);
            return false;
        }

        @Override
        public boolean processRequest(CefRequest request, CefCallback callback) {
            return queuePreview(callback);
        }

        private synchronized boolean queuePreview(CefCallback callback) {
            if (closed || cancelled) return false;
            try {
                task = executor.submit(() -> loadPreview(callback));
                return true;
            } catch (RejectedExecutionException closing) {
                return false;
            }
        }

        private void loadPreview(CefCallback callback) {
            if (closed || cancelled) return;
            try {
                if (search) {
                    byte[] bytes = GdxChatImageLoader.downloadSearchPreview(url);
                    if (GdxChatImageLoader.isGif(bytes)) asset = new Asset(bytes, "image/gif");
                    else if (GdxChatImageLoader.isWebp(bytes)) asset = new Asset(bytes, "image/webp");
                    else throw new IOException("Search animation is unavailable");
                } else {
                    asset = previewAsset(GdxChatImageLoader.download(url));
                }
            } catch (IOException | RuntimeException invalid) {
                if (!closed && !cancelled) callback.cancel();
                return;
            }
            if (!closed && !cancelled) callback.Continue();
        }

        @Override
        public synchronized void cancel() {
            cancelled = true;
            if (task != null) task.cancel(true);
        }
    }
}
