package com.flamingo.ai.notebooklm.domain.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/** JPA converter for persisting {@code List<String>} as a JSON array in a TEXT column. */
@Converter
@Slf4j
public class StringListConverter implements AttributeConverter<List<String>, String> {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {};

  @Override
  public String convertToDatabaseColumn(List<String> attribute) {
    if (attribute == null || attribute.isEmpty()) {
      return null;
    }
    try {
      return MAPPER.writeValueAsString(attribute);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize string list: {}", e.getMessage());
      return null;
    }
  }

  @Override
  public List<String> convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.isBlank()) {
      return Collections.emptyList();
    }
    try {
      return MAPPER.readValue(dbData, LIST_TYPE);
    } catch (JsonProcessingException e) {
      log.error("Failed to deserialize string list: {}", e.getMessage());
      return Collections.emptyList();
    }
  }
}
