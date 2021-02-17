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
import javax.ejb.Schedule;
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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.TimeZone;

/** 
 * Liquidity update MDB<br/>
 * An MDB logs updates in the liquidity table or memory cache. When triggered by the CSM timer task this bean updates the 
 * liquidity position in the status table.
 * We do this in a separate MDB so that each status update is done in a separate non-blocking transaction.
 * This avoids taking too long in the CSMTimerTask. 
 * @author Allan Smith
 *  
 */
@MessageDriven(name = "CSMLiquidityMDB", activationConfig = {
		//@ActivationConfigProperty(propertyName = "transaction-type", propertyValue = "Bean"),
        @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "1"),
        @ActivationConfigProperty(propertyName = "maxSessions", propertyValue = "1"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "instantpayments_csm_liquidity")  })

public class CSMLiquidityMDB implements MessageDrivenBean, MessageListener
{
	private static final Logger logger = LoggerFactory.getLogger(CSMLiquidityMDB.class);

    private MessageDrivenContext ctx = null;
    private QueueConnectionFactory qcf;	// To get outgoing connections for sending messages
    
    
	SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");	// 2018-12-28T15:25:40.264
		
	@EJB
	CSMDBSessionBean dbSessionBean;
    
	public CSMLiquidityMDB() {
		
	}

    public void setMessageDrivenContext(MessageDrivenContext ctx)
    {
        this.ctx = ctx;
        //logger.info("BeneficiaryRequest.setMessageDrivenContext, this=" + hashCode());
    }
    
    public void ejbCreate()
    {

    }

    public void ejbRemove()
    {
        //logger.info("BeneficiaryRequest.ejbRemove, this="+hashCode());

        ctx = null;

    }
                
    public void onMessage(Message msg)
    {
        logger.trace("CSMLiquidityMDB.onMessage, this="+hashCode());
        
       TextMessage tm = (TextMessage) msg;
            
       // Update liquidity status and delete liquidity change records
       	dbSessionBean.saveLiquidityStatus();

    }

}

