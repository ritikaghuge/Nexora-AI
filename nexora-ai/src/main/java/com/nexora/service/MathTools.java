package com.nexora.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Real calculator/statistics tool: results are computed in Java and handed to the model as ground truth. */
@Service
public class MathTools {
    private static final Pattern EXPR_OK = Pattern.compile("^[0-9a-zA-Z+\\-*/^().,%×÷\\s]+$");
    private static final Pattern PREFIX = Pattern.compile("(?i)^(please\\s+)?(calculate|compute|evaluate|what is|what's|solve|find|simplify)\\s*[:]?\\s*");
    private static final Pattern NUM = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final Pattern STAT_WORDS = Pattern.compile("(?i)\\b(mean|average|median|variance|standard deviation|std|stdev|sum)\\b");

    public static String format(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.valueOf((long) v);
        return new BigDecimal(v).round(new MathContext(12)).stripTrailingZeros().toPlainString();
    }

    public Optional<String> note(String text) {
        List<String> parts = new ArrayList<>();
        String cand = PREFIX.matcher(text.trim()).replaceFirst("").replaceAll("[?=.\\s]+$", "").trim();
        if (!cand.isEmpty() && cand.length() <= 300 && EXPR_OK.matcher(cand).matches()
                && cand.matches(".*\\d.*") && cand.matches(".*([+\\-*/^%×÷(]|[a-zA-Z]).*")) {
            try {
                parts.add("Expression " + cand + " = " + format(ExpressionEvaluator.eval(cand)));
            } catch (RuntimeException ignored) { /* not a pure expression */ }
        }
        if (STAT_WORDS.matcher(text).find()) {
            Matcher m = NUM.matcher(text);
            List<Double> xs = new ArrayList<>();
            while (m.find() && xs.size() < 500) xs.add(Double.parseDouble(m.group()));
            if (xs.size() >= 2) parts.add(stats(xs));
        }
        if (parts.isEmpty()) return Optional.empty();
        return Optional.of("TOOL RESULTS (computed exactly by the application's Java calculator, not by you). "
                + "Treat them as ground truth, show your working, and do not contradict them:\n- " + String.join("\n- ", parts));
    }

    static String stats(List<Double> xs) {
        double[] a = xs.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        int n = a.length;
        double sum = Arrays.stream(a).sum();
        double mean = sum / n;
        double median = n % 2 == 1 ? a[n / 2] : (a[n / 2 - 1] + a[n / 2]) / 2.0;
        double ss = Arrays.stream(a).map(x -> (x - mean) * (x - mean)).sum();
        double popVar = ss / n;
        double sampVar = ss / (n - 1);
        return "Data " + Arrays.toString(a) + ": n=" + n + ", sum=" + format(sum) + ", mean=" + format(mean)
                + ", median=" + format(median) + ", min=" + format(a[0]) + ", max=" + format(a[n - 1])
                + ", population variance=" + format(popVar) + ", population std dev=" + format(Math.sqrt(popVar))
                + ", sample variance=" + format(sampVar) + ", sample std dev=" + format(Math.sqrt(sampVar));
    }
}
