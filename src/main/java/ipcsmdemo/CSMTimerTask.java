package ipcsmdemo;
 
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import javax.ejb.*;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.jms.pool.PooledConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
/** 
 * A timer task that checks the transaction table for timed out transactions.
 * Timeout messages are sent to the beneficiary response MDB to be forwarded to the originator. 
 * The MDB will avoid duplicate responses being forwarded to the originator.  
 * 
 */ 
@Singleton
public class CSMTimerTask {
	private static final Logger logger = LoggerFactory.getLogger(CSMTimerTask.class);
	
    static final long TENSECS=10000;
    static final long ONESEC=1000;
    static final long TWOSEC=2000;
    static final long DEADTIME=ONESEC*60;
    static final int MAXMESSAGES=200;
    
	@EJB
    CSMDBSessionBean dbSessionBean;

	SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");	// 2018-12-28T15:25:40.264
    
	private String responseTemplate="pacs.002.xml";
	
    private QueueConnectionFactory qcf;	// To get outgoing connections for sending messages

	public CSMTimerTask() {
		
		// Check to see if a private pool is defined (if this is not the case 'null' we will look for the named Wildfly managed pool)
    	try { 
            InitialContext iniCtx = new InitialContext();

	    	qcf=PrivatePool.createPrivatePool(iniCtx,logger);	// Null if not configured in standalone.xml
    		if (qcf==null) {
            	// Use the application server ActiveMQ connection pool
				qcf = ManagedPool.getPool(iniCtx,logger);
    		}
		} catch (NamingException e) {
            logger.error("Activemq factory {}",e.getMessage());
            throw new EJBException(e);
		}
	}
	
    @Schedule(hour="*", minute="*", second="*/2", persistent=false)
    public void logCountsJob() throws IOException, NamingException {
        // Do timeout check here which should run every second.
    	
        Context ic=new InitialContext();

        long startTime=System.currentTimeMillis();
        
        // Select open transactions older than timeout period
        Timer[] timedOut=dbSessionBean.getTimers(MAXMESSAGES);	// Locked for update
		if(timedOut!=null&&timedOut.length==MAXMESSAGES)
			logger.info("Max timeout transactions found {}",timedOut.length);

		// For each timeout message (if any) send a timeout message to the response bean
        int sent=0;
        if (timedOut!=null && timedOut.length>0) try {
        	
	        QueueConnection conn = qcf.createQueueConnection();
	        conn.start();
	        MessageUtils mu=new MessageUtils();

	        long timeNow=0;
	        
	        for (int i=0;i<timedOut.length&&timeNow<(startTime+TWOSEC-100);i++) {

        		// Check the time for a dead timer / tx and clear the cache - should never happen...
        		Date atime=timedOut[i].adatetime;
        		if (atime.getTime()+DEADTIME<startTime) {
        				logger.warn("Dead timer "+atime+", now "+new Date(startTime));
	        			dbSessionBean.deleteTimer(timedOut[i].txid);
	        	} else {
	        		Document orgMsg=XMLutils.bytesToDoc(timedOut[i].msg.getBytes("UTF-8"));
                	// Reject to originator
	                String reason="AB06";
                	Document rejectdoc=mu.CreateReject(orgMsg, reason);
        			
            		QueueSession session = conn.createQueueSession(false,
                            QueueSession.AUTO_ACKNOWLEDGE);
            		try {
            			// Queue responseDest=(Queue)ic.lookup("CSMOriginatorResponseQueue"+debtorBIC); // *
            			Queue responseDest=(Queue)ic.lookup("CSMBeneficiaryResponseQueue"+timedOut[i].debtorBIC); 
                    	QueueSender sender = session.createSender(responseDest);
                    	String respText=XMLutils.documentToString(rejectdoc);
                    	TextMessage sendmsg = session.createTextMessage(respText);
                    	sender.setPriority(7);
            			sender.send(sendmsg);
            			sender.close();
            			sent++;
            			session.close();
            		} catch (NamingException e) {
                		logger.info("Queue not found "+e);
                		reason="FF01";
            		}
        	    	dbSessionBean.deleteTimer(timedOut[i].txid);       			
                    try {
                    	dateTimeFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
                    	Date origTime = dateTimeFormat.parse(XMLutils.getElementValue(rejectdoc,"AccptncDtTm"));
                    	logger.trace("Timers 'acceptance' {} 'now' {}",origTime,new Date());
                    } catch (Exception e) {
                    	logger.warn("Bad date");
                    }
	        	}
	        	timeNow=System.currentTimeMillis();
        	}
			conn.close();	// Return connection to the pool
	        logger.info("Timeout count {}, timeouts sent {}, time {} ms",timedOut.length,sent,timeNow-startTime);
        } catch (JMSException e) {
    		throw new EJBException(e);
        }
    	dbSessionBean.saveLiquidityStatus();
    }
}
