package ipcsmdemo;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class MessageUtils {

	static SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");	// 2018-12-28T15:25:40.264
	private String responseTemplate="pacs.002.xml";
	private byte[] responseText;
	
	MessageUtils () {
        // Get XML template for creating messages from a file - real application code would have a better way of doing this
        responseText=XMLutils.getTemplate(responseTemplate);
	}
	
	Document CreateReject(Document msgdoc, String reason) {
		
		if (responseText==null) return null;

        String txid=XMLutils.getElementValue(msgdoc,"TxId");
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
