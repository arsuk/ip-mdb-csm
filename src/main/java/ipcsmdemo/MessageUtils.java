package ipcsmdemo;

import java.text.SimpleDateFormat;
import java.util.Date;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.ejb.EJB;
import javax.jms.Queue;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ScheduledMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** 
 * Common message handling utilities
 *  
 * @author Allan Smith
 * 
 * NOTE: extra exception handling has been added for ActiveMQ Artemis (RedHat AMQ7) which is not necessary with ActimeMQ (RedHat AMQ6).
 * This needs cleaning up in the future - it scans the exception text as the cause is a sub-exception. Must be a better way.
 */
public class MessageUtils {

	static final long SEVENSECS=7000;

	private static final Logger logger = LoggerFactory.getLogger(MessageUtils.class);

	static SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");	// 2018-12-28T15:25:40.264
	private String responseTemplate="pacs.002.xml";
	private byte[] responseText;
	
	@EJB
	CSMDBSessionBean dbSessionBean;
	
	MessageUtils () {
        // Get XML template for creating messages from a file - real application code would have a better way of doing this
        responseText=XMLutils.getTemplate(responseTemplate);
	}
	
    Queue lookupQueue (Context ic, String name) {
    	Queue queue=null;
    	try {
    		queue = (Queue)ic.lookup(name);
    	} catch (NamingException e) {
			logger.trace("Lookup error "+e);
		}
    	return queue;
    }
    
    static BankStatus[] bankStatus=null;
    
    String lookupBankName (String bic) {
    	if (bankStatus==null)
    		bankStatus=dbSessionBean.getStatus("%");
    	for (int i=0;i<bankStatus.length;i++) {
    		if (bic.equals(bankStatus[i].bic)) {
    			return bankStatus[i].name;
    		}
    	}
    	return null;
    }
	
	Document createReject(Document msgdoc, String reason) {
		
		if (responseText==null) return null;

        String txid=XMLutils.getElementValue(msgdoc,"TxId");
        if (txid==null)	// TxId not set with pain.002 so try OrgnlTxId 
        	txid=XMLutils.getElementValue(msgdoc,"OrgnlTxId");
        String debtorBIC=XMLutils.getElementValue(XMLutils.getElement(msgdoc,"DbtrAgt"),"BIC");
        String creditorBIC=XMLutils.getElementValue(XMLutils.getElement(msgdoc,"CdtrAgt"),"BIC");

    	Document rejectdoc=XMLutils.bytesToDoc(responseText);	// pacs.002 template
        
        XMLutils.setElementValue(rejectdoc,"MsgId",hashCode()+"-"+System.nanoTime());
        XMLutils.setElementValue(rejectdoc,"CreDtTm",dateTimeFormat.format(new Date()));
    	// Copy BIC from pacs.008 <DbtrAgt>FinInstnId><BIC> 
        // To pacs.002 <DbtrAgt>FinInstnId><BIC>
        XMLutils.setElementValue(XMLutils.getElement(rejectdoc,"DbtrAgt"),"BIC",debtorBIC);
        
        XMLutils.setElementValue(rejectdoc,"OrgnlMsgId",XMLutils.getElementValue(msgdoc,"MsgId"));
        String status="RJCT";
       	XMLutils.setElementValue(rejectdoc,"GrpSts",status);
       	Element grpInf=XMLutils.getElement(rejectdoc,"OrgnlGrpInfAndSts");
       	Element cdNode = rejectdoc.createElement("Cd");
       	cdNode.setTextContent(reason);
       	Element rsnNode = rejectdoc.createElement("Rsn");
       	rsnNode.appendChild(cdNode);
       	Element rsnInfNode = rejectdoc.createElement("StsRsnInf");
       	rsnInfNode.appendChild(rsnNode);
        grpInf.appendChild(rsnInfNode);
        XMLutils.setElementValue(rejectdoc,"OrgnlInstrId",XMLutils.getElementValue(msgdoc,"InstrId"));
        XMLutils.setElementValue(rejectdoc,"OrgnlEndToEndId",XMLutils.getElementValue(msgdoc,"EndToEndId"));
        XMLutils.setElementValue(rejectdoc,"OrgnlTxId",txid);
        XMLutils.copyElementValues(msgdoc,rejectdoc,"AccptncDtTm");
        XMLutils.setElementValue(XMLutils.getElement(rejectdoc,"InstgAgt"),"BIC",creditorBIC);
        
        return rejectdoc;		
	}

