package me.ksyz.accountmanager.utils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Simple random username generator. Attempts to load resource lists
 * from /nameGenerator/adjectives.txt and /nameGenerator/animals.txt on the
 * classpath. If missing, falls back to small built-in lists.
 */
public final class NameGenerator {
    private static final List<String> ADJECTIVES = loadLinesOrDefault("/nameGenerator/adjectives.txt", new String[]{"cool","fast","silent","brave","crazy","tiny","big","frosty","red","blue"});
    private static final List<String> ANIMALS = loadLinesOrDefault("/nameGenerator/animals.txt", new String[]{"fox","wolf","hawk","lion","tiger","panda","otter","shark","eagle","bear"});
    private static final Random RNG = new Random();

    private static List<String> loadLinesOrDefault(String resource, String[] fallback) {
        try (InputStream is = NameGenerator.class.getResourceAsStream(resource)) {
            if (is == null) {
                List<String> out = new ArrayList<>();
                for (String s : fallback) out.add(s);
                return out;
            }
            List<String> lines = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) lines.add(line);
                }
            }
            return lines.isEmpty() ? java.util.Arrays.asList(fallback) : lines;
        } catch (Exception e) {
            return java.util.Arrays.asList(fallback);
        }
    }

    /**
     * Generate a random username with max length between 8 and 16 by default.
     * The format is adjective + optional '_' + animal + optional digits.
     */
    public static String randomUsername() {
        return randomUsername(8 + RNG.nextInt(9));
    }

    public static String randomUsername(int maxLength) {
        String first = ADJECTIVES.get(RNG.nextInt(ADJECTIVES.size()));
        String second = ANIMALS.get(RNG.nextInt(ANIMALS.size()));
        // ensure total length fits
        if (first.length() + second.length() > maxLength) {
            // try swap or shorten
            if (second.length() <= maxLength) {
                first = first.substring(0, Math.max(1, Math.min(first.length(), maxLength - second.length())));
            } else {
                second = second.substring(0, Math.max(1, Math.min(second.length(), maxLength - first.length())));
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(first);
        // random delimiter
        if (RNG.nextBoolean() && sb.length() + 1 + second.length() <= maxLength) sb.append('_');
        sb.append(second);

        // optionally append digits
        int remaining = maxLength - sb.length();
        if (remaining > 0 && RNG.nextInt(5) != 0) {
            int digits = Math.min(remaining, 3);
            int num = RNG.nextInt((int) Math.pow(10, digits));
            sb.append(num);
        }

        return sb.toString();
    }
}

