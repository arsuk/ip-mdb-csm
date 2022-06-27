package ipcsmdemo;
 
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.ejb.*;
import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
/** 
 * Echo request timer task<br/>
 * A timer task that simulates an Instant Payments CSM echo request processing.
 * It sends and echo request to each configured client every interval.  
 * @author Allan Smith
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
	
	public CSMTimedEchoRequest() {
		
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
        	
	        int sent=0;
	        for (int i=0;i<banks.length;i++) {

	        	String bic=banks[i].bic;
	        	String name=banks[i].name;
	        	String queueName="instantpayments_"+name+"_echo_request";

		      	XMLutils.setElementValue(XMLutils.getElement(doc,"InstdAgt"), "BIC", bic);
		       	MessageUtils.sendMessage(XMLutils.documentToString(doc),queueName);
	       	    sent++;
	       	    logger.trace("Send echo on {} to {}",queueName,bic);

	        }
            logger.trace("Sent {} echos at {}",sent,new Date());
        } catch (NamingException ne) {
        	logger.error("Naming exception: "+ne);
        }
 
    }
}
