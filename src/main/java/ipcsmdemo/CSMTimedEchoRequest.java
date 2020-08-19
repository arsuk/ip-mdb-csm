package ipcsmdemo;
 
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

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

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.jms.pool.PooledConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
/** 
 * A timer task that simulates an Instant Payments CSM echo request processing.
 * It sends and echo request to each configured client every minute.  
 * 
 */
@Singleton
public class CSMTimedEchoRequest {
	private static final Logger logger = LoggerFactory.getLogger(CSMTimedEchoRequest.class);
	
	@EJB
	CSMDBSessionBean dbSessionBean;
	
    static final long TENSECS=10000;
    static final long ONESEC=1000;

	SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");	// 2018-12-28T15:25:40.264
    
	private String defaultTemplate="Echo_Request.xml";
	
    private QueueConnectionFactory qcf;	// To get outgoing connections for sending messages

	public CSMTimedEchoRequest() {
		
    	// Check too see if there is a 'private pool' definition. Other wise look for the standalone config pool definition we should use.
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
	
    @Schedule(hour="*", minute="*/1", second="0", persistent=false)
    public void logCountsJob() throws IOException {
        // Do your job here which should run every 10 secs.

        Document doc=XMLutils.bytesToDoc(XMLutils.getTemplate(defaultTemplate));
        
        XMLutils.setElementValue(doc,"CreDtTm",dateTimeFormat.format(new Date()));
       	// Copy BIC from echo request <InstgAgt><FinInstnId><BIC>
 
        // XMLutils.setElementValue(XMLutils.getElement(doc,"InstgAgt"),"BIC",finInstBIC); 

        // XMLutils.setElementValue(doc,"OrgnlInstrId",origInstId);
        
        // Get a pooled connection
        try {
        	
        	BankStatus[] banks=dbSessionBean.getStatus("%");
        	if (banks==null) {
        		logger.info("No CSM status data");
        		return;
        	}
        	
	        QueueConnection conn = qcf.createQueueConnection();
	        conn.start();
	
	        QueueSession session = conn.createQueueSession(false,
	                    QueueSession.AUTO_ACKNOWLEDGE);
	        int sent=0;
	        for (int i=0;i<banks.length;i++) {
	        	Queue requestDest=null;
	        	String bic=banks[i].bic;
	        	String queueName="CSMEchoQueue"+bic;// BIC
	        	try {
	        		InitialContext iniCtx = new InitialContext();
	        		requestDest=(Queue)iniCtx.lookup(queueName);	
	        	} catch(NamingException  ne) {};
	        	if (requestDest==null) {
	        		logger.info("Missing queue {}",queueName);
	        	} else {
		        	QueueSender sender = session.createSender(requestDest);
		        	XMLutils.setElementValue(XMLutils.getElement(doc,"InstdAgt"), "BIC", bic);
		        	TextMessage sendmsg = session.createTextMessage(XMLutils.documentToString(doc));
		        	sender.send(sendmsg);
		            sender.close();
	        	    sent++;
	        	    logger.trace("Send echo on {} to {}",requestDest.getQueueName(),bic);
	        	}
	        }
            session.close();  	
	        conn.close();	// Return connection to the pool
            logger.trace("Sent {} echos at {}",sent,new Date());
        } catch (JMSException e) {
        	throw new EJBException(e);
        }

    }
}
