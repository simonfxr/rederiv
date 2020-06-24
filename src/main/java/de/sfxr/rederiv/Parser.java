package de.sfxr.rederiv;

import de.sfxr.rederiv.support.Checking;
import de.sfxr.rederiv.support.Interval;
import de.sfxr.rederiv.support.IntervalSet;

import java.util.ArrayList;
import java.util.Objects;
import java.util.function.IntPredicate;

public class Parser {

    private final static boolean CHECKING = Checking.isCheckingEnabled(Parser.class);

    private final String inp;
    private final boolean extended;

    private final static String META_CHARS_BEGIN = "{([?+*^$|.";
    private final static String META_CHARS = META_CHARS_BEGIN + "})]";
    private final static String EXT_META_CHARS_BEGIN = META_CHARS_BEGIN + "&^";
    private final static String EXT_META_CHARS = META_CHARS + "&^";

    private static Re parse(String inp, boolean extended) {
        if (inp.isEmpty())
            return Re.Lit.from("");
        var p = new Parser(inp, extended);
        var re = p.alt();
        if (p.error != null || re == null)
            throw new IllegalArgumentException("Parse exception: " + p.error);
        return re;
    }

    public static Re parse(String inp) {
        return parse(inp, false);
    }

    public static Re parseExtended(String inp) {
        return parse(inp, true);
    }

    private String error = null;
    private int col = 0;
    private int pos = 0;
    private int ch = -1;

    private Parser(String inp, boolean extended) {
        this.inp = inp;
        this.extended = extended;
        if (!inp.isEmpty())
            ch = inp.codePointAt(0);
    }

    private boolean next() {
        pos += Character.charCount(ch);
        if (eof()) {
            ch = -1;
            return false;
        }
        ch = inp.codePointAt(pos);
        return true;
    }

    private boolean error() { return error != null; }

    private boolean eof() { return pos >= inp.length(); }

    private boolean done() { return eof() || error(); }

    private boolean sat(IntPredicate p) {
        if (error()) return false;
        if (!p.test(ch)) return false;
        next();
        return true;
    }

    private boolean expect(IntPredicate p, String hint) {
        if (sat(p)) return true;
        failWith(offset(), hint);
        return false;
    }

    private boolean consume(char c, String hint) {
        return expect(d -> c == d, hint == null ? hint : "expected character " + c);
    }

    private boolean consume(char c) {
        return consume(c, null);
    }

    private boolean any(String hint) {
        if (next()) return true;
        failWith(pos, "Unexpected end of input: " + hint);
        return false;
    }

    private Re alt() {
        var a = isect();
        if (done()) return a;
        if (!sat(c -> c == '|')) return a;
        var b = alt();
        if (b == null) return null;
        return Objects.requireNonNull(a).alt(b);
    }

    private Re isect() {
        if (!extended) return fact();
        var a = fact();
        if (done()) return a;
        if (!sat(c -> c == '&')) return a;
        var b = isect();
        if (b == null) return null;
        return Objects.requireNonNull(a).isect(b);
    }

    private Re fact() {
        if (!extended) return rep();
        boolean neg = sat(c -> c == '!');
        var a = rep();
        if (a == null) return null;
        return neg ? a.neg() : a;
    }

    private int parseInt() {
        int n = ch - '0';
        if (!expect(c -> '1' <= c && c < '9', "non zero decimal digit"))
            return -1;
        while (!eof()) {
            var d = ch - '0';
            if (!sat(c -> '0' <= c && c < '9'))
                break;
            n = n * 10 + d;
        }
        if (CHECKING) System.err.println("PARSED int: " + n);
        return n;
    }

    private <T> T failWith(int offset, String message) {
        if (error == null)
            error = String.format("Error at position %d: %s", offset, message);
        return null;
    }

    private int offset() {
        return pos;
    }

