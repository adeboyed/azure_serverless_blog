# Serverless + Service Bus Queue = Magic

https://blogs.msdn.microsoft.com/uk_faculty_connection/2017/12/14/serverless-service-bus-queue-magic/

## About Me

Hello! My name is David Adeboye and I am currently studying Computer Science at University of Cambridge. My main interest is in site reliability and concurrent and distributed systems, and in particular, I like designing system architectures in interesting ways. LinkedIn profile: https://www.linkedin.com/in/david-adeboye/ 

## Introduction

One of the ideas in distributed systems is the producer-consumer model, in this blog we'll look into using Azure functions to process items on an Azure Service Bus queue. The advantages of this is that it means you are not having to bear the costs of a running a virtual machine all the time, especially if your workload can be quite infrequent. Another use is when your workload is highly parallelisable, meaning you can run several instances of your code on different data.

We will partially follow the step by step tutorial provided and show how to an example of functions by creating a twitter bot using Markov models. All the source for this blog can be found on my github. (Link found at the bottom)

## Prerequisite

I do use both Java in and Javascript in this project, this is both to demonstrate how the Service Bus queue supports multiple different languages and because Java is still in preview for Azure functions. The code is well-commented and should be easy to follow even if you've never used either language before.

Theory about Hidden Markov Models would be beneficial but is not required, for brevity I use a library to generate the words, however better results may be achieved by using a custom solution.

You will require an Azure account, sign up here for free: http://portal.azure.com/

You will also require Twitter API credentials, obtainable from here: https://developer.twitter.com/en/docs/basics/authentication/guides/access-tokens Please generate an access token + secret for yourself.

## End Product

You should build a bot that will wait for people to tweet on a particular hashtag with a word, and then tweet back at them with song lyrics generated using a hidden markov model and a starting word.

## Implementation

Since we are using the Service Bus queue, we will require 2 things, producer(s) and consumer(s). 

### Producer

This was implemented using Java, for now I ran it on my local computer, but it could easily run on an Azure Virtual Machine.

The first step is to set it up as a maven project, this is so that we can include the required dependencies easily, essentially we need the Azure service bus library and I'm using twitter4j to communicate with the Twitter API.

```
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>TwitterMSPBot</groupId>
    <artifactId>twitter-msp-bot</artifactId>
    <version>1.0-SNAPSHOT</version>

    <dependencies>
        <dependency>
            <groupId>com.microsoft.azure</groupId>
            <artifactId>azure-servicebus</artifactId>
            <version>0.9.8</version>
        </dependency>
        <dependency>
            <groupId>org.twitter4j</groupId>
            <artifactId>twitter4j-core</artifactId>
            <version>[4.0,)</version>
        </dependency>
    </dependencies>

</project>
```

Next we'll need to create a service bus, head to portal.azure.com, login then click `New` and finally type `Service Bus`.![](photos/service_bus_search.png)

Click `Create` and fill in the information, **taking note of your namespace.** for the rest of this tutorial we shall take the namespace to be *twitter-msp-bus*

Once it has been deployed, head to the settings page and click on `Shared access policies > RootManageSharedAccessKey ` you'll need to **take note of the primary key shown.**

![](photos/shared_policies.png)



Finally, we need to create a queue, click `Queue` then `+ Queue` at the top. The settings don't need to be changed, but just create a queue and take note of the name.

![](photos/add_queue.png)

Next create a new class, I've named mine `Producer.java` Create a main function.

The first thing we'll need is to communicate with the Azure Service Bus. Copy and paste the code with your namespace and primary key in the relevant places:

```Java
Configuration config =
                ServiceBusConfiguration.configureWithSASAuthentication(
                        "twitter-msp-bus",
                        "RootManageSharedAccessKey",
                        "[primary key]",
                        ".servicebus.windows.net"
                );
```

Next we'll need to get the Twitter functionality, make sure to enter your own twitter credentials.

