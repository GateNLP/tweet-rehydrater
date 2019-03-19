# tweet-rehydrater

This is a very simple command line tool to take standoff annotations that relate to tweets from Twitter, fetch the corresponding data including the tweet text from the Twitter API, and merge the two.

## Download

The latest release of the rehydrater tool can be download [from GitHub][1]

## Usage

    java -jar tweet-rehydrater.jar <credentials> <input> <output>

  - `credentials` - a Java properties file containing Twitter credentials (see below)
  - `input` - file containing "dehydrated" Tweets, i.e. a stream of JSON objects with properties "id" (long integer Tweet ID) and "entities" (standoff annotations).  Use "-" (a single hyphen) to read from standard input.
  - `output` - file into which rehydrated Tweets should be written.  Use "-" (a single hyphen) to write to standard output

API access uses the ["application only"][2] authentication scheme.  You must create a Twitter application, and provide its consumer key and secret in a properties file:

    consumerKey=...
    consumerSecret=...

You can create an application at https://apps.twitter.com

By default this tool fetches "extended" format tweets, if you want to fetch them in "compatibility" mode instead, add

    compatibilityMode=true

to your credentials properties file.

Note that the API used is rate-limited - do not attempt to rehydrate more than 6000 Tweets in any 15 minute window.

## Data format

The input file is expected to contain a stream of JSON objects concatenated together, one per tweet, which are essentially a subset of the standard Twitter JSON format that will be merged into the full JSON retrieved from Twitter.  Each object must have at least the following property:

 - `id` - a long integer giving the ID of the tweet

In addition, the object may have a property `entities` giving the standoff annotations in the top-level `full_text` (or `text` in compatibility mode), represented in the normal Twitter format as used for things like hashtags and URLs in the Twitter APIs.  If the tweet in question is a retweet, the input object may have `retweeted_status` which in turn contains an `entities` property, giving annotations in the `full_text` of the original retweeted status, and if the tweet is a quote tweet the input object may have `quoted_status` which in turn contains `entities` in the same way.  Each set of entities will be merged into the corresponding set in the JSON retrieved from Twitter.

Each `entities` property is an object where each property name is an annotation type and the corresponding value is an array of objects representing the annotations of that type.  Each annotation object has a property `indices` giving the annotation offsets, and other properties are treated as annotation features, for example:

    {
      "id":12345678,
      "entities":{
        "Person":[
          {
            "indices":[1,5],
            "gender":"male"
          }
        ],
        "Location":[
          {
            "indices":[17,23],
            "locType":"city"
          },
          {
            "indices":[34,49],
            "locType":"country"
          }
        ]
      }
    }

(This example has been pretty-printed for clarity, but this is not required for the rehydrater tool).

## Deleted Tweets

It is possible for a user to delete any of their tweets at any time after posting them, and deleted tweets will not be returned by the Twitter APIs.  Therefore it is possible that there may be annotations in the input file for which the original tweet is no longer available, and such tweets will be omitted from the output.


 [1]: https://github.com/GateNLP/tweet-rehydrater/releases/latest
 [2]: https://dev.twitter.com/oauth/application-only
