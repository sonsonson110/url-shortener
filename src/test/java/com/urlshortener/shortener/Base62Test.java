package com.urlshortener.shortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class Base62Test {

    @Test
    void encodesKnownValues() {
        assertThat(Base62.encode(0)).isEqualTo("0");
        assertThat(Base62.encode(9)).isEqualTo("9");
        assertThat(Base62.encode(10)).isEqualTo("a");
        assertThat(Base62.encode(35)).isEqualTo("z");
        assertThat(Base62.encode(36)).isEqualTo("A");
        assertThat(Base62.encode(61)).isEqualTo("Z");
        assertThat(Base62.encode(62)).isEqualTo("10");
        assertThat(Base62.encode(125)).isEqualTo("21");
        assertThat(Base62.encode(3844)).isEqualTo("100"); // 62^2
    }

    @Test
    void decodesKnownValues() {
        assertThat(Base62.decode("0")).isEqualTo(0);
        assertThat(Base62.decode("Z")).isEqualTo(61);
        assertThat(Base62.decode("10")).isEqualTo(62);
        assertThat(Base62.decode("21")).isEqualTo(125);
    }

    @Test
    void roundTripsRepresentativeValues() {
        long[] values = {0, 1, 61, 62, 63, 3843, 3844, 1_000_000L, 56_800_235_583L, Long.MAX_VALUE};
        for (long v : values) {
            assertThat(Base62.decode(Base62.encode(v))).as("round trip of %d", v).isEqualTo(v);
        }
    }

    @Test
    void distinctNumbersGiveDistinctCodes() {
        // Invariant behind collision-free IDs: encode is one-to-one.
        var seen = new java.util.HashSet<String>();
        for (long v = 0; v < 100_000; v++) {
            assertThat(seen.add(Base62.encode(v))).as("duplicate code for %d", v).isTrue();
        }
    }

    @Test
    void longMaxValueFitsInShortCodeColumn() {
        // links.short_code is VARCHAR(12)
        assertThat(Base62.encode(Long.MAX_VALUE).length()).isLessThanOrEqualTo(12);
    }

    @Test
    void encodeRejectsNegative() {
        assertThatThrownBy(() -> Base62.encode(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decodeRejectsInvalidInput() {
        assertThatThrownBy(() -> Base62.decode("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Base62.decode("ab-c")).isInstanceOf(IllegalArgumentException.class);
    }
}
