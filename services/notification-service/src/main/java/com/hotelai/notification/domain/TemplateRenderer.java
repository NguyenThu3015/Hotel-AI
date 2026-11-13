package com.hotelai.notification.domain;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class TemplateRenderer {
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");

    public String render(String template, Map<String, Object> variables) {
        if (template == null) return null;
        Map<String, Object> safeVariables = variables == null ? Map.of() : variables;
        Matcher matcher = VARIABLE.matcher(template);
        StringBuilder output = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!safeVariables.containsKey(key)) {
                throw new IllegalArgumentException("Missing template variable: " + key);
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(String.valueOf(safeVariables.get(key))));
        }
        matcher.appendTail(output);
        return output.toString();
    }
}
