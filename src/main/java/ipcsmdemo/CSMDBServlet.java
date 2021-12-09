package ipcsmdemo;
 
import javax.ejb.EJB;
import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.w3c.dom.*;

import javax.xml.parsers.*;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
/**
 * Servlet supporting DB queries<br/>
 * The CSM demo html DB query page uses this servlet to retrieve transaction information or delete records
 * @author Allan Smith
 *
 */
@WebServlet("/db")
public class CSMDBServlet extends HttpServlet {

	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");	// 2018-12-28
	SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");	// 2018-12-28T15:25:40.264

	@EJB
	CSMDBSessionBean dbSessionBean;
	
	@Override
    public void init() throws ServletException {
    }
 
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {

    	String id=req.getParameter("id");
    	String key=req.getParameter("key");
    	if (key==null) key="id";
    	
    	String data="bad parameter";
    	
    	if (id==null) 
    		data="Records "+dbSessionBean.txCount();
    	else {
    		if (!id.equals("*"))	// Single * is last tx - otherwise treat as %
    			id=id.replaceAll("\\*", "%");
       		if(key.equals("id") || key.equals("txid"))
				data=dbSessionBean.txQueryKey(id,key);
       		else
       			if(key.equals("delete"))
       				data=dbSessionBean.deleteTX(id,key);
       			else
       				data="Bad key "+key+", must be txid or id";
    	}

    	PrintWriter writer=res.getWriter();

       	writer.println(dateTimeFormat.format(new Date())+"\n"+data);

    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
    	doGet(req,res);
    }

}

