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

Note that the API used is rate-limited - do not attempt to rehydrate more than 6000 Tweets in any 15 minute window.

## Data format

The input file is expected to contain a stream of JSON objects concatenated together, one per tweet.  Each object must have at least the following two properties:

 - `id` - a long integer giving the ID of the tweet
 - `entities` - the standoff annotations, represented in the normal Twitter format as used for things like hashtags and URLs in the Twitter APIs.

The `entities` property is an object where each property name is an annotation type and the corresponding value is an array of objects representing the annotations of that type.  Each annotation object has a property `indices` giving the annotation offsets, and other properties are treated as annotation features, for example:

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
