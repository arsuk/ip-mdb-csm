package ipcsmdemo;
 
import java.io.IOException;

import javax.ejb.*;
import javax.jms.QueueConnectionFactory;
import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 
 * CSM timer task<br/>
 * A timer task that runs housekeeping activities every 1 second.
 * The only activity currently implemented if the liquidity status update.
 * @author Allan Smith
 * 
 */ 
@Singleton
public class CSMStatusTimerTask {
	private static final Logger logger = LoggerFactory.getLogger(CSMStatusTimerTask.class);
    
	@EJB
    CSMDBSessionBean dbSessionBean;

    public CSMStatusTimerTask()
    {

    	logger.info("Started");

    }
 
    static int statsCount=0;
    static int oldRecvCount=0;
    
    @Schedule(hour="*", minute="*", second="*/1", persistent=false)
    public void logCountsJob() throws IOException, NamingException {
    	
    	// Update liquidity status       
    	dbSessionBean.saveLiquidityStatus();
    	
    	// Log originator delivery stats every 10 timer increments
    	if (++statsCount>9 && oldRecvCount<CSMOriginatorBean.recvCount.get()) {
    		statsCount=0;
    		int totRecvCount=CSMOriginatorBean.recvCount.get();
    		int recvCount=totRecvCount-oldRecvCount;
    		oldRecvCount=totRecvCount;
    		long minDeliveryTime=CSMOriginatorBean.minDeliveryTime;
    		long maxDeliveryTime=CSMOriginatorBean.maxDeliveryTime;
    		long totDeliveryTime=CSMOriginatorBean.totDeliveryTime.get();
    		logger.info("Receive count "+recvCount+" delivery time min "+minDeliveryTime+" max "+
    				maxDeliveryTime+" avg "+totDeliveryTime/recvCount);
    		CSMOriginatorBean.minDeliveryTime=0L;
    		CSMOriginatorBean.maxDeliveryTime=0L;
    		CSMOriginatorBean.totDeliveryTime.set(0);
    	}  	

    }
}
