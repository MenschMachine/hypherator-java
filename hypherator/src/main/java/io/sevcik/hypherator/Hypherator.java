package io.sevcik.hypherator;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.sevcik.hypherator.dto.DictionaryEntry;
import io.sevcik.hypherator.dto.HyphenationCandidate;
import io.sevcik.hypherator.dto.HyphenationCandidateKind;
import io.sevcik.hypherator.dto.HyphenationSplit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Main entry point for working with the hyphenation package.
 * <p>
 * This class provides global, thread-safe access to all supported locale dictionaries.
 * Dictionaries are loaded once per classloader and always shared between all instances
 * of {@code Hyphenator}. You are free to create as many instances as you wish, as all
 * hyphenation data is managed and shared internally.
 * <br><br>
 * This approach ensures efficient memory usage and keeps
 * hyphenation operations lightweight for your application.
 * <p>
 * Use {@link #getInstance(String)} to create new hyphenation iterators for specific locales.
 * <p>
 * Sponsored by <a href="https://pdf365.cloud">pdf365.cloud</a>.
 */


public class Hypherator {
    private static final Logger logger = LoggerFactory.getLogger(Hypherator.class);
    private static final String ALL_JSON_PATH = "/hyphen/all.json";

    private static final Map<String, DictionaryResource> dictionaryResources = loadDictionaryIndex();
    private static final Map<String, HyphenDict> dictionaries = new ConcurrentHashMap<>();

    /**
     * Creates a new Hypherator facade. Dictionaries are loaded lazily on first use.
     */
    protected Hypherator() {
    }

    /**
     * Retrieves a new {@link HyphenationIterator} instance for the given locale.
     *
     * @param locale the locale identifier (e.g. "en-US")
     * @return a new {@link HyphenationIterator} for the locale, or {@code null} if no dictionary is available for the locale
     *
     * <p>
     * <b>Usage Note:</b> The returned iterator is the recommended way to access hyphenation points and process hyphenation in text.
     * Calling this method repeatedly for the same locale will create a new iterator instance each time,
     * but the underlying dictionary is not shared between {@code Hyphenator} instances. For efficiency,
     * load and reuse the {@code Hyphenator} and its dictionaries as a singleton.
     * </p>
     */
    public static HyphenationIterator getInstance(String locale) {
        HyphenDict dict = getOrLoadDictionary(locale);
        if (dict == null) {
            return null;
        }
        return new HyphenationIteratorImpl(dict);
    }

    /**
     * Returns all hyphenation candidates for a word in one call.
     *
     * @param locale the locale identifier (for example "en-US")
     * @param word the logical word to hyphenate
     * @return all break candidates for the word, or an empty list when unavailable
     */
    public static List<HyphenationCandidate> hyphenate(String locale, String word) {
        if (word == null || word.isEmpty()) {
            return List.of();
        }

        HyphenDict dict = getOrLoadDictionary(locale);
        if (dict == null) {
            return List.of();
        }

        HyphenateImpl hyphenate = new HyphenateImpl();
        return hyphenate.hyphenate(dict, word).stream()
                .map(Hypherator::toCandidate)
                .toList();
    }

    /**
     * Applies a public batch API candidate to a word.
     *
     * @param word the original logical word
     * @param candidate the breakpoint to apply
     * @return the split text around the break
     */
    public static HyphenationSplit applyBreak(String word, HyphenationCandidate candidate) {
        if (word == null) {
            throw new IllegalArgumentException("word cannot be null");
        }
        if (candidate == null) {
            throw new IllegalArgumentException("candidate cannot be null");
        }

        PotentialBreakImpl breakImpl = fromCandidate(candidate);
        var split = new HyphenateImpl().applyBreak(word, breakImpl);
        return new HyphenationSplit(split.getFirst(), split.getSecond());
    }


    /**
     * Builds a new {@link HyphenationIterator} instance from provided input stream
     * @param inputStream the input stream with dictionary data
     * @return a new {@link HyphenationIterator} for the given input stream
     * @throws IOException In case the dictionary cannot be read.
     */
    public static HyphenationIterator getInstance(InputStream inputStream) throws IOException{
        HyphenDict dict = HyphenDictBuilder.fromInputStream(inputStream);
        return new HyphenationIteratorImpl(dict);
    }

    /**
     * Loads all dictionaries from the all.json resource file.
     * 
     * @throws IOException if there's an error loading the dictionaries
     */
    protected static void loadDictionaries() throws IOException {
        for (String locale : dictionaryResources.keySet()) {
            getOrLoadDictionary(locale);
        }
    }

    private static Map<String, DictionaryResource> loadDictionaryIndex() {
        try (InputStream is = Hypherator.class.getResourceAsStream(ALL_JSON_PATH)) {
            if (is == null) {
                throw new IOException("Resource not found: " + ALL_JSON_PATH);
            }

            ObjectMapper objectMapper = new ObjectMapper();
            List<DictionaryEntry> entries = objectMapper.readValue(is, new TypeReference<List<DictionaryEntry>>() {});

            Map<String, DictionaryResource> resources = new ConcurrentHashMap<>();
            int localeCount = 0;
            for (DictionaryEntry entry : entries) {
                if (entry.getLocations() == null || entry.getLocations().isEmpty() || entry.getLocales() == null || entry.getLocales().isEmpty()) {
                    continue;
                }

                String location = entry.getLocations().get(0);
                String resourcePath = "/hyphen/" + location;
                String hyphen = entry.getHyphen();
                for (String locale : entry.getLocales()) {
                    String normalizedLocale = normalizeLocale(locale);
                    if (normalizedLocale == null) {
                        continue;
                    }
                    resources.put(normalizedLocale, new DictionaryResource(resourcePath, hyphen));
                    localeCount++;
                }
            }

            logger.info("Registered {} locales for lazy dictionary loading", localeCount);
            return resources;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads a dictionary from a resource path.
     * 
     * @param resourcePath the path to the dictionary resource
     * @return the loaded dictionary
     * @throws IOException if there's an error loading the dictionary
     */
    protected static HyphenDict loadDictionaryFromResource(String resourcePath) throws IOException {
        try (InputStream is = Hypherator.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }

            // Load the dictionary directly from the input stream
            return HyphenDictBuilder.fromInputStream(is);
        }
    }

    protected Map<String, HyphenDict> getDictionaries() {
        return dictionaries;
    }

    protected HyphenDict getDictionary(String locale) {
        return getOrLoadDictionary(locale);
    }

    private static HyphenDict getOrLoadDictionary(String locale) {
        String normalizedLocale = normalizeLocale(locale);
        if (normalizedLocale == null) {
            return null;
        }

        DictionaryResource resource = dictionaryResources.get(normalizedLocale);
        if (resource == null) {
            return null;
        }

        return dictionaries.computeIfAbsent(normalizedLocale, ignored -> {
            try {
                logger.info("Loading dictionary: {} ({})", resource.resourcePath(), normalizedLocale);
                HyphenDict dict = loadDictionaryFromResource(resource.resourcePath());
                dict.hyphen = resource.hyphen();
                return dict;
            } catch (IOException e) {
                logger.warn("Failed to load dictionary: {}", resource.resourcePath(), e);
                return null;
            }
        });
    }

    private static String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return null;
        }
        return locale.replace('_', '-');
    }

    private static HyphenationCandidate toCandidate(io.sevcik.hypherator.dto.PotentialBreak potentialBreak) {
        PotentialBreakImpl breakImpl = (PotentialBreakImpl) potentialBreak;
        HyphenDict.BreakRule breakRule = breakImpl.breakRule();
        return new HyphenationCandidate(
                breakImpl.position(),
                breakImpl.priority(),
                breakRule != null ? breakRule.getReplacement() : null,
                breakRule != null ? breakRule.getReplacementIndex() : 0,
                breakRule != null ? breakRule.getReplacementCount() : 0,
                breakImpl.kind());
    }

    private static PotentialBreakImpl fromCandidate(HyphenationCandidate candidate) {
        HyphenDict.BreakRule breakRule = new HyphenDict.BreakRule()
                .setValue(candidate.priority());
        if (candidate.hasReplacement()) {
            breakRule.setReplacement(candidate.replacement())
                    .setReplacementIndex(candidate.replacementIndex())
                    .setReplacementCount(candidate.replacementCount());
        }
        return new PotentialBreakImpl(
                candidate.logicalOffset(),
                candidate.priority(),
                breakRule,
                candidate.kind() != null ? candidate.kind() : HyphenationCandidateKind.STANDARD);
    }

    private record DictionaryResource(String resourcePath, String hyphen) {
    }
}
