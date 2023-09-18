package no.sikt.generator;

import static java.util.Locale.ENGLISH;
import static nva.commons.core.attempt.Try.attempt;
import com.google.common.io.Resources;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import nva.commons.core.ioutils.IoUtils;

public final class Utils {

    private Utils() {
    }

    public static String readResource(String filename) {
        URL url = Resources.getResource(filename);
        return attempt(() -> Resources.toString(url, StandardCharsets.UTF_8)).orElseThrow();
    }

    public static InputStream readResourceAsStream(String filename) {
        return IoUtils.inputStreamFromResources(filename);
    }

    public static String toSnakeCase(String string) {
        return string.replaceAll("\\s+", "-").toLowerCase(ENGLISH);
    }

    public static <T> Predicate<T> distinctByKey(Function<? super T,Object> keyExtractor) {
        Map<Object,Boolean> seen = new ConcurrentHashMap<>();
        return t -> Objects.isNull(seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE));
    }

}
