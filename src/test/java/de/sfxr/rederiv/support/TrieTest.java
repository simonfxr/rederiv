package de.sfxr.rederiv.support;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TrieTest {

    @BeforeAll
    static void setup() {
        TestUtil.init();
    }

    private void printStream(String nm, Trie t) {
        System.err.println("Trie: " + nm);
        t.stream().forEach(System.err::println);
    }

    private Set<String> finiteSetOf(Trie t) {
        return t.stream().collect(Collectors.toSet());
    }

    @Test
    void test1() {
        printStream("empty", Trie.empty());
        assertEquals(Set.of(), finiteSetOf(Trie.empty()));

        printStream("epsilon", Trie.epsilon());
        assertEquals(Set.of(""), finiteSetOf(Trie.epsilon()));

        var d = Trie.epsilon().cons(Interval.of('0', '1' + 1));

        printStream("d", d);
        assertEquals(Set.of("0", "1"), finiteSetOf(d));

        var d1 = Trie.epsilon().cons(Interval.of('1', '1' + 1));

        printStream("d1", d1);
        assertEquals(Set.of("1"), finiteSetOf(d1));

        var d2 = d1.concat(d);
        printStream("d2", d2);
        assertEquals(Set.of("10", "11"), finiteSetOf(d2));

        var d12 = d.union(d2);
        printStream("d12", d12);
        assertEquals(Set.of("0", "1", "10", "11"), finiteSetOf(d12));

    }
}
