package myau.module.modules;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class TransactionAnalyzerTest {
    @Test
    public void handlesNoTransactionsAndUnknownEvidence() {
        assertEquals("Vanilla / No Anticheat", TransactionAnalyzer.classify(Collections.<Short>emptyList()));
        assertEquals("Unknown", TransactionAnalyzer.classify(shorts(9, -7)));
    }

    @Test
    public void recognizesDeterministicPatterns() {
        assertEquals("Grim", TransactionAnalyzer.classify(shorts(0, -1, -2, -3)));
        assertEquals("Karhu", TransactionAnalyzer.classify(shorts(-3000, -3001, -3002, -3003)));
        assertEquals("Frequency", TransactionAnalyzer.classify(shorts(23767, 23767, 23767)));
        assertEquals("AGC", TransactionAnalyzer.classify(shorts(1, 2, 3, 4)));
        assertEquals("Vulcan", TransactionAnalyzer.classify(shorts(-31000, -31001, -31002, -31003)));
    }

    private static java.util.List<Short> shorts(int... values) {
        Short[] result = new Short[values.length];
        for (int i = 0; i < values.length; i++) result[i] = (short) values[i];
        return Arrays.asList(result);
    }
}