    private Re rep() {
        var a = seq();
        if (a == null) return null;
        if (done()) return a;
        switch (ch) {
            case '?': next(); return a.opt();
            case '*': next(); return a.many();
            case '+': next(); return a.some();
            case '{': next();
                var start = offset();
                var n = parseInt();
                if (n < 0) return null;
                Re rep;
                if (sat(c -> c == ',')) {
                    var m = parseInt();
                    if (m < 0) return null;
                    if (n > m) return failWith(start, "Range lower bound is bigger then upper bound");
                    rep = a.range(n, m);
                } else {
                    rep = a.repeat(n);
                }
                if (!consume('}'))
                    return failWith(offset(), "Expected closing range ('}')");
                return rep;
            default:
                return a;
        }
    }

    private CharSet charSet() {
        var ivs = new ArrayList<Interval<Void>>();
        if (eof()) return null;
        var compl = sat(c -> c == '^');
        if (sat(c -> c == ']')) ivs.add(Interval.of(']'));
        if (eof()) return null;

        var prev = -1;
        int rangeState = 0;

        while (!sat(c -> c == ']')) {
            if (eof()) {
                failWith(offset(), "expected closing ']' in char class");
                return null;
            }
            switch (rangeState) {
                case 0:
                    prev = ch;
                    rangeState = 1;
                    break;
                case 1:
                    if (ch == '-') {
                        rangeState = 2;
                    } else {
                        ivs.add(Interval.of(prev));
                        prev = ch;
                        rangeState = 1;
                    }
                    break;
                default: // 2
                    if (ch == '-') {
                        return failWith(offset(), "Repeating a hypen in ranges is not allowed");
                    } else {
                        if (prev > ch)
                            return failWith(offset(), "range where lower > upper is forbidden");
                        // FIXME: overflow checkking
                        ivs.add(Interval.of(prev, ch + 1));
                        rangeState = 0;
                    }
                    break;
            }
            next();
        }

        switch (rangeState) {
            case 0:
                break;
            case 1:
                ivs.add(Interval.of(prev));
                break;
            default: // 2
                return failWith(offset(), "trailing hypen in range is forbidden");
        }

        var cs = CharSet.from(IntervalSet.buildDestructive(ivs, null), compl);
        if (CHECKING) System.err.println("Parsed cs: " + cs);
        return cs;
    }

    private String metaChars() {
        return extended ? EXT_META_CHARS : META_CHARS;
    }

    private int unescape(int ch) {
        switch (ch) {
            case '\\': return ch;
            case '\n': return '\n';
            case '\t': return '\t';
            case '\r': return '\r';
            default:
                failWith(offset(), "invalid escape sequence: '" + Character.toString(ch) + "'");
                return -1;
        }
    }

    private Re lit() {
        StringBuilder sb = null;
        while (!eof() && metaChars().indexOf(ch) < 0) {
            int c = ch;
            if (ch == '\\') {
                if (!any("escaped symbol"))
                    return null;
                c = unescape(ch);
                if (c < 0) return null;
            } else
            if (sb == null)
                sb = new StringBuilder();
            sb.appendCodePoint(c);
            next();
        }
        var ret = Re.Lit.from(sb != null ? sb.toString() : "");
        if (CHECKING) System.err.println("PARSED LIT: " + ret);
        return ret;
    }

    private Re seq() {
        if (done()) return null;
        var a = term();
        if (a == null) return a;
        while (!eof()) {
            if (!(metaChars().indexOf(ch) < 0 || ch == '.' || ch == '[' || ch == '('))
                break;
            var b = term();
            if (b == null) return null;
            a = a.seq(b);
        }
        return a;
    }

    private Re term() {
        if (done()) return null;
        switch (ch) {
            case '.':
                next();
                return CharSet.ANY;
            case '(':
                next();
                var a = alt();
                if (a == null) return null;
                if (!consume(')'))
                    return failWith(offset(), "Missing closing ')'");
                return a;
            case '[':
                next();
                return charSet();
            default:
                return lit();
        }
    }
}
