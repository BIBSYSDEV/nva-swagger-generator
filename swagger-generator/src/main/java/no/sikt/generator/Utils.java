package no.sikt.generator;

import static java.util.Locale.ENGLISH;
import static nva.commons.core.ioutils.IoUtils.inputStreamFromResources;
import static nva.commons.core.ioutils.IoUtils.streamToString;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

public final class Utils {

  private Utils() {}

  public static String readResource(String filename) {
    return streamToString(inputStreamFromResources(filename));
  }

  public static String toSnakeCase(String string) {
    return string.replaceAll("\\s+", "-").toLowerCase(ENGLISH);
  }

  public static <T> Predicate<T> distinctByKey(Function<? super T, Object> keyExtractor) {
    Map<Object, Boolean> seen = new ConcurrentHashMap<>();
    return t -> Objects.isNull(seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE));
  }
}