```JAva
TwitterFactory factory = new TwitterFactory();
AccessToken accessToken = new AccessToken( [twitter_access_token], [twitter_access_secret] );
Twitter twitter = factory.getInstance();

twitter.setOAuthConsumer( [twitter_consumer_key] ) , [twitter_consumer_secret] );
twitter.setOAuthAccessToken(accessToken);
```

Next we shall initialise the service bus connection and create a query to search on Twitter, in this example we are searching for the hashtag **#TwitterMSPBot**

```
ServiceBusContract service = ServiceBusService.create(config);

Query query = new Query("#TwitterMSPBot");
QueryResult queryResult;
```

The next part will need to be srrounded in a try and catch because it can throw errors.

The part below will indefinetly look on twitter for the latest tweets with the hashtag and add them to the service bus message queue in the JSON format. It will also remove any unnecessary details such as other user's usernames and any hashtags, including the one we are searching for.

```Java
while(true){
	System.out.println("Started Loop");
	queryResult  = twitter.search( query );
	List<Status> statuses = queryResult.getTweets();
  	if ( statuses.size() > 0 ){
    	for ( Status status : statuses ){
      		if ( !seenTweets.contains( status.getId() ) ){
            	seenTweets.add( status.getId() );
              	System.out.println("Found Tweet!");
              	//Put this on the queue
              	try {
                	String text = status.getText().trim();

                  	//Remove any @s
                  	text = text.replaceAll("@[a-z|1-9|A-Z]+", "").trim();

                  	//Remove any hashtags
                  	text = text.replaceAll("#[a-z|A-Z|1-9]+", "").trim();
                  	text = text.toLowerCase();

                  	JSONObject jsonObject = new JSONObject();
                  	jsonObject.put("author", status.getUser().getScreenName() );
                  	jsonObject.put("tweet", text );
                  	BrokeredMessage message = new BrokeredMessage( jsonObject.toString() );
                  	System.out.println("Adding to the queue: " + jsonObject.toString() );
                  	service.sendQueueMessage( queueName, message );
              	} catch ( JSONException e ){
                  System.out.println("Could not add tweet to messaging queue due to JSON parsing");
        		}
			}
        }
  }
  Thread.sleep(60000); //Sleep for a minute before checking again
```

And then we are done!

### Consumer

This was implemented using Javascript, with a bit of nodejs.

Returning to the Azure portal as we will be creating the serverless functions, click `New` and type `Function App`

![](photos/function.png)

Create an app with Windows as OS, in this case we shall name *twitter-bot-msp* and I will use it this for the rest of the walkthrough.

Once it has been deployed, we will need to create the function, you want a 'Queue Trigger' function.

![](photos/queue_trigger.png)

Select the language as Javascript, and name your function, I've named it 'queue-trigger' but you can ignore everything else, we'll connect it to the service bus later.

####  Creating the function

1. Navigate to `https://twitter-bot-msp.scm.azurewebsites.net/`  and click `Debug Console > CMD`

2. Type in `cd site\wwwroot` to get to the site's home directory then type `npm init` to start an npm package. Enter data for the relevant fields. Here is mine below:

   ![](photos/npm_init.png)

3. Type `npm install markovchain` to install the hmm module, then type `npm install twitter` to install the twitter module

4. Return back to the Azure portal and click on the file named 'function.json', you want to make sure that the type, the queueName, and the connection are changed to the correct parameters as shown below

   ```JSON
   {
     "bindings": [
       {
         "name": "myQueueItem",
         "type": "serviceBusTrigger",
         "direction": "in",
         "queueName": "twitter-queue",
         "connection": "twitter-bot-msp_RootManageSharedAccessKey_SERVICEBUS",
         "accessRights": "Manage"
       }
     ],
     "disabled": false
   }
   ```

   ​

