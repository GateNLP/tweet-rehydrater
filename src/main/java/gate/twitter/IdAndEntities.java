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
  
  /**
   * Entities in this status (may be null).
   */
  public Map<String, List<JsonNode>> entities;

  /**
   * Entities in the retweeted status (if this is a retweet).
   */
  public IdAndEntities retweeted_status;

  /**
   * Entities in the quoted status (if this is a quote).
   */
  public IdAndEntities quoted_status;

}
