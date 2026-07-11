package io.github.vfedoriv.graphrag.service;

import java.util.ArrayList;
import java.util.List;

final class CypherLimitScanner {

    private CypherLimitScanner() {
    }

    static List<LimitClause> topLevelLimits(String cypher) {
        List<LimitClause> limits = new ArrayList<>();
        int index = 0;
        int depth = 0;
        while (index < cypher.length()) {
            char current = cypher.charAt(index);
            if (current == '\'' || current == '"' || current == '`') {
                index = skipQuoted(cypher, index, current);
                continue;
            }
            if (current == '/' && index + 1 < cypher.length() && cypher.charAt(index + 1) == '/') {
                index = skipLineComment(cypher, index + 2);
                continue;
            }
            if (current == '/' && index + 1 < cypher.length() && cypher.charAt(index + 1) == '*') {
                index = skipBlockComment(cypher, index + 2);
                continue;
            }
            if (current == '(' || current == '[' || current == '{') {
                depth++;
                index++;
                continue;
            }
            if (current == ')' || current == ']' || current == '}') {
                depth = Math.max(0, depth - 1);
                index++;
                continue;
            }
            if (current == '$') {
                index++;
                while (index < cypher.length() && isWordPart(cypher.charAt(index))) {
                    index++;
                }
                continue;
            }
            if (depth == 0 && isWordStart(current)) {
                int wordEnd = index + 1;
                while (wordEnd < cypher.length() && isWordPart(cypher.charAt(wordEnd))) {
                    wordEnd++;
                }
                if (cypher.regionMatches(true, index, "LIMIT", 0, "LIMIT".length()) && wordEnd - index == 5) {
                    limits.add(parseLimit(cypher, index, wordEnd));
                }
                index = wordEnd;
                continue;
            }
            index++;
        }
        return limits;
    }

    private static LimitClause parseLimit(String cypher, int start, int expressionStart) {
        int index = skipTrivia(cypher, expressionStart);
        if (index < cypher.length() && cypher.charAt(index) == '$') {
            int parameterStart = ++index;
            while (index < cypher.length() && isWordPart(cypher.charAt(index))) {
                index++;
            }
            String parameter = cypher.substring(parameterStart, index);
            return parameter.isBlank() ? LimitClause.unsupported(start) : LimitClause.parameter(start, parameter);
        }
        int numberStart = index;
        while (index < cypher.length() && Character.isDigit(cypher.charAt(index))) {
            index++;
        }
        if (numberStart != index) {
            try {
                return LimitClause.literal(start, Long.parseLong(cypher.substring(numberStart, index)));
            } catch (NumberFormatException ex) {
                return LimitClause.unsupported(start);
            }
        }
        return LimitClause.unsupported(start);
    }

    private static int skipTrivia(String cypher, int index) {
        int current = index;
        while (current < cypher.length()) {
            if (Character.isWhitespace(cypher.charAt(current))) {
                current++;
            } else if (current + 1 < cypher.length() && cypher.charAt(current) == '/' && cypher.charAt(current + 1) == '/') {
                current = skipLineComment(cypher, current + 2);
            } else if (current + 1 < cypher.length() && cypher.charAt(current) == '/' && cypher.charAt(current + 1) == '*') {
                current = skipBlockComment(cypher, current + 2);
            } else {
                return current;
            }
        }
        return current;
    }

    private static int skipQuoted(String cypher, int index, char quote) {
        int current = index + 1;
        while (current < cypher.length()) {
            if (cypher.charAt(current) == quote) {
                if (current + 1 < cypher.length() && cypher.charAt(current + 1) == quote) {
                    current += 2;
                } else {
                    return current + 1;
                }
            } else if (cypher.charAt(current) == '\\' && quote != '`' && current + 1 < cypher.length()) {
                current += 2;
            } else {
                current++;
            }
        }
        return current;
    }

    private static int skipLineComment(String cypher, int index) {
        int newline = cypher.indexOf('\n', index);
        return newline == -1 ? cypher.length() : newline + 1;
    }

    private static int skipBlockComment(String cypher, int index) {
        int end = cypher.indexOf("*/", index);
        return end == -1 ? cypher.length() : end + 2;
    }

    private static boolean isWordStart(char value) {
        return Character.isLetter(value) || value == '_';
    }

    private static boolean isWordPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_';
    }

    record LimitClause(int position, Long literal, String parameter, boolean supported) {

        static LimitClause literal(int position, long value) {
            return new LimitClause(position, value, null, true);
        }

        static LimitClause parameter(int position, String value) {
            return new LimitClause(position, null, value, true);
        }

        static LimitClause unsupported(int position) {
            return new LimitClause(position, null, null, false);
        }
    }
}
