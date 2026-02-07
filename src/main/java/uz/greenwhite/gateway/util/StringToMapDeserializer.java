package uz.greenwhite.gateway.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class StringToMapDeserializer extends JsonDeserializer<Map<String, String>> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, String> deserialize(JsonParser p, DeserializationContext context) {
        try {
            if (p.currentToken() == JsonToken.VALUE_STRING) {
                String jsonString = p.getValueAsString();
                if (jsonString == null || jsonString.isEmpty()) {
                    return null;
                }
                return objectMapper.readValue(jsonString, new TypeReference<>() {});
            } else if (p.currentToken() == JsonToken.START_OBJECT) {
                return objectMapper.readValue(p, new TypeReference<>() {});
            } else {
                log.warn("Unexpected token type for headers: {}", p.currentToken());
                return null;
            }
        } catch (Exception e) {
            log.error("Error deserializing headers map: {}", e.getMessage(), e);
            return null;
        }
    }
}