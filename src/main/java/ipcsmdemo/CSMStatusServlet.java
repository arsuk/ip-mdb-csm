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
import java.text.DecimalFormat;
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
 
@WebServlet("/status")
public class CSMStatusServlet extends HttpServlet {

	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");	// 2018-12-28
	SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");	// 2018-12-28T15:25:40.264

	@EJB
	CSMDBSessionBean dbSessionBean;
	
	@Override
    public void init() throws ServletException {
    }
 
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {

    	DecimalFormat df = new DecimalFormat("#0.00");
    	String id=req.getParameter("bic");
    	if (id==null || id.isEmpty()) id="%";
    	
    	String data="";
    	
    	BankStatus banks[]=dbSessionBean.getStatus(id);
    	
    	for (int i=0;i<banks.length;i++) {
    		data=data+"BIC "+banks[i].bic+", "+banks[i].name+
    					",  Liquidity "+df.format(banks[i].liquidity/100.0)+	// Convert from cents to euro for display
    					",  Last echo "+banks[i].lastecho+" "+"\n";
    	}

    	PrintWriter writer=res.getWriter();

       	writer.println(dateTimeFormat.format(new Date())+"\n"+data);

    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
    	doGet(req,res);
    }

}

