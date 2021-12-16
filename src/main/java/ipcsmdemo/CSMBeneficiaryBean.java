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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.TimeZone;

/**
 * CSM Beneficiary Response Handler.<br/>
 * An MDB that simulates an Instant Payments CSM handling beneficiary (creditor bank) responses. 
 * It receives beneficiary responses and if OK it forwards the answer to the originator and a confirmation to the beneficiary bank.
 * It also receives time out beneficiary messages from the timer task which will be forwarded to the originator. Duplicates are 
 * ignored because we might get a timeout and a response.
 * @author Allan Smith
 * 
 */
@MessageDriven(name = "CSMBeneficiaryBean", activationConfig = {
		//@ActivationConfigProperty(propertyName = "transaction-type", propertyValue = "Bean"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "instantpayments_mybank_beneficiary_payment_response")  })

public class CSMBeneficiaryBean extends MessageUtils implements MessageDrivenBean, MessageListener
{
	private static final Logger logger = LoggerFactory.getLogger(CSMBeneficiaryBean.class);

    private MessageDrivenContext ctx = null;
    private QueueConnectionFactory qcf;	// To get outgoing connections for sending messages
    
	private String defaultTemplate="pacs.002.xml";
    
	SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");	// 2018-12-28T15:25:40.264

	private byte[] docText;
	
	static final long SEVENSECS=7000;
	
	@EJB
	CSMDBSessionBean dbSessionBean;
    
	public CSMBeneficiaryBean() {
		
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

            // Get XML template for creating messages from a file - real application code would have a better way of doing this
            docText=XMLutils.getTemplate(defaultTemplate);
            
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

    }
                
    public void onMessage(Message msg)
    {
        logger.trace("CSMBeneficiaryResponse.onMessage, this="+hashCode());
        
    	if (docText==null) {
    		logger.error("IPBeneficiaryRequest not initialised for onMessage (missing response template or destination name or factory)");
    		ctx.setRollbackOnly();
    	}
    	else
        try {
            String id=msg.getJMSMessageID();
            if (id==null) id=System.identityHashCode(this)+" "+System.nanoTime();	// JMSMessageID may not be set with Artemis
            if (msg.getJMSRedelivered()) logger.info("Redelivered "+new Date()+" "+id);

            TextMessage tm = (TextMessage) msg;
            Document doc=XMLutils.stringToDoc(tm.getText());	// pacs.002 new document from input message
            String txid=XMLutils.getElementValue(doc,"OrgnlTxId");
            if (txid==null) {
            	logger.error("Illegal message - no txid - message dropped");
            	return;           	
            }

            String debtorBIC=XMLutils.getElementValue(XMLutils.getElement(doc,"DbtrAgt"),"BIC");
            String status=XMLutils.getElementValue(doc,"GrpSts");
            String acceptanceTime=XMLutils.getElementValue(doc,"AccptncDtTm");
            String creditorBIC=XMLutils.getElementValue(XMLutils.getElement(doc,"InstgAgt"),"BIC");
            if (debtorBIC==null || status==null || acceptanceTime==null || creditorBIC==null) {
            	logger.error("Illegal message - no GrpSts, AccptncDtTm, DbtrAgt BIC or InstgAgt BIC");
            	return;
            }
            String reason=null;
          	Element rsnInf=XMLutils.getElement(doc,"StsRsnInf");
           	if (rsnInf!=null) reason=XMLutils.getElementValue(rsnInf,"Cd");
           	if (reason==null) reason="";
           	else reason=reason.trim();
           	String confirmationReason="";
           	
            Context ic = new InitialContext();

            String confirmationQueueName=null;            
            String beneficiaryName = lookupBankName(creditorBIC);
            if (beneficiaryName==null)  {
    			logger.error("Lookup error creditor "+creditorBIC);
    			return;
    		} else {
    			confirmationQueueName="instantpayments_"+beneficiaryName+"_beneficiary_payment_confirmation";
    		}
            String responseQueueName=null;
            String originatorName = lookupBankName(debtorBIC);
            if (originatorName==null)  {
    			logger.error("Lookup error debtor "+debtorBIC);
    			return;
    		} else {
    			responseQueueName="instantpayments_"+originatorName+"_originator_payment_response";
    		}

           	// Insert response record (will fail if it is a duplicate or DB error)
            if (!dbSessionBean.txInsert(id,txid,CSMDBSessionBean.responseRecordType,tm.getText())) {
            	// Rollback on DB exceptions (already logged)
            	if (dbSessionBean.lastException!=null) {
            		ctx.setRollbackOnly();
            		return;
            	}
            	// Check what we should do with the duplicate - get the status
            	TXstatus duplicateStatus=dbSessionBean.getTXstatus(txid,CSMDBSessionBean.responseRecordType);

            	if (duplicateStatus==null) {
            		logger.error("Duplicate detected but cannot get status "+txid);
            		status="RJCT";
            		confirmationReason="FF01";	// Send confirmation reject but no response
            	} else if (reason.equals("") && duplicateStatus.reason.equals("AB05")) {
            		status="RJCT";
            		confirmationReason="AB01";	// Valid response but already timed out - send confirmation reject but no response
	            	logger.trace("Already timed out - reject to conf queue- JMS ID {} TXID {} reason {} duplicate reason {}",id,txid,reason,duplicateStatus.reason);
            	} else if (duplicateStatus.reason.equals("AB06")) {
            		// Timed out already and error message back from beneficiary - just drop
	            	logger.trace("Already timed out - error, so ignore - JMS ID {} TXID {} reason {} duplicate reason {}",id,txid,reason,duplicateStatus.reason);
	            	return;
            	} else {
            		// This should not happen
	            	logger.warn("Already responded JMS ID {} TXID {} reason {} duplicate reason {}",id,txid,reason,duplicateStatus.reason);
	            	return;
            	}
            }
           	
            // Update message ID time so we can forward the message
            XMLutils.setElementValue(doc,"MsgId",hashCode()+"-"+System.nanoTime());

            // Get a pooled connection
            QueueConnection conn = qcf.createQueueConnection();
            conn.start();
            QueueSession session = conn.createQueueSession(false,QueueSession.AUTO_ACKNOWLEDGE);
        	Queue responseDest = lookupQueue(ic,responseQueueName);
        	if (responseDest==null)
        		responseDest=session.createQueue(responseQueueName);
        	QueueSender senderResp = session.createSender(responseDest);

            Date origTime = null;
            try {
            	dateTimeFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            	origTime = dateTimeFormat.parse(acceptanceTime);
            } catch (Exception e) {
            	logger.error("Bad AccptncDtTm "+acceptanceTime+" "+txid);
            	return;
            }

            TXstatus originalStatus=null;
            for (int i=0;i<10&&originalStatus==null;i++) {
            	// Should not happen but if non-xa connector used message can arrive before original message commit - if so wait
            	originalStatus=dbSessionBean.getTXstatus(txid, CSMDBSessionBean.requestRecordType);
            	if (originalStatus==null) {
            		logger.warn("Original request message missing "+txid+" "+(i+1));
            		try {Thread.sleep(10);} catch (InterruptedException e) {};          		
            	}
            }
            if (originalStatus==null) {
            	logger.error("Original request message missing "+txid);
        		status="RJCT";
        		confirmationReason="FF01";
            }
            if (status.equals("ACCP")) {
               	// Add value to creditor liquidity
        		if (!dbSessionBean.updateLiquidity(creditorBIC, originalStatus.value)) {
        			// Update failed
            		status="RJCT";
            		confirmationReason="AM04";
        		}
            } else if (confirmationReason.isEmpty()) {
            	// Reverse liquidity debit with a credit to the debtor liquidity.
        		if (!dbSessionBean.updateLiquidity(debtorBIC, originalStatus.value)) {
        			logger.warn("Could not update liqidity {} for transaction {} with value {}",debtorBIC,txid,originalStatus.value);
        		}
        	}
            TextMessage sendmsg;
            if (status.equals("ACCP")) {
            	// Forward to originator
            	sendmsg = session.createTextMessage(XMLutils.documentToString(doc));
                sendmsg.setJMSType(status); // Allows for broker to use message selector
                senderResp.send(sendmsg);
                senderResp.close();
            } else if (!confirmationReason.isEmpty()) {
            	// No forward to originator - create confirmation reject
               	sendmsg = session.createTextMessage(XMLutils.documentToString(createReject(doc, confirmationReason)));
            } else {
            	// Send reject to originator
            	sendmsg = session.createTextMessage(XMLutils.documentToString(createReject(doc, reason)));
                sendmsg.setJMSType(status); // Allows for broker to use message selector
                senderResp.send(sendmsg);
                senderResp.close();
        	}
            
        	// Send beneficiary confirmation if ACCP or or confirmation reject due to timeout or system error.  
            if (status.equals("ACCP") || !confirmationReason.isEmpty()) {
            	Queue confirmationDest = lookupQueue(ic,confirmationQueueName);
            	if (confirmationDest==null)
            		confirmationDest=session.createQueue(confirmationQueueName);
            	QueueSender senderConf=session.createSender(confirmationDest);
                senderConf.send(sendmsg);
                senderConf.close();
            }
            session.close();
            conn.close();	// Return connection to the pool

            if (!confirmationReason.isEmpty()) reason=confirmationReason;	// Log new reason - original reason in message
           	logger.trace("TX status {} {} {}",txid,status,reason);
       		long value=0;
           	if (originalStatus!=null) value=originalStatus.value;
           	dbSessionBean.txStatusUpdate(txid,status,reason,origTime,value,CSMDBSessionBean.responseRecordType);
        	if (dbSessionBean.lastException!=null) {
        		ctx.setRollbackOnly();
        		return;
           	}
        } catch(JMSException e) {
            throw new EJBException(e);
        } catch (NamingException e) {
			logger.error("Lookup error "+e);
		}
    }

}

