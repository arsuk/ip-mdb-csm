package ipcsmdemo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.MessageDriven;
import javax.ejb.MessageDrivenBean;
import javax.ejb.MessageDrivenContext;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;

import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** 
 * Payment request (pacs008) handling MDB<br/>
 * An MDB that simulates an Instant Payments CSM handling the originator (debtor bank) request.
 * If OK it forwards the payment to the beneficiary queue and inserts an entry in the timer task table.
 * If not OK the message is rejected to the originator response queue.  
 * @author Allan Smith
 *  
 */
@MessageDriven(name = "CSMOriginatorBean", activationConfig = {
		//@ActivationConfigProperty(propertyName = "transaction-type", propertyValue = "Bean"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "instantpayments_mybank_originator_payment_request")  })

public class CSMOriginatorBean extends MessageUtils implements MessageDrivenBean, MessageListener
{
	static final long SEVENSECS=7000;
	
	private static final Logger logger = LoggerFactory.getLogger(CSMOriginatorBean.class);
	
    private MessageDrivenContext ctx = null;
	
	private String myBIC=null;
	
	static AtomicInteger recvCount=new AtomicInteger(0);
	static AtomicLong totDeliveryTime=new AtomicLong(0);
	static Long maxDeliveryTime=0L;
	static Long minDeliveryTime=0L;
    
	SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");	// 2018-12-28T15:25:40.264
	
	@EJB
	CSMDBSessionBean dbSessionBean;
    
	public CSMOriginatorBean() {
		
	}

    public void setMessageDrivenContext(MessageDrivenContext ctx)
    {
        this.ctx = ctx;
        //logger.info("OriginatorRequest.setMessageDrivenContext, this=" + hashCode());
    }
    
    public void ejbCreate()
    {
    	logger.info("Started");
    }

    public void ejbRemove()
    {
        //logger.info("OriginatorRequest.ejbRemove, this="+hashCode());

        ctx = null;

    }
	              
    public void onMessage(Message msg)
    {
        logger.trace("OriginatorRequest.onMessage, this="+hashCode());
        
        try {
        	String status="";
        	String reason="";
            String id=msg.getJMSMessageID();
            if (id==null) id=System.identityHashCode(this)+" "+System.nanoTime();	// JMSMessageID may not be set with Artemis
            if (msg.getJMSRedelivered()) logger.info("Redelivered "+new Date()+" "+id);

            TextMessage tm = (TextMessage) msg;
            Document msgdoc=XMLutils.stringToDoc(tm.getText());	// pacs.008 input msg
            if (msgdoc==null) {
            	logger.error("Illegal message - bad xml");
            	return;
            }
            String txid=XMLutils.getElementValue(msgdoc,"TxId");
            String debtorBIC=XMLutils.getElementValue(XMLutils.getElement(msgdoc,"DbtrAgt"),"BIC");
            String creditorBIC=XMLutils.getElementValue(XMLutils.getElement(msgdoc,"CdtrAgt"),"BIC");
            String acceptanceTime=XMLutils.getElementValue(msgdoc,"AccptncDtTm");
            String valueStr=XMLutils.getElementValue(msgdoc,"IntrBkSttlmAmt");
            if (txid==null ||debtorBIC==null || status==null || acceptanceTime==null || creditorBIC==null || valueStr==null) {
            	logger.error("Illegal message - no TxId,GrpSts, AccptncDtTm, IntrBkSttlmAmt, DbtrAgt BIC or InstgAgt BIC");
            	return;
            }
            long value=0;	// Euro cents
            try {
            	float fvalue=Float.parseFloat(valueStr);	// Could do a format check here
            	value=(long) (fvalue*100);	// Convert to Euro cents and ignore fractions (not allowed with SEPA)
            } catch (RuntimeException e) {
            	status="RJCT";
            	reason="FF01";
            }
            
            Context ic = new InitialContext();

            String beneficiaryQueueName=null;
        	String beneficiaryName=lookupBankName(creditorBIC);
            if (beneficiaryName==null)  {
    			logger.trace("Lookup error creditor name "+creditorBIC);
    			status="RJCT";	// Reject back to originator
    			reason="RC01";
    		} else {
    			beneficiaryQueueName="instantpayments_"+beneficiaryName+"_beneficiary_payment_request";
    		}
            String originatorQueueName=null;
            String originatorName = lookupBankName(debtorBIC);
            if (originatorName==null)  {
    			logger.error("Lookup error debtor name "+debtorBIC);
    			return;	// Stop because we cannot reply (no name - no queue)
    		} else {
    			originatorQueueName="instantpayments_"+originatorName+"_originator_payment_response";
    		}
            
            // Get incoming queue name this mdb is serving from the message
        	Destination incomingDestination=msg.getJMSDestination();
            // Check that this queue belongs to the originator BIC        	
            if (myBIC==null) {
            	// First find our BIC using the name in the MDB queue we are serving - only need to do this once
            	for (int i=0;i<bankStatus.length;i++) {
            		if (incomingDestination.toString().contains(bankStatus[i].name)) {
            			myBIC=bankStatus[i].bic;
            			break;
            		}
            	}
            	if (myBIC==null) {
            		logger.warn("Missing debtor BIC "+debtorBIC+" in CSM status table");
        		}            		
            }
            // Check if BICs match - if not log a warning - should not happen in real life but OK in test
           	if (myBIC!=null && !myBIC.equals(debtorBIC)) {
    			logger.warn("BIC "+debtorBIC+" does not match "+myBIC+" of "+incomingDestination);      		
        	}
            
            if (!dbSessionBean.txInsert(id,txid,CSMDBSessionBean.requestRecordType,tm.getText())) {
            	if (dbSessionBean.lastException!=null) {
            		ctx.setRollbackOnly();
            		return;
            	}
            	logger.warn("Duplicate "+new Date()+" "+id+" "+txid);
            	return;
            }
            
            // Check timeout
            Date origTime = null;
            try {
            	dateTimeFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            	origTime = dateTimeFormat.parse(acceptanceTime);
            } catch (Exception e) {
            	logger.error("Bad AccptncDtTm "+acceptanceTime+" "+txid);
            	return;
            }
        	long diff=new Date().getTime()-origTime.getTime();
        	logger.debug("Acceptance time {}",origTime);
        	if (diff>SEVENSECS) {
        		status="RJCT";
        		reason="AB05";
        	}

        	// Update deliver stats - logged by timer task
        	recvCount.incrementAndGet();
        	totDeliveryTime.addAndGet(diff);
        	if (diff<minDeliveryTime||minDeliveryTime==0) minDeliveryTime=diff;
           	if (diff>maxDeliveryTime) maxDeliveryTime=diff;      	

           	// Liquidity reservation and check
        	if (status.isEmpty()) {
        		if (!dbSessionBean.updateLiquidity(debtorBIC, -value)) {
            		status="RJCT";
            		reason="AM04";
        		}
        	}
        	
        	// If not rejected start timer
        	if (status.equals("")){
                //dbSessionBean.insertTimer(txid,origTime,debtorBIC,tm.getText());

    			String timeoutDest;
    			try {
    				timeoutDest=(String)ic.lookup("CSMTimeoutQueue"); // Lookup JNDI name
    			} catch (NamingException e) {
    				timeoutDest="instantpayments_csm_timeout";	// Use timer MDB default queue name 
    			}
                MessageUtils.sendMessage(tm.getText(),timeoutDest,"ACCP",debtorBIC,txid);
        	}
            // Get a pooled connection and forward to beneficiary or reject to originator

          
            if (status.equals("")) {
            	MessageUtils.sendMessage(tm.getText(),beneficiaryQueueName,"ACCP");
            	
               	dbSessionBean.txStatusUpdate(txid,status,reason,origTime,value,CSMDBSessionBean.requestRecordType);
            } else {
            	// Reject to originator
           	
            	Document rejectdoc=createReject(msgdoc, reason);
                String rejectText=XMLutils.documentToString(rejectdoc);
                
                String msgID=MessageUtils.sendMessage(rejectText,originatorQueueName,"RJCT");
            	
            	dbSessionBean.txInsert(msgID,txid,CSMDBSessionBean.responseRecordType,rejectText);
            	if (dbSessionBean.lastException==null) {
            		dbSessionBean.txStatusUpdate(txid,status,reason,origTime,value,CSMDBSessionBean.responseRecordType);
            	}
            	if (dbSessionBean.lastException!=null) {
            		ctx.setRollbackOnly();
            		return;
            	}
            }
                      
        } catch(JMSException e) {
            throw new EJBException(e);
        } catch (NamingException e) {
			logger.error("Enexpected lookup error "+e);
		}
    }

}

