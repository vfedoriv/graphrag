package io.github.vfedoriv.graphrag.search.query.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CypherParsingSupport {

    private static final Pattern NODE_PATTERN = Pattern.compile("\\(([^\\[\\]]*?)\\)");
    private static final Pattern RELATIONSHIP_PATTERN = Pattern.compile("\\[([^\\]]*?)\\]");
    private static final Pattern LABEL_PATTERN = Pattern.compile(":[`]?([A-Za-z_][A-Za-z0-9_]*)[`]?");
    private static final Pattern PROPERTY_PATTERN = Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]*\\.([A-Za-z_][A-Za-z0-9_]*)\\b");

    private CypherParsingSupport() {
    }

    public static List<String> extractNodeLabels(String cypher) {
        Matcher matcher = NODE_PATTERN.matcher(cypher);
        List<String> labels = new ArrayList<>();
        while (matcher.find()) {
            String nodePattern = beforePropertyMap(matcher.group(1));
            labels.addAll(matchCaptures(LABEL_PATTERN, nodePattern));
        }
        return labels;
    }

    public static List<String> extractRelationshipTypes(String cypher) {
        Matcher matcher = RELATIONSHIP_PATTERN.matcher(cypher);
        List<String> relationshipTypes = new ArrayList<>();
        while (matcher.find()) {
            String relationshipPattern = beforePropertyMap(matcher.group(1));
            int typePrefixIndex = relationshipPattern.indexOf(':');
            if (typePrefixIndex < 0) {
                continue;
            }
            String typeExpression = relationshipPattern.substring(typePrefixIndex + 1).trim();
            int typeExpressionEnd = findTypeExpressionEnd(typeExpression);
            String typeSegment = typeExpression.substring(0, typeExpressionEnd);
            for (String rawType : typeSegment.split("\\|")) {
                String relationshipType = trimBackticks(rawType.trim());
                if (!relationshipType.isBlank()) {
                    relationshipTypes.add(relationshipType);
                }
            }
        }
        return relationshipTypes;
    }

    public static List<String> extractPropertyReferences(String cypher) {
        return matchCaptures(PROPERTY_PATTERN, cypher);
    }

    private static String beforePropertyMap(String patternContent) {
        int propertyMapIndex = patternContent.indexOf('{');
        if (propertyMapIndex < 0) {
            return patternContent;
        }
        return patternContent.substring(0, propertyMapIndex);
    }

    private static int findTypeExpressionEnd(String typeExpression) {
        for (int i = 0; i < typeExpression.length(); i++) {
            char ch = typeExpression.charAt(i);
            if (Character.isWhitespace(ch) || ch == '*') {
                return i;
            }
        }
        return typeExpression.length();
    }

    private static String trimBackticks(String value) {
        if (value.length() >= 2 && value.startsWith("`") && value.endsWith("`")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static List<String> matchCaptures(Pattern pattern, String cypher) {
        Matcher matcher = pattern.matcher(cypher);
        List<String> values = new ArrayList<>();
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }
}
