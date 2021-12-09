package ipcsmdemo;
 
import java.io.IOException;

import javax.ejb.*;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.TextMessage;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.activemq.ScheduledMessage;
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

    private QueueConnectionFactory qcf;	// To get outgoing connections for sending messages

    public CSMStatusTimerTask()
    {
    	// Check for pooled connection factory created directly with ActiveMQ above or
    	// get a pool defined in the standalone.xml.
        try {
            // Create connection for replies and forwards
            InitialContext iniCtx = new InitialContext();

            if (qcf==null) {
            	// Use an application server defined ActiveMQ connection pool using a JNDI name from a system property
				qcf = ManagedPool.getPool(iniCtx,logger);
            }

	       	logger.info("Started");
        }
        catch (javax.naming.NameNotFoundException e) {
        	logger.error("Init Error: "+e);
        } catch (Exception e) {
            throw new EJBException("Init Exception ", e);
        }
    }
    
    @Schedule(hour="*", minute="*", second="*/1", persistent=false)
    public void logCountsJob() throws IOException, NamingException {
    	
    	// Update liquidity status       
    	dbSessionBean.saveLiquidityStatus();

    }
}