	static String sendMessage (String messageStr, String destinationName) throws NamingException {
		return sendMessage(messageStr,destinationName,null,null,null);
	}
	
	static String sendMessage (String messageStr, String destinationName, String status) throws NamingException {
		return sendMessage(messageStr,destinationName,status,null,null);
	}
	
	static String sendMessage (String messageStr, String destinationName, String status, String timerBIC, String txid) throws NamingException {
		Context ic = new InitialContext();
		ConnectionFactory cf;
		Connection connection = null;
		Session session = null;
		String msgID=null;

		InitialContext iniCtx = new InitialContext();
		// Check to see if a private pool is defined (if this is not the case 'null' we will look for the named Wildfly managed pool)
		cf=PrivatePool.createPrivatePool(iniCtx,logger);	// Null if not configured in standalone.xml
		if (cf==null)
			cf=ManagedPool.getPool(iniCtx,logger);
		
		// Retry sends ignoring 'blocking exceptions'
		boolean retry=true;
		for (int retryCount=0; retryCount<10 && retry; retryCount++)
		try {
			// Get connection
			connection = cf.createConnection();
			session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
			TextMessage message = session.createTextMessage();
			message.setText(messageStr);
			if (status!=null)
				message.setJMSType(status); // Allows for broker to use message selector
			if (timerBIC!=null) {
				// Timer message so add the timer headers expected!
				message.setStringProperty("TXID",txid);
				message.setStringProperty("DEBTORBIC",timerBIC);
				message.setLongProperty(ScheduledMessage.AMQ_SCHEDULED_DELAY, SEVENSECS);	//	Activemq
				message.setLongProperty("_AMQ_SCHED_DELIVERY", System.currentTimeMillis() + SEVENSECS);	//Artemis
			}
            Queue queue;
            try {
            	queue = (Queue)ic.lookup(destinationName);
        	} catch (NamingException e) {
                queue = session.createQueue(destinationName);            	
            }
			MessageProducer publisher = session.createProducer(queue);

			connection.start();

			publisher.send(message);
			msgID=message.getJMSMessageID();
			
			if (retryCount>0) logger.info("Retry "+retryCount+" OK");
			retry=false;
		} catch (javax.jms.JMSException e) {
			String thisClass=Thread.currentThread().getStackTrace()[1].getClassName();
			String callerClass=thisClass;
			int i=2;
			while (callerClass.equals(thisClass)) {
				callerClass=Thread.currentThread().getStackTrace()[i].getClassName();
				i++;
			}
			if (e.toString().contains("Unblocking a blocking call") || e.toString().contains("Timed out") 
					|| e.toString().contains("Could not create a session") || e.toString().contains("Session closed") ) {
				logger.warn("Retrying "+callerClass+" call after "+e);
				try {
					if (session!=null) session.close();
				} catch (Exception ee) {logger.warn("Session close "+ee);};
				try {
					connection.close();
					Thread.sleep(1000);
				} catch (Exception ee) {logger.warn("Connection close "+ee);};
			} else {
				logger.error("Unexpected JMS error from "+callerClass+" : "+e);
				retry=false;
			}
		} finally {
			try {
				if (session!=null) session.close();
				if (connection!=null)connection.close();
			} catch (Exception ee) {};
		}
		if (msgID==null) logger.error("Message not Sent");
		return msgID;		
	}


}
