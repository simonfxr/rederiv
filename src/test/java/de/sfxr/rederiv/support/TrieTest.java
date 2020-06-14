package de.sfxr.rederiv.support;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TrieTest {

    @BeforeAll
    static void setup() {
        TestUtil.init();
    }

    private void printStream(Stream<?> xs) {
        xs.forEach(System.out::println);
    }

    @Test
    void test1() {
        var d = Trie.epsilon().cons(Interval.of('0', '1' + 1));
        var d1 = Trie.epsilon().cons(Interval.of('1', '1' + 1));
        var d2 = d.union(d1.concat(d));
        var d3 = d2.union(d2.concat(d));
        printStream(d3.enumateStrings());
    }
}
