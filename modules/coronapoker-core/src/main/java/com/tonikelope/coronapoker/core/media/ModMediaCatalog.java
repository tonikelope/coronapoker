/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
package com.tonikelope.coronapoker.core.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Renderer-neutral description and resolver for an optional external mod.
 *
 * <p>The catalog never copies mod media into the application. It only exposes
 * normalized, readable paths below {@code <install>/mod}, so Swing and GDX can
 * share discovery while retaining their own media decoders.</p>
 */
public final class ModMediaCatalog {

    private static final Logger LOGGER = Logger.getLogger(
            ModMediaCatalog.class.getName());

    private final Path root;
    private final boolean installed;
    private final String name;
    private final String version;
    private final boolean fuseSounds;
    private final boolean fuseCinematics;
    private final String font;
    private final List<String> decks;

    private ModMediaCatalog(Path root, boolean installed, String name,
            String version, boolean fuseSounds, boolean fuseCinematics,
            String font, List<String> decks) {
        this.root = root;
        this.installed = installed;
        this.name = name;
        this.version = version;
        this.fuseSounds = fuseSounds;
        this.fuseCinematics = fuseCinematics;
        this.font = font;
        this.decks = List.copyOf(decks);
    }

    public static ModMediaCatalog discover(Path installationDirectory) {
        Objects.requireNonNull(installationDirectory, "installationDirectory");
        Path root = installationDirectory.toAbsolutePath().normalize()
                .resolve("mod").normalize();
        Path descriptor = root.resolve("mod.xml");
        if (!Files.isRegularFile(descriptor) || !Files.isReadable(descriptor)) {
            return empty(root);
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",
                    true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities",
                    false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities",
                    false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            // Xerces' default handler prints malformed external XML straight
            // to stderr before throwing. Keep startup/tests clean and report
            // the rejected descriptor once through the application logger.
            builder.setErrorHandler(new DefaultHandler());
            Document document = builder.parse(descriptor.toFile());
            Element mod = (Element) document.getElementsByTagName("mod").item(0);
            String name = text(document, "name").orElse("MOD");
            String version = text(document, "version").orElse("");
            boolean fuseSounds = attribute(mod, "fusion_sounds", false);
            boolean fuseCinematics = attribute(mod, "fusion_cinematics", false);
            String font = text(document, "font").orElse("");
            return new ModMediaCatalog(root, true, name, version,
                    fuseSounds, fuseCinematics, font, discoverDecks(root));
        } catch (Exception failure) {
            LOGGER.log(Level.WARNING,
                    "Could not load external CoronaPoker mod catalog: {0}",
                    failure.getMessage());
            return empty(root);
        }
    }

    private static ModMediaCatalog empty(Path root) {
        return new ModMediaCatalog(root, false, "", "", false, false,
                "", List.of());
    }

    private static List<String> discoverDecks(Path root) throws IOException {
        Path folder = root.resolve("decks");
        if (!Files.isDirectory(folder) || !Files.isReadable(folder)) {
            return List.of();
        }
        ArrayList<String> result = new ArrayList<>();
        try (var children = Files.list(folder)) {
            children.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !name.isBlank())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(result::add);
        }
        return result;
    }

    private static Optional<String> text(Document document, String tag) {
        Node node = document.getElementsByTagName(tag).item(0);
        if (node == null || node.getTextContent() == null) {
            return Optional.empty();
        }
        String value = node.getTextContent().trim();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    private static boolean attribute(Element element, String name,
            boolean fallback) {
        if (element == null || !element.hasAttribute(name)) {
            return fallback;
        }
        return Boolean.parseBoolean(element.getAttribute(name).trim());
    }

    public boolean installed() {
        return installed;
    }

    public String name() {
        return name;
    }

    public String version() {
        return version;
    }

    public boolean fuseSounds() {
        return fuseSounds;
    }

    public boolean fuseCinematics() {
        return fuseCinematics;
    }

    public Optional<Path> font() {
        return font.isBlank() ? Optional.empty()
                : resolve("fonts/" + font);
    }

    public List<String> decks() {
        return decks;
    }

    /**
     * Resolves one language-specific funny-sound category using the same
     * replacement/fusion contract as the classic frontend.
     */
    public Map.Entry<String, String[]> soundCategory(String language,
            String category, Map.Entry<String, String[]> bundled) {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(bundled, "bundled");
        String relative = "joke/" + language.toLowerCase(java.util.Locale.ROOT)
                + "/" + category.toLowerCase(java.util.Locale.ROOT) + "/";
        List<String> external = files("sounds/" + relative, ".wav").stream()
                .map(path -> path.getFileName().toString()).toList();
        if (external.isEmpty()) {
            return copyEntry(bundled);
        }
        ArrayList<String> selected = new ArrayList<>();
        if (fuseSounds) selected.addAll(Arrays.asList(bundled.getValue()));
        selected.addAll(external);
        return new AbstractMap.SimpleImmutableEntry<>(relative,
                selected.toArray(String[]::new));
    }

    private static Map.Entry<String, String[]> copyEntry(
            Map.Entry<String, String[]> source) {
        return new AbstractMap.SimpleImmutableEntry<>(source.getKey(),
                source.getValue().clone());
    }

    /** Resolves a readable file without permitting traversal outside the mod. */
    public Optional<Path> resolve(String relativePath) {
        if (!installed || relativePath == null || relativePath.isBlank()) {
            return Optional.empty();
        }
        Path candidate = root.resolve(relativePath.replace('\\', '/'))
                .normalize();
        return candidate.startsWith(root) && Files.isRegularFile(candidate)
                && Files.isReadable(candidate)
                ? Optional.of(candidate) : Optional.empty();
    }

    /** Lists readable files immediately below one mod media directory. */
    public List<Path> files(String relativeDirectory, String extension) {
        if (!installed || relativeDirectory == null) {
            return List.of();
        }
        Path directory = root.resolve(relativeDirectory.replace('\\', '/'))
                .normalize();
        if (!directory.startsWith(root) || !Files.isDirectory(directory)
                || !Files.isReadable(directory)) {
            return List.of();
        }
        String suffix = extension == null ? ""
                : extension.toLowerCase(java.util.Locale.ROOT);
        try (var children = Files.list(directory)) {
            return children.filter(Files::isRegularFile)
                    .filter(Files::isReadable)
                    .filter(path -> suffix.isEmpty() || path.getFileName()
                    .toString().toLowerCase(java.util.Locale.ROOT)
                    .endsWith(suffix))
                    .sorted(Comparator.comparing(path -> path.getFileName()
                    .toString(), String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (IOException failure) {
            LOGGER.log(Level.WARNING,
                    "Could not enumerate mod media directory " + directory,
                    failure);
            return List.of();
        }
    }
}
