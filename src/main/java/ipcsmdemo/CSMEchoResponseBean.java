package ipcsmdemo;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import javax.annotation.PostConstruct;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.MessageDriven;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/** 
 * An MDB that simulates an Instant Payments client echo request / response function. 
 * It receives echo responses from the client bank and updates the status table with the time. No online/offline logic is implemented. 
 * 
 */
@MessageDriven(name = "CSMEchoResponseBean", activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "instantpayments_mybank_echo_response")
})

public class CSMEchoResponseBean implements MessageListener
{
	private static final Logger logger = LoggerFactory.getLogger(CSMEchoResponseBean.class);
    
	SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");	// 2018-12-28T15:25:40.264   
	
	@EJB
    CSMDBSessionBean dbSessionBean;
    
	public CSMEchoResponseBean() {
		

	}

    @PostConstruct
    public void ejbCreate()
    {

    }

    public void onMessage(Message msg)
    {
        try {

            String id=msg.getJMSMessageID();
            if (msg.getJMSRedelivered()) logger.info("Redelivered {} {}",new Date(),id);

            TextMessage tm = (TextMessage) msg;
            Document doc=XMLutils.bytesToDoc(tm.getText().getBytes("UTF-8"));
            
            String bic=XMLutils.getElementValue(doc,"BIC");
          
            if (dbSessionBean.getStatus(bic).length==0) {        
                logger.warn("Echo with unknown BIC {}",bic);
                return;
            };

            String timeStr=XMLutils.getElementValue(doc,"CreDtTm");
            Date sentDate=null;
            if (timeStr==null) {
            	sentDate=new Date();
            	logger.warn("Missing creditor time");
            } else {
            	try {
                    dateTimeFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
                    sentDate = dateTimeFormat.parse(timeStr);
            	} catch (Exception e) {
            		sentDate=new Date();
                	logger.warn("Bad creditor time "+timeStr);
            	}
            }
            
            dbSessionBean.setEchoStatus(bic, sentDate);
            
            // TBA - log status in DB.

        } catch(JMSException | IOException e) {
            throw new EJBException(e);
        }
    }
}