5. Return back to index.js and copy in the following code, you'll need to enter your Twitter credientials again below in the correct position. In the corpus you'll need to paste in text for your markov model to train from. In my case I entered lyrics from one of most-listened artists this year, according to Spotify. (Omittted for copyright reasons). I've explained what each part does below:

   ```javascript
   var Twitter = require('twitter');
   var MarkovChain = require('markovchain');

   const corpus = ``;

   module.exports = function (context, myQueueItem) {
   	var tweetLength = Math.floor((Math.random() * 5) + 5);
       const starting_word = myQueueItem.tweet;

       var client = new Twitter({
       	consumer_key: '[twitter_access_consumer_key]',
         	consumer_secret: '[twitter_consumer_secret]',
         	access_token_key: '[twitter_access_token]',
         	access_token_secret: '[twitter_access_secret]'
       });
     
     	const lyricGen = quotes = new MarkovChain(corpus);
     	const sentence = quotes.start( starting_word ).end( tweetLength ).process();
     	const toTweet = "@" + myQueueItem.author + " " + sentence;
     	context.log('Generated sentence to tweet: ' + sentence);

     	client.post('statuses/update', {status: toTweet},function(error, tweet, response) {
       	if(error) throw error;
         		//context.log(tweet);  // Tweet body. 
               //context.log(response);  // Raw response object. 
       });
       context.done();
   };
   ```




```javascript
var Twitter = require('twitter');
var MarkovChain = require('markovchain');

const corpus = ``;
```

This piece of code imports both the Twitter and Markov chain library, for brevity I am using a library instead of implementing my own. The corpus variable should be where you paste in some text for your Markov model, I used about 1000 lines of lyrics. Basically the more the better.

```javascript
var tweetLength = Math.floor((Math.random() * 5) + 5);
const starting_word = myQueueItem.tweet;
```

To make things more interesting we want to vary the length of the tweets, tweetLength should be a random number between 5 and 10, denoting the number of words we are going to generate. The starting_word should be taken from the tweet as the start of the markov model.

```javascript
var client = new Twitter({
    consumer_key: '[twitter_access_consumer_key]',
    consumer_secret: '[twitter_consumer_secret]',
    access_token_key: '[twitter_access_token]',
    access_token_secret: '[twitter_access_secret]'
});
```

This just creates and authenicates the Twitter object we are going to use for tweeting

```javascript
const lyricGen = quotes = new MarkovChain(corpus);
const sentence = quotes.start( starting_word ).end( tweetLength ).process();
const toTweet = "@" + myQueueItem.author + " " + sentence;
context.log('Generated sentence to tweet: ' + sentence);
```

Firstly we generate our markov model and store it in an object named `lyricGen`, then we use this to generate a new lyric using the starting word we got from the original tweet. Then finally we draft a tweet to send, and we log the sentence we generate. In a future iteration we should train once and just store the probabilities in a file to be loaded up everytime this function runs.

```javascript
client.post('statuses/update', {status: toTweet},function(error, tweet, response) {
    if(error) throw error;
    //context.log(tweet);  // Tweet body. 
    //context.log(response);  // Raw response object. 
});
context.done();
```

This piece of code will tweet the sentence from your account. In testing you should uncomment those lines in case there are any errors, especially the raw response object. Context.done tells the function that we have completeted execution and we can be terminated, hopefully that tweet posted!

**And then we are done, we have succesfully created a twitter bot using Azure Service bus and serverless.**

### In Action 

#### Producer running:

![](photos/run_producer.png)

#### Consumer runs:

![](photos/run_consumer.png)

#### Example tweet produced:

![](photos/run_tweet.png)

### Closing

There are several improvements that can be made to this system, however the general idea was demonstrated. This could be used in applications such as batch processing where you may not particularly want a dedicated virtual machine running all the time, but you want it there when you need them. 

Another use case could be the ability to test your application as part of your deployment, instead of having a dedicated machine for testing, or running it locally, you could have an Azure function running to test your application when you need it!

Extra points for guessing the artist!


## Resources for reference and further reading

1. Source code: https://github.com/adeboyed/azure_serverless_blog
2. Azure Service bus: https://azure.microsoft.com/en-gb/services/service-bus/
3. Introducing Azure functions: https://azure.microsoft.com/en-gb/blog/introducing-azure-functions/
4. npm reference for the markov models: https://www.npmjs.com/package/markov-chains-text
5. npm reference for the twitter api: https://www.npmjs.com/package/twitter
6. java reference for the twitter api: http://twitter4j.org/en/

