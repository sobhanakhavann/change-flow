package com.example.changeflow.controller;

import com.example.changeflow.domain.ChangeRequestRules;
import java.util.Map;
import java.util.Set;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Parses only when invoked after the service's current visibility/permission checks. */
@Component
public class RequestPayloads {
    private final ObjectMapper mapper;
    public RequestPayloads(ObjectMapper mapper) { this.mapper = mapper; }

    public ChangeRequestRules.Content content(byte[] raw) {
        var fields = object(raw, Set.of("title", "changeDescription", "reasonForChange"));
        return new ChangeRequestRules.Content(text(fields, "title"), text(fields, "changeDescription"), text(fields, "reasonForChange"));
    }

    public String reason(byte[] raw) { return text(object(raw, Set.of("reason")), "reason"); }

    public ChangeRequestRules.Content emptyCommand(byte[] raw) { object(raw, Set.of()); return null; }

    private Map<?, ?> object(byte[] raw, Set<String> allowed) {
        try {
            if (raw == null) { throw new IllegalArgumentException("Missing object"); }
            // Decode here, after current visibility/action checks. Never replace malformed bytes.
            String decoded = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();
            Map<?, ?> result = mapper.readValue(decoded, Map.class);
            if (result == null || !allowed.containsAll(result.keySet())) { throw new IllegalArgumentException("Invalid fields"); }
            return result;
        } catch (CharacterCodingException | RuntimeException failure) { throw new IllegalArgumentException("Invalid UTF-8 JSON object"); }
    }

    private String text(Map<?, ?> fields, String key) {
        Object value = fields.get(key);
        if (value != null && !(value instanceof String)) { throw new IllegalArgumentException("Invalid field type"); }
        return (String) value;
    }
}
