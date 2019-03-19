package gate.twitter;

import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.bind.DatatypeConverter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class Rehydrate {

  public static final String API_BASE = "https://api.twitter.com";

  protected static final ObjectMapper MAPPER = new ObjectMapper();

  public static void main(String... args) throws Exception {
    if(args.length < 3) {
      // TODO consider an interactive mode for people who don't/can't do
      // command lines and pipes
      System.err.println("Usage: java -jar tweet-rehydrater.jar <credentials> <input> <output>");
      System.err.println();
      System.err.println("  <credentials> - a Java properties file containing Twitter");
      System.err.println("                  credentials (see below)");
      System.err.println("  <input>  - file containing \"dehydrated\" Tweets, i.e. a stream");
      System.err.println("             of JSON objects with properties \"id\" (long integer");
      System.err.println("             Tweet ID) and \"entities\" (standoff annotations).");
      System.err.println("             Use \"-\" to read from standard input");
      System.err.println("  <output> - file into which rehydrated Tweets should be written");
      System.err.println("             Use \"-\" to write to standard output");
      System.err.println();
      System.err.println("This tool processes a stream of JSON objects representing standoff");
      System.err.println("annotations on Tweets, fetching the full JSON representation of the");
      System.err.println("Tweets from the Twitter API, merging in the entities, and writing");
      System.err.println("the resulting JSON objects to the output file.");
      System.err.println();
      System.err.println("API access uses the \"application only\" authentication scheme.");
      System.err.println("You must create a Twitter application, and provide its consumer");
      System.err.println("key and secret in a properties file:");
      System.err.println();
      System.err.println("  consumerKey=...");
      System.err.println("  consumerSecret=...");
      System.err.println();
      System.err.println("You can create an application at https://apps.twitter.com");
      System.err.println();
      System.err.println("By default this tool fetches \"extended\" format tweets, if you want");
      System.err.println("to fetch them in \"compatibility\" mode instead, add");
      System.err.println();
      System.err.println("  compatibilityMode=true");
      System.err.println();
      System.err.println("to your credentials properties file.");
      System.err.println();
      System.err.println("Note that the API used is rate-limited - do not attempt to rehydrate");
      System.err.println("more than 6000 Tweets in any 15 minute window.");
      System.exit(1);
    } else {
      // non-interactive - assume first argument is a properties file
      // with credentials, input comes on stdin and output goes to
      // stdout
      Path credentialsFile = Paths.get(args[0]);
      if(!Files.exists(credentialsFile)) {
        System.err.println("Credentials file " + args[0] + " does not exist");
        System.exit(1);
      }
      Properties credentials = new Properties();
      try(InputStream credentialsStream = Files.newInputStream(credentialsFile)) {
        credentials.load(credentialsStream);
      }
      Path inputFile = ("-".equals(args[1]) ? null : Paths.get(args[1]));
      Path outputFile = ("-".equals(args[2]) ? null : Paths.get(args[2]));
      try(InputStream inStream = (inputFile == null ? System.in : Files.newInputStream(inputFile));
              OutputStream outFileStream = (outputFile == null ? System.out : Files.newOutputStream(outputFile));
              OutputStream outStream = new BufferedOutputStream(outFileStream);
              JsonGenerator jsonG = MAPPER.getFactory().createGenerator(outStream)
                      .setRootValueSeparator(new SerializedString("\n"))) {
        process(inStream, jsonG,
                credentials.getProperty("consumerKey"),
                credentials.getProperty("consumerSecret"),
                Boolean.parseBoolean(credentials.getProperty("compatibilityMode", "false")));
      }
    }
  }

  /**
   * TODO: deal with rate limiting - currently you need to be careful
   * not to request more than 6000 Tweets at once (rate limit per 15
   * minutes is 60 requests times 100 Tweets per request)
   */
  public static void process(InputStream in, JsonGenerator out,
          String consumerKey, String secret, boolean compatMode) throws Exception {
    // obtain OAuth2 bearer token
    String bearerAuthHeader = getToken(consumerKey, secret);
    // read input tweets
    JsonParser parser = MAPPER.getFactory().createParser(in);
    Iterator<IdAndEntities> dehydratedTweets =
            MAPPER.readValues(parser, IdAndEntities.class);
    List<IdAndEntities> currentBatch = new ArrayList<>();
    int count = 0;
    while(dehydratedTweets.hasNext()) {
      currentBatch.add(dehydratedTweets.next());
      if((++count % 100) == 0) {
        processBatch(currentBatch, out, bearerAuthHeader, compatMode);
        currentBatch.clear();
      }
    }
    // last batch, if we didn't have an exact multiple of 100
    if(!currentBatch.isEmpty()) {
      processBatch(currentBatch, out, bearerAuthHeader, compatMode);
    }
  }

  /**
   * Obtain a "bearer token" from Twitter for application-only
   * authentication using the given consumer key. This entails making a
   * simple request to the /oauth2/token API using the consumer key for
   * basic auth.
   * 
   * @param consumerKey the consumer key for the authenticating
   *          application
   * @param secret the corresponding secret
   * @return A value suitable for use as the "Authorization" HTTP header
   *         for subsequent requests.
   */
  protected static String getToken(String consumerKey, String secret)
          throws Exception {
    URL tokenServiceURL = new URL(API_BASE + "/oauth2/token");
    HttpURLConnection connection =
            (HttpURLConnection)tokenServiceURL.openConnection();
    connection.setDoOutput(true);
    connection.setRequestMethod("POST");
    connection.setRequestProperty("Content-Type",
            "application/x-www-form-urlencoded;charset=UTF-8");
    // build the auth header - first URI-encode the key and secret, and
    // join them with a colon
    String basicCredentials =
            new URI("urn", consumerKey, null).getRawSchemeSpecificPart() + ":"
                    + new URI("urn", secret, null).getRawSchemeSpecificPart();
    // now base64 encode the result
    String base64Credentials =
            DatatypeConverter.printBase64Binary(basicCredentials
                    .getBytes("UTF-8"));
    connection
            .setRequestProperty("Authorization", "Basic " + base64Credentials);

    byte[] requestBody = "grant_type=client_credentials".getBytes("UTF-8");
    connection.setFixedLengthStreamingMode(requestBody.length);
    try(OutputStream tokenRequestOut = connection.getOutputStream()) {
      tokenRequestOut.write(requestBody);
    }

    // response should be JSON:
    // {"token_type":"bearer","access_token":"..."}
    if(connection.getResponseCode() != 200) {
      throw new IllegalStateException("Received HTTP status "
              + connection.getResponseCode() + " "
              + connection.getResponseMessage()
              + " when requesting bearer token");
    }

    try(InputStream responseInput = connection.getInputStream()) {
      Map<String, String> response =
              MAPPER.readValue(responseInput,
                      new TypeReference<Map<String, String>>() {
                      });
      if("bearer".equals(response.get("token_type"))) {
        // construct auth header for use with future requests
        return "Bearer " + response.get("access_token");
      } else {
        throw new IllegalStateException("Received OAuth token of wrong type ("
                + response.get("token_type") + ")");
      }
    }
  }

  protected static void processBatch(List<IdAndEntities> currentBatch,
          JsonGenerator out, String bearerAuthHeader, boolean compatMode) throws Exception {
    // call Twitter statuses/lookup
    URL lookupUrl = new URL(API_BASE + "/1.1/statuses/lookup.json");
    HttpURLConnection connection =
            (HttpURLConnection)lookupUrl.openConnection();
    connection.setDoOutput(true);
    connection.setRequestMethod("POST");
    connection.setRequestProperty("Content-Type",
            "application/x-www-form-urlencoded;charset=UTF-8");
    connection.setRequestProperty("Authorization", bearerAuthHeader);

    try(Writer w =
            new OutputStreamWriter(connection.getOutputStream(), "UTF-8")) {
      if(!compatMode) {
        w.write("tweet_mode=extended&");
      }
      w.write("id=");
      Iterator<IdAndEntities> batchIterator = currentBatch.iterator();
      w.write(String.valueOf(batchIterator.next().id));
      while(batchIterator.hasNext()) {
        w.write(",");
        w.write(String.valueOf(batchIterator.next().id));
      }
    }

    // read response - a JSON array of Tweets, not necessarily in the
    // same order as the batch, and index them by ID
    Map<Long, JsonNode> fullTweets = new HashMap<>();
    try(InputStream responseInput = connection.getInputStream()) {
      List<JsonNode> response =
              MAPPER.readValue(responseInput,
                      new TypeReference<List<JsonNode>>() {
                      });
      for(JsonNode tweet : response) {
        fullTweets.put(tweet.get("id").asLong(), tweet);
      }
    }

    // for each input tweet, find the matching response from Twitter
    for(IdAndEntities itemToMerge : currentBatch) {
      // merge our entities into that and output the result
      JsonNode matchingTweet = fullTweets.get(itemToMerge.id);
      if(matchingTweet == null) {
        System.err.println("No Tweet found with ID " + itemToMerge.id
                + " - it may have been deleted");
      } else {
        mergeEntities(matchingTweet, itemToMerge);
        // output the result
        MAPPER.writeValue(out, matchingTweet);
      }
    }
  }

  /**
   * Merge entities from the given IdAndEntities into the given Json status
   * object, and recurse into retweeted and quoted status if these are present.
   */
  public static void mergeEntities(JsonNode status, IdAndEntities itemToMerge) {
    if(status != null && itemToMerge != null) {
      if(itemToMerge.entities != null) {
        ObjectNode entitiesNode = (ObjectNode)status.get("entities");
        if(entitiesNode == null) {
          entitiesNode = MAPPER.getNodeFactory().objectNode();
          ((ObjectNode)status).put("entities", entitiesNode);
        }
        for(Map.Entry<String, List<JsonNode>> entitiesEntry : itemToMerge.entities
                .entrySet()) {
          ArrayNode entitiesOfType =
                  (ArrayNode)entitiesNode.get(entitiesEntry.getKey());
          if(entitiesOfType == null) {
            entitiesOfType = MAPPER.getNodeFactory().arrayNode();
            entitiesNode.put(entitiesEntry.getKey(), entitiesOfType);
          }
          entitiesOfType.addAll(entitiesEntry.getValue());
        }
      }
      // process retweet, if any
      mergeEntities(status.get("retweeted_status"), itemToMerge.retweeted_status);
      // process quote, if any
      mergeEntities(status.get("quoted_status"), itemToMerge.quoted_status);
    }
  }

}
