package de.sfxr.rederiv.support;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class IntervalTest {

    @BeforeAll
    static void setup() {
        TestUtil.init();
    }

    @Test
    void testUnion() {

        // disjoint
        {
            var i1 = Interval.of(0, 3, 1);
            var i2 = Interval.of(4, 5, 2);
            var result = i1.union(i2, TestUtil.INTS).toList();
            var expected = List.of(i1, i2);
            assertEquals(expected, result);

            assertEquals(i2.union(i1, TestUtil.INTS).toList(), expected);
        }

        // overlapping
        {
            var i1 = Interval.of(0, 3, 1);
            var i2 = Interval.of(2, 5, 2);
            var result = i1.union(i2, TestUtil.INTS).toList();
            var expected =
                    List.of(Interval.of(0, 2, 1), Interval.of(2, 3, 3), Interval.of(3, 5, 2));
            assertEquals(expected, result);

            // swapped
            result = i2.union(i1, TestUtil.INTS).toList();
            assertEquals(expected, result);
        }

        // overlapping + neutral value
        {
            var i1 = Interval.of(0, 3, 1);
            var i2 = Interval.of(2, 5, 0);
            var result = i1.union(i2, TestUtil.INTS).toList();
            var expected = List.of(Interval.of(0, 3, 1), Interval.of(3, 5, 0));
            assertEquals(expected, result);

            // swapped
            result = i2.union(i1, TestUtil.INTS).toList();
            assertEquals(expected, result);
        }

        // overlapping + neutral value 2
        {
            var i1 = Interval.of(0, 3, 0);
            var i2 = Interval.of(2, 5, 1);
            var result = i1.union(i2, TestUtil.INTS).toList();
            var expected = List.of(Interval.of(0, 2, 0), Interval.of(2, 5, 1));
            assertEquals(expected, result);

            // swapped
            result = i2.union(i1, TestUtil.INTS).toList();
            assertEquals(expected, result);
        }

        // overlapping + neutral value 3
        {
            var i1 = Interval.of(0, 3, 0);
            var i2 = Interval.of(2, 5, 0);
            var result = i1.union(i2, TestUtil.INTS).toList();
            var expected = List.of(Interval.of(0, 5, 0));
            assertEquals(expected, result);

            // swapped
            result = i2.union(i1, TestUtil.INTS).toList();
            assertEquals(expected, result);
        }

        // inclusion
        {
            var i1 = Interval.of(0, 7, 1);
            var i2 = Interval.of(2, 5, 2);
            var result = i1.union(i2, TestUtil.INTS).toList();
            var expected =
                    List.of(Interval.of(0, 2, 1), Interval.of(2, 5, 3), Interval.of(5, 7, 1));
            assertEquals(expected, result);

            // swapped
            result = i2.union(i1, TestUtil.INTS).toList();
            assertEquals(expected, result);
        }

        // inclusion, left aligned
        {
            var i1 = Interval.of(0, 5, 1);
            var i2 = Interval.of(0, 3, 2);
            var result = i1.union(i2, TestUtil.INTS).toList();
            var expected = List.of(Interval.of(0, 3, 3), Interval.of(3, 5, 1));
            assertEquals(expected, result);

            // swapped
            result = i2.union(i1, TestUtil.INTS).toList();
            assertEquals(expected, result);
        }

        // inclusion, right aligned
        {
            var i1 = Interval.of(0, 5, 1);
            var i2 = Interval.of(2, 5, 2);
            var result = i1.union(i2, TestUtil.INTS).toList();
            var expected = List.of(Interval.of(0, 2, 1), Interval.of(2, 5, 3));
            assertEquals(expected, result);

            // swapped
            result = i2.union(i1, TestUtil.INTS).toList();
            assertEquals(expected, result);
        }
    }
}
