package gate.twitter;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Container class for a single dehydrated Tweet
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class IdAndEntities {

  public long id;
  
  public Map<String, List<JsonNode>> entities;

}
