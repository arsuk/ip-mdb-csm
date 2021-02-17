package ipcsmdemo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.*;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
/** 
 * Class for XML handling procedures used by multiple classes<br/>
 * These are wrapper procedures around the Java XML Document class. 
 * @author Allan Smith
 * 
 */
public class XMLutils {
	private static final Logger logger = LoggerFactory.getLogger(XMLutils.class);
	
	static DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
	static TransformerFactory tf = TransformerFactory.newInstance();

	private XMLutils() {
		// hide constructor for Utility-class
	}
	// XML Document help methods - used by other classes as well
	public static byte[] getTemplate(String file) {
		byte[] docBytes=null;
		try {
			docBytes=Files.readAllBytes(Paths.get(file));
		} catch (IOException | NullPointerException e) {
			logger.error("IP Get Tamplate failed for path "+new File(file).getAbsolutePath(),e);
		}
		return docBytes;
	}
	public static Document bytesToDoc(byte[]text) {
		Document doc=null;
		try {			
			DocumentBuilder builder = factory.newDocumentBuilder();

			ByteArrayInputStream input = new ByteArrayInputStream(text);
			doc = builder.parse(input);
			input.close();
		} catch (NullPointerException | SAXException | IOException | ParserConfigurationException e) {
			logger.error("XML document: ",e);
		}
		return doc;
	}
	public static Document stringToDoc (String xml) {
		try {
			return bytesToDoc(xml.getBytes("UTF-8"));
		} catch (UnsupportedEncodingException e) {
			logger.error("XML document: ",e);
			return null;
		}
	}
	public static String documentToString(Document doc) {
		try {
			StringWriter sw = new StringWriter();
			tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			tf.setAttribute("indent-number", new Integer(2));
			Transformer transformer = tf.newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
			transformer.setOutputProperty(OutputKeys.METHOD, "xml");
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "1");
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			transformer.transform(new DOMSource(doc), new StreamResult(sw));
			return sw.toString();
		} catch (TransformerException ex) {
			throw new IllegalStateException("XML to String failed with a TransformerException", ex);
		}
	}
	public static String getElementValue(Document doc, String tagName) {
		if (doc==null) return null;
		return getElementValue(doc.getDocumentElement(),tagName);
	}
	public static String getElementValue(Element element, String tagName) {
		if (element==null) return null;
		NodeList nl=element.getElementsByTagName(tagName);
		if (nl.getLength()>0) return nl.item(0).getTextContent();
		else return null;
	}
	public static void setElementValue(Document doc, String tagName, String value) {
		if (doc==null) return;
		setElementValue(doc.getDocumentElement(),tagName,value);
	}
	public static void setElementValue(Element element, String tagName, String value) {
		if (value==null||element==null) return; // In case old doc getElementValue returned null do not set new doc
		NodeList nl=element.getElementsByTagName(tagName);
		if (nl.getLength()>0) nl.item(0).setTextContent(value);
	}
	public static void copyElementValues(Document doc1, Document doc2, String tagName) {
		if (doc1==null||doc2==null) return;
		NodeList nl1=doc1.getElementsByTagName(tagName);
		NodeList nl2=doc2.getElementsByTagName(tagName);
		for(int i=0;i<nl1.getLength()&&i<nl2.getLength();i++)
			nl2.item(i).setTextContent(nl1.item(i).getTextContent());
	}
	public static Element getElement(Element element,String tagName) {
		if (element==null) return null;
		NodeList nl=element.getElementsByTagName(tagName);
		if (nl.getLength()>0) return (Element)nl.item(0);
		else return null;
	}
	public static Element getElement(Document doc,String tagName) {
		if (doc==null) return null;
		NodeList nl=doc.getElementsByTagName(tagName);
		if (nl.getLength()>0) return (Element)nl.item(0);
		else return null;
	}
}

