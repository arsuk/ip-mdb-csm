package ipcsmdemo;
 
import java.io.IOException;

import javax.ejb.*;
import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 
 * A timer task that runs the liquidity status update.
 * 
 */ 
@Singleton
public class CSMStatusTimerTask {
	private static final Logger logger = LoggerFactory.getLogger(CSMStatusTimerTask.class);
    
	@EJB
    CSMDBSessionBean dbSessionBean;
	
    @Schedule(hour="*", minute="*", second="*/1", persistent=false)
    public void logCountsJob() throws IOException, NamingException {
    	// Update liquidity status and delete liquidity change records
    	dbSessionBean.saveLiquidityStatus();
    }
}
