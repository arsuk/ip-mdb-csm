package ipcsmdemo;

import javax.jms.QueueConnectionFactory;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.slf4j.Logger;

/** 
 * Get connection from the Wildfly message queue connection pool<br/>
 * The connection pool is identified by looking up a system property or JNDI property with the name ConnectionFactory. 
 * @author Allan Smith
 * 
 */
public class ManagedPool {

	static private QueueConnectionFactory privateManagedConnectionFactory=null;
	
	static boolean firstTime=true;

	// Lookup system property "ConnectionFactory" to find out which pool we should use.
	// If not found we use the pool defined in the standalone.xml for JBoss / Wildfly.

	static QueueConnectionFactory getPool(InitialContext iniCtx, Logger logger) throws NamingException {
    	// Use an application server defined ActiveMQ connection pool using a JNDI name from a system property
		String cfStr=System.getProperty("ConnectionFactory");
		// If the system property is not defined use the JNDI name used by the JBoss standalone-full.xml example 
		if (cfStr==null) cfStr="ConnectionFactory";
		if (!cfStr.startsWith("/")) cfStr="/"+cfStr;

		Object cf = iniCtx.lookup(cfStr);
		privateManagedConnectionFactory = (QueueConnectionFactory) cf;
		if (firstTime) logger.info("Using queue factory "+cfStr);
		firstTime=false;

		return privateManagedConnectionFactory;
	}
}
