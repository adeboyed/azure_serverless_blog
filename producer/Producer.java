import com.microsoft.windowsazure.Configuration;
import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.services.servicebus.ServiceBusConfiguration;
import com.microsoft.windowsazure.services.servicebus.ServiceBusContract;
import com.microsoft.windowsazure.services.servicebus.ServiceBusService;
import com.microsoft.windowsazure.services.servicebus.models.BrokeredMessage;
import com.microsoft.windowsazure.services.servicebus.models.CreateQueueResult;
import com.microsoft.windowsazure.services.servicebus.models.QueueInfo;
import twitter4j.*;
import twitter4j.auth.AccessToken;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;


/**
* This Java file is meant as a example of how to make a Java producer for the 
* Azure Service Bus. This is by no means a final piece of code, and extra 
* considerations should be made before using parts of this in production code.
*
* You'll need to set this up as a maven project before this works
*
* @author  David Adeboye
*/
public class Producer {

    private static long currentMaxId = 0;
    private static String queueName;
    private static Set<Long> seenTweets = new HashSet<Long>();
	
	private static final int SLEEP_TIME = 60000; //Time in ms
	private static final String SEARCH_TERM = "#TwitterMSPBot";

    public static void main( String args[] ){
		
		//Loading in all of the config from a properties file (not required)
        Properties prop = new Properties();
        InputStream input = null;

        try {
            input = new FileInputStream("config.properties");
            prop.load(input); //load a properties file
        }catch( Exception e ){
            e.printStackTrace();
            return; //We shouldn't continue if we don't have all the information;
        }

        queueName = prop.getProperty("queue_name");

        Configuration config =
                ServiceBusConfiguration.configureWithSASAuthentication(
                        "twitter-bot-msp",
                        "RootManageSharedAccessKey",
                        prop.getProperty("service_bus_primary_key"),
                        ".servicebus.windows.net"
                );

		//Connecting to the Twitter API
        TwitterFactory factory = new TwitterFactory();
        AccessToken accessToken = new AccessToken(prop.getProperty("twitter_access_token"), prop.getProperty("twitter_access_secret") );
        Twitter twitter = factory.getInstance();

        twitter.setOAuthConsumer( prop.getProperty("twitter_consumer_key") , prop.getProperty("twitter_consumer_secret") );
        twitter.setOAuthAccessToken(accessToken);

        ServiceBusContract service = ServiceBusService.create(config);

        Query query = new Query( SEARCH_TERM );
        QueryResult queryResult;

        try {
            //Run this indefinitely
            while(true){
                System.out.println("Started Loop");
                queryResult  = twitter.search( query );
				//This doesn't properly deal with paging, should be fixed!
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

								//We should also take the first word of the text because the markov chain 
								
								//Creating the JSON object to send
                                JSONObject jsonObject = new JSONObject();
                                jsonObject.put("author", status.getUser().getScreenName() );
                                jsonObject.put("tweet", text );
								
                                BrokeredMessage message = new BrokeredMessage( jsonObject.toString() );
                                System.out.println("Adding to the queue: " + jsonObject.toString() );
								
								//This is where we add the message to the queue.
                                service.sendQueueMessage( queueName, message );
                            }catch ( JSONException e ){
								//This shouldn't really happen
                                System.out.println("Could not add tweet to messaging queue due to JSON parsing");
								e.printStackTrace();
                            }
                        }
                    }
                }
                Thread.sleep( SLEEP_TIME ); //Sleep for a minute before checking again
            }

        } catch (ServiceException e) {
            System.out.print("ServiceException encountered: ");
            e.printStackTrace();
        } catch (InterruptedException e) {
			//Should handle these errors instead of just printing stack trace
            e.printStackTrace();
        } catch (TwitterException e) {
            e.printStackTrace();
        }

    }

}
