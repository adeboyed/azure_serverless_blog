var Twitter = require('twitter');
var MarkovChain = require('markovchain');

const corpus = ``;

/**
* This javascript file is meant as an example of how you can use Azure
* functions as a consumer for an Azure Service Bus.
*/

module.exports = function (context, myQueueItem) {
    var tweetLength = Math.floor((Math.random() * 5) + 5);
    const starting_word = myQueueItem.tweet;

    var client = new Twitter({
            consumer_key: '',
            consumer_secret: '',
            access_token_key: '',
            access_token_secret: ''
        });
        
        const lyricGen = quotes = new MarkovChain(corpus);
        const sentence = quotes.start( starting_word ).end( tweetLength ).process();
        const toTweet = "@" + myQueueItem.author + " " + sentence;
        context.log('Generated sentence to tweet: ' + sentence);

        client.post('statuses/update', {status: toTweet},  function(error, tweet, response) {
            if(error) throw error;
                //context.log(tweet);  // Tweet body. 
                //context.log(response);  // Raw response object. 
        });

    context.done();
};