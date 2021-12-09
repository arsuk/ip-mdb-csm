package ipcsmdemo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.MessageDriven;
import javax.ejb.MessageDrivenBean;
import javax.ejb.MessageDrivenContext;
import javax.ejb.Timeout;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.TimeZone;

/** 
 * Timer task creating echo requests<br/>
 * An MDB creates timeout messages. We do this in a separate MDB so that each message and DB update is done in a separate transaction.
 * This avoids taking too long in the CSMTimerTask. 
 * @author Allan Smith
 * 
 */
@MessageDriven(name = "CSMTimeoutMDB", activationConfig = {
		//@ActivationConfigProperty(propertyName = "transaction-type", propertyValue = "Bean"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "instantpayments_csm_timeout")  })

public class CSMTimoutMDB extends MessageUtils implements MessageDrivenBean, MessageListener
{
	private static final Logger logger = LoggerFactory.getLogger(CSMTimoutMDB.class);

    private MessageDrivenContext ctx = null;
    private QueueConnectionFactory qcf;	// To get outgoing connections for sending messages
    
    private static int timeoutCount=0;
    
	SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");	// 2018-12-28T15:25:40.264
		
	@EJB
	CSMDBSessionBean dbSessionBean;
    
	public CSMTimoutMDB() {
		
		// Check to see if a private pool is defined (if this is not the case 'null' we will look for the named Wildfly managed pool)
		try {
			InitialContext iniCtx = new InitialContext(); 				

			qcf=PrivatePool.createPrivatePool(iniCtx,logger);	// Null if not configured in standalone.xml
		} catch (javax.naming.NameNotFoundException je) {
			logger.debug("Factory naming error "+je);
		} catch (Exception e) {
			logger.error("Activemq factory "+e);
		};
	}

    public void setMessageDrivenContext(MessageDrivenContext ctx)
    {
        this.ctx = ctx;
        //logger.info("BeneficiaryRequest.setMessageDrivenContext, this=" + hashCode());
    }
    
    public void ejbCreate()
    {
    	// Check for pooled connection factory created directly with ActiveMQ above or
    	// get a pool defined in the standalone.xml.
        try {
            // Create connection for replies and forwards
            InitialContext iniCtx = new InitialContext();

            if (qcf==null) {
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

    public void ejbRemove()
    {
        //logger.info("BeneficiaryRequest.ejbRemove, this="+hashCode());

        ctx = null;
        logger.info("Timeout count "+timeoutCount);

    }
	
    Queue lookupQueue (Context ic, String name) {
    	Queue queue=null;
    	try {
    		queue = (Queue)ic.lookup(name);
    	} catch (NamingException e) {
			logger.trace("Timeout lookup error "+e);
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
                
    public void onMessage(Message msg)
    {
        logger.trace("CSMTimeoutMDB.onMessage, this="+hashCode());
        
        try {
            Context ic=new InitialContext();

            String id=msg.getJMSMessageID();
            TextMessage tm = (TextMessage) msg;

	        // Forward timeout message
            String txid=tm.getStringProperty("TXID");
            String debtorbic=tm.getStringProperty("DEBTORBIC");
            String respText=null;
            
    		Document orgMsg=XMLutils.bytesToDoc(tm.getText().getBytes("UTF-8"));
        	// Reject to originator
    		String status="RJCT";
            String reason="AB05";
        	Document rejectDoc=createReject(orgMsg, reason);
           	respText=XMLutils.documentToString(rejectDoc);
        
           	// Insert response record (will fail if it is a duplicate so the message has already been responded to)
           	// If we can create the response record we can send the timeout message to the originator and reverse the liquidity change 
            if (dbSessionBean.txInsert(id,txid,CSMDBSessionBean.responseRecordType,respText)) {

                String valueStr=XMLutils.getElementValue(orgMsg,"IntrBkSttlmAmt");
                long value=0;	// Euro cents
                try {
                	float fvalue=Float.parseFloat(valueStr);	// Could do a format check here
                	value=(long) (fvalue*100);	// Convert to Euro cents and ignore fractions (not allowed with SEPA)
                } catch (RuntimeException e) {};

        		if (!dbSessionBean.updateLiquidity(debtorbic, value)) {
        			logger.warn("Could not update liqidity {} for transaction {} with value {}",debtorbic,txid,value);
        		}
            	// Update the status codes to show it timed out
            	dbSessionBean.txStatusUpdate(txid,status,reason,new Date(),value,CSMDBSessionBean.responseRecordType);

            	// Get connection from pool
		        QueueConnection conn = qcf.createQueueConnection();
		        conn.start();
				
	    		QueueSession session = conn.createQueueSession(false,QueueSession.AUTO_ACKNOWLEDGE);
	
	            String originatorName = lookupBankName(debtorbic);
	            if (originatorName==null) {
	            	logger.error("Unknown bic "+debtorbic);
	            	return;
	            }
	            String responseQueueName="instantpayments_"+originatorName+"_originator_payment_response";
	        	Queue responseDest = lookupQueue(ic,responseQueueName);
	        	if (responseDest==null)
	        		responseDest=session.createQueue(responseQueueName);

	           	QueueSender sender = session.createSender(responseDest);

	           	TextMessage sendmsg = session.createTextMessage(respText);
	   			sender.send(sendmsg);
	   			
	   			sender.close();
	   			session.close();
				conn.close();	// Return connection to the pool
				
				timeoutCount++;
            }

        } catch(JMSException e) {
            throw new EJBException(e);
        } catch (NamingException e) {
			logger.error("Lookup error "+e);
		} catch (UnsupportedEncodingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
    }

}

