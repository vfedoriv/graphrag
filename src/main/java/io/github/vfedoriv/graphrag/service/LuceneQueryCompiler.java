package io.github.vfedoriv.graphrag.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LuceneQueryCompiler {

    static final int MAX_TERMS = 16;

    public String compile(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Lexical query must not be blank");
        }
        String normalized = input.strip().replaceAll("\\s+", " ");
        List<String> escapedTerms = new ArrayList<>();
        for (String term : normalized.split(" ")) {
            if (!term.isBlank()) {
                escapedTerms.add(escape(term));
            }
            if (escapedTerms.size() == MAX_TERMS) {
                break;
            }
        }
        String escapedPhrase = String.join(" ", escapedTerms);
        String termVariant = String.join(" AND ", escapedTerms);
        if (escapedTerms.size() == 1) {
            return escapedTerms.getFirst();
        }
        return "(\"" + escapedPhrase + "\")^3 OR (" + termVariant + ")";
    }

    String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() * 2);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if ("+-!(){}[]^\"~*?:\\/".indexOf(character) >= 0) {
                escaped.append('\\');
            }
            if (character == '&' && index + 1 < value.length() && value.charAt(index + 1) == '&') {
                escaped.append("\\&\\&");
                index++;
            } else if (character == '|' && index + 1 < value.length() && value.charAt(index + 1) == '|') {
                escaped.append("\\|\\|");
                index++;
            } else {
                escaped.append(character);
            }
        }
        return escaped.toString();
    }
}
