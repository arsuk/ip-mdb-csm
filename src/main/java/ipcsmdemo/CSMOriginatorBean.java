package ipcsmdemo;

import org.apache.activemq.ScheduledMessage;
import org.apache.activemq.jms.pool.PooledConnectionFactory;
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
 * An MDB that simulates an Instant Payments CSM handling the originator (debtor bank) request.
 * If OK it forwards the payment to the beneficiary queue and inserts an entry in the timer task table.
 * If not OK the message is rejected to the originator response queue.  
 * 
 */
@MessageDriven(name = "CSMOriginatorBean", activationConfig = {
		//@ActivationConfigProperty(propertyName = "transaction-type", propertyValue = "Bean"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "instantpayments_mybank_originator_payment_request")  })

public class CSMOriginatorBean implements MessageDrivenBean, MessageListener
{
	static final long SEVENSECS=7000;
	
	private static final Logger logger = LoggerFactory.getLogger(CSMOriginatorBean.class);
	
    private MessageDrivenContext ctx = null;
    
    private QueueConnectionFactory qcf;	// To get outgoing connections for sending messages
    
	private MessageUtils messageUtils;
	
	private String myBIC=null;
    
	SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");	// 2018-12-28T15:25:40.264
	
	@EJB
	CSMDBSessionBean dbSessionBean;
    
	public CSMOriginatorBean() {
		
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
        //logger.info("OriginatorRequest.setMessageDrivenContext, this=" + hashCode());
    }
    
    public void ejbCreate()
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

            messageUtils=new MessageUtils();
            
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
        //logger.info("OriginatorRequest.ejbRemove, this="+hashCode());

        ctx = null;

    }
	
    Queue lookupQueue (Context ic, String name) {
    	Queue queue=null;
    	try {
    		queue = (Queue)ic.lookup(name);
    	} catch (NamingException e) {
    		logger.trace("Originator lookup error "+e);
		}
    	return queue;
    }
                
    public void onMessage(Message msg)
    {
        logger.trace("OriginatorRequest.onMessage, this="+hashCode());
        
    	if (messageUtils==null) {
    		logger.error("Not initialised for onMessage (missing response template or connection factory)");
    		ctx.setRollbackOnly();
    	}
    	else
        try {
        	String status="";
        	String reason="";
            String id=msg.getJMSMessageID();
            if (id==null) id=System.identityHashCode(this)+" "+System.nanoTime();	// JMSMessageID may not be set with Artemis
            if (msg.getJMSRedelivered()) logger.info("Redelivered "+new Date()+" "+id);

            TextMessage tm = (TextMessage) msg;
            Document msgdoc=XMLutils.bytesToDoc(tm.getText().getBytes("UTF-8"));	// pacs.008 input msg
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
            Queue beneficiaryDest = lookupQueue(ic,"CSMBeneficiaryRequestQueue"+creditorBIC);
            if (beneficiaryDest==null)  {
    			logger.trace("Lookup error creditor "+creditorBIC);
    			status="RJCT";	// Reject back to originator
    			reason="RC01";
    		}
        	Queue responseDest = lookupQueue(ic,"CSMOriginatorResponseQueue"+debtorBIC);
            if (responseDest==null)  {
    			logger.error("Lookup error debtor "+debtorBIC);
    			return;	// Stop because we cannot reply (no queue)
    		}

            // Check that this queue belongs to the originator BIC
        	Destination incomingDestination=msg.getJMSDestination();
        	// First find our BIC using the name in the MDB queue we are serving - only need to do this once
            if (myBIC==null) {
            	BankStatus[] bankStatus=dbSessionBean.getStatus("%");

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
    	        QueueConnection conn = qcf.createQueueConnection();
    	        conn.start();
    	        
       			QueueSession session = conn.createQueueSession(false,QueueSession.AUTO_ACKNOWLEDGE);
    			Queue timeoutDest;
    			try {
    				timeoutDest=(Queue)ic.lookup("CSMTimeoutQueue"); // Lookup JNDI name
    			} catch (NamingException e) {
    				timeoutDest=session.createQueue("instantpayments_csm_timeout");	// Use timer MDB default queue name 
    			}
        		QueueSender sender = session.createSender(timeoutDest);
                TextMessage sendmsg=session.createTextMessage(tm.getText());
                sendmsg.setStringProperty("TXID",txid);
                sendmsg.setStringProperty("DEBTORBIC",debtorBIC);
                sendmsg.setLongProperty(ScheduledMessage.AMQ_SCHEDULED_DELAY, SEVENSECS);	//	Activemq
                sendmsg.setLongProperty("_AMQ_SCHED_DELIVERY", System.currentTimeMillis() + SEVENSECS);	//Artemis
            	sender.send(sendmsg);
            	sender.close();
    			session.close();
    			conn.close();	// Return connection to the pool
        	}
            // Get a pooled connection and forward to beneficiary or reject to originator
            QueueConnection conn = qcf.createQueueConnection();
            conn.start();
            QueueSession session = conn.createQueueSession(false,
                        QueueSession.AUTO_ACKNOWLEDGE);
           
            if (status.equals("")) {
            	QueueSender sender = session.createSender(beneficiaryDest);
            	msg.setJMSType("ACCP"); // Allows for broker to use message selector
            	sender.send(msg);
            	sender.close();
            	
               	dbSessionBean.txStatusUpdate(txid,status,reason,origTime,value,CSMDBSessionBean.requestRecordType);
            } else {
            	// Reject to originator
            	QueueSender sender = session.createSender(responseDest);
            	
            	Document rejectdoc=messageUtils.CreateReject(msgdoc, reason);

                String rejectText=XMLutils.documentToString(rejectdoc);
                TextMessage sendmsg = session.createTextMessage(rejectText);
            	sendmsg.setJMSType("RJCT"); // Allows for broker to use message selector
            	sender.send(sendmsg);
            	sender.close();
            	
            	dbSessionBean.txInsert(sendmsg.getJMSMessageID(),txid,CSMDBSessionBean.responseRecordType,rejectText);
            	if (dbSessionBean.lastException==null) {
            		dbSessionBean.txStatusUpdate(txid,status,reason,origTime,value,CSMDBSessionBean.responseRecordType);
            	}
            	if (dbSessionBean.lastException!=null) {
            		ctx.setRollbackOnly();
            		return;
            	}
            }
            session.close();
            conn.close();	// Return connection to the pool
                      
        } catch(JMSException | UnsupportedEncodingException e) {
            throw new EJBException(e);
        } catch (NamingException e) {
			logger.error("Enexpected lookup error "+e);
		}
    }

}

