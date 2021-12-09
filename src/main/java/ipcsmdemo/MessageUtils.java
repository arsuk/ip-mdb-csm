package ipcsmdemo;

import java.text.SimpleDateFormat;
import java.util.Date;

import javax.ejb.EJB;
import javax.jms.Queue;
import javax.naming.Context;
import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
/** 
 * Class for XML message handling procedures used by multiple classes<br/>
 * CreateReject - create a reject pacs002 from a payment request pacs008. 
 * @author Allan Smith
 * 
 */
public class MessageUtils {

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

}
