package ipcsmdemo;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.Stateless;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.Set;
 
@Stateless
public class CSMDBSessionBean  {
	private static final Logger logger = LoggerFactory.getLogger(CSMDBSessionBean.class);

	private static final int LONG_QUERY=500;
	
	public static final String requestRecordType="csmrequest";
	public static final String responseRecordType="csmresponse";
	public static final String badRecordType="csmbadrecord";
	
	static final long SEVENSECONDS=7000;
	
	private DataSource ds;
	private static Hashtable<String, Float> liquidityCache = null;
	
	private static Hashtable<String, Timer> timerCache=null;
	
	static final String datasourceProperty="IPCSMdatasource";
	
	public Exception lastException=null;

	// CSMSTATUS methods
	public void setEchoStatus(String bic,Date echoTime) {
		Connection con=null;
		Statement stmt=null;
		lastException=null;
		if (ds==null) {
			lastException=new Exception("Datasource not initialized");
		} else {
    		java.sql.Timestamp sTime = new java.sql.Timestamp(echoTime.getTime());
			try {
				con = ds.getConnection();    // Connect using datasource username/pwd etc.

				stmt = con.createStatement();

				String sql = "UPDATE CSMSTATUS SET lastecho='" + sTime + "'" +
						"WHERE bic='" + bic + "'";
				stmt.executeUpdate(sql);
				stmt.close();
			} catch (SQLException e) {
				logger.error("Update status error " + e);
				lastException=e;
			} finally {
				try {
					if (stmt!=null)
						stmt.close();
					if (con != null)
						con.close();
				} catch (Exception e) {
					logger.error("Error on close " + e);
				};
			}
		}
	}
	public void setLiqudityStatus(String bic,float liquidity) {
		Connection con=null;
		Statement stmt=null;
		lastException=null;
		if (ds==null) {
			lastException=new Exception("Datasource not initialized");
		} else {
			try {
				con = ds.getConnection();    // Connect using datasource username/pwd etc.

				stmt = con.createStatement();

				String sql = "UPDATE CSMSTATUS SET liqudity=" + liquidity + 
						" WHERE bic='" + bic + "'";
				stmt.executeUpdate(sql);
				stmt.close();
			} catch (SQLException e) {
				logger.error("Update error " + e);
				lastException=e;
			} finally {
				try {
					if (stmt!=null)
						stmt.close();
					if (con != null)
						con.close();
				} catch (Exception e) {
					logger.error("Error on close " + e);
				};
			}
		}
	}
	public boolean updateLiquidity(String bic,float value) {
		boolean success=true;
		lastException=null;
		synchronized (liquidityCache) {
			Float liquidityObject=liquidityCache.get(bic);
			if (liquidityObject==null) {
				success=false;
			} else {
				if (((float)liquidityObject)+value<0) {
					success=false;
				} else {
					float liquidity=((float)liquidityObject)+value;
					liquidityCache.replace(bic,new Float(liquidity));
				}
			}			
		}
    	return success;
	}
	public int saveLiquidityStatus() {
		Connection con=null;
		Statement stmt=null;
		lastException=null;
		int changes=0;
		if (ds==null) {
			lastException=new Exception("Datasource not initialized");
		} else {
			try {
				con = ds.getConnection();    // Connect using datasource username/pwd etc.
				stmt = con.createStatement();

	    		Set<String> keys = liquidityCache.keySet();
	    		for(String bic: keys){
	    			float value=liquidityCache.get(bic);

	    			String sql = "UPDATE CSMSTATUS SET liquidity=" + value + 
	    						" WHERE bic='" + bic + "'";
	    			stmt.executeUpdate(sql);
	    			changes=changes+stmt.getUpdateCount();
	    		}
				stmt.close();
			} catch (SQLException e) {
				logger.error("Update error " + e);
				lastException=e;
			} finally {
				try {
					if (stmt!=null)
						stmt.close();
					if (con != null)
						con.close();
				} catch (Exception e) {
					logger.error("Error on close " + e);
				};
			}
		}
    	return changes;
	}
    public BankStatus[] getStatus(String bic) {
    	long startTime=System.currentTimeMillis();
    	Connection con = null;
		Statement st=null;
    	BankStatus[]data=null;
		lastException=null;
		if (ds==null) {
			lastException=new Exception("Datasource not initialized");
		} else {
			try {
				con = ds.getConnection();
				st = con.createStatement();
				String sql= "SELECT * FROM CSMSTATUS WHERE BIC LIKE '"+bic+"'";
				ResultSet rs = st.executeQuery(sql);
		        java.util.ArrayList<BankStatus> dataList =
                        new java.util.ArrayList<BankStatus>();

				while (rs.next()) {
					BankStatus row = new BankStatus();
					row.bic=rs.getString("BIC");
					row.lastecho=rs.getString("LASTECHO");
					row.liquidity=rs.getFloat("LIQUIDITY");
					dataList.add(row);
				}
				data=new BankStatus [dataList.size()];
				for (int i=0;i<data.length;i++) data[i]=dataList.get(i);
				rs.close();
    		} catch (SQLException e) {
    			logger.error("Query error " + e);
    		} finally {
				try {
					if (st!=null)
						st.close();
					if (con != null)
						con.close();
				} catch (Exception e) {
					logger.error("Error on close " + e);
					lastException=e;
				};
				long queryTime=System.currentTimeMillis()-startTime;
				if (queryTime>LONG_QUERY) logger.warn("Long status query " + queryTime + " ms");
			}
		}
        return data;
    }
	public void insertStatus(String bic,Date echoTime,long liquidity) {
		Connection con=null;
		Statement stmt=null;
		lastException=null;
		if (ds==null) {
			lastException=new Exception("Datasource not initialized");
		} else {
    		java.sql.Timestamp sTime = new java.sql.Timestamp(echoTime.getTime());
			try {
				con = ds.getConnection();    // Connect using datasource username/pwd etc.

				stmt = con.createStatement();

				String sql = "INSERT INTO (bic,liquidity,lastecho) CSMSTATUS " +
						"VALUES ('" + bic + "', '" + liquidity + "', '" + sTime + "')";
				stmt.executeUpdate(sql);
			} catch (SQLException e) {
				logger.error("Insert error " + e);
				lastException=e;
			} finally {
				try {
					if (stmt!=null)
						stmt.close();
					if (con != null)
						con.close();
				} catch (Exception e) {
					logger.error("Error on close " + e);
				};
			}
		}
	}
	
	// Timer methods
	public void insertTimer(String txid,Date adateTime,String debtorBIC,String msg) {
		Timer timer=new Timer();
		timer.txid=txid;
		timer.adatetime=adateTime;
		timer.debtorBIC=debtorBIC;
		timer.msg=msg;
		lastException=null;
		if (timerCache!=null) {
			synchronized (timerCache) {
				timerCache.put(txid,timer);
			}
			return;
		}
		Connection con=null;
		Statement stmt=null;
		if (ds==null) {
			lastException=new Exception("Datasource not initialized");
		} else {
    		java.sql.Timestamp sTime = new java.sql.Timestamp(adateTime.getTime());
    		try {
				con = ds.getConnection();    // Connect using datasource username/pwd etc.
				stmt = con.createStatement();
				
				String sql = "INSERT INTO CSMTIMER (txid,debtorbic,adatetime,ndatetime,msg) " +
						"VALUES ('" + txid + "','" + debtorBIC + "','" + sTime + "', now(), '" + msg + "')";
				stmt.executeUpdate(sql);
			} catch (SQLException e) {
				logger.error("Insert tx status error " + e);
				lastException=e;
			} finally {
				try {
					if (stmt!=null)
						stmt.close();
					if (con != null)
						con.close();
				} catch (Exception e) {
					logger.error("Error on close " + e);
				};
			}
		}
	}
	public void deleteTimers(String[] txids) {
		lastException=null;
		if (timerCache!=null) {
			synchronized (timerCache) {
				for (String txid: txids) {
					timerCache.remove(txid);
				}
			}
			return;
		}		
		long startTime=System.currentTimeMillis();
		Connection con=null;
		Statement stmt=null;
		if (ds==null) {
			lastException=new Exception("Datasource not initialized");
		} else
    	if (txids.length>0) {
			try {
				con = ds.getConnection();    // Connect using datasource username/pwd etc.
				stmt = con.createStatement();

				StringBuilder sql = new StringBuilder("DELETE CSMTIMER WHERE TXID in (");
				for (int i=0;i<txids.length;i++) {
					sql.append("'"+txids[i]+"'");
					if (i<txids.length-1) sql.append(",");
				}
				sql.append(")");
				stmt.executeUpdate(sql.toString());
				long queryTime=System.currentTimeMillis()-startTime;
				if (queryTime>LONG_QUERY) logger.warn("Long timer delete " + queryTime + " ms");
			} catch (SQLException e) {
				logger.error("Delete tx status error " + e);
				lastException=e;
			} finally {
				try {
					if (stmt!=null)
						stmt.close();
					if (con != null)
						con.close();
				} catch (Exception e) {
					logger.error("Error on close " + e);
				};
			}
		}
	}
	public void deleteTimer(String txid) {
		String txids[]={txid};
		deleteTimers(txids);
	}
	public Timer[] getTimers (int maxMessages) {
		lastException=null;		
		if (timerCache!=null) {
			Timer data[]=null;
			synchronized (timerCache) {
				long now=System.currentTimeMillis();
		        java.util.ArrayList<Timer> dataList =
	                    new java.util.ArrayList<Timer>();
				Set<String> keys = timerCache.keySet();
				int count=0;
				for(String key: keys){
					Timer timer=timerCache.get(key);				
					if (timer.adatetime.getTime()+SEVENSECONDS<now) {
						dataList.add(timer);
						count++;
					};
					if (count>=maxMessages) break;
				}
				data=new Timer [dataList.size()];
				for (int i=0;i<data.length;i++) {
					data[i]=dataList.get(i);
				}
			}
			return data;
		}
		long startTime=System.currentTimeMillis();

		Connection con=null;
		Statement st=null;
		Timer[] data=null;
		if (ds==null) {
			lastException=new Exception("Datasource not initialized");
		} else
		try {
			con = ds.getConnection();
			st = con.createStatement();
			st.setQueryTimeout(1);
			st.setMaxRows(maxMessages);
			String sql= "SELECT * FROM CSMtimer WHERE DATEADD('SECOND',7,adatetime)<now()";
			ResultSet rs = st.executeQuery(sql);
			ArrayList<Timer> resultList=new ArrayList<Timer>();
			while (rs.next()) {
				Timer timer=new Timer();
				timer.txid=rs.getNString("TXID");
				timer.adatetime=rs.getTimestamp("ADATETIME");
				timer.debtorBIC=rs.getNString("DEBTORBIC");
				timer.msg=rs.getNString("MSG");
				resultList.add(timer);
			}
			data=new Timer [resultList.size()];
			for (int i=0;i<data.length;i++) data[i]=resultList.get(i);
			rs.close();
			long queryTime=System.currentTimeMillis()-startTime;
			if (queryTime>LONG_QUERY) logger.warn("Long timer query " + queryTime + " ms");
		} catch (SQLException e) {
			if (e.toString().contains("Timeout") || e.toString().contains("canceled"))
				logger.warn("Timer query timeout");
			else {
				logger.error("Timer query error " + e);
				lastException=e;
			}
		} finally {
			try {
				if (st!=null)
					st.close();
				if (con != null)
					con.close();
			} catch (Exception e) {
				logger.error("Error on close " + e);
			};
		}
		return data;
	}
	public Date getTimerTime (String txid) {
		lastException=null;		
		if (timerCache!=null) {
			Date atime=null;
			synchronized (timerCache) {
				Timer timer=timerCache.get(txid);
				atime=timer.adatetime;
			}
			return atime;
		}
		long startTime=System.currentTimeMillis();

		Connection con=null;
		Statement st=null;
		Date atime=null;
		if (ds==null) {
			lastException=new Exception("Datasource not initialized");
		} else
		try {
			con = ds.getConnection();
			st = con.createStatement();
			st.setQueryTimeout(1);
			String sql= "SELECT ADATETIME FROM CSMtimer WHERE TXID='"+txid+"'";
			ResultSet rs = st.executeQuery(sql);
			while (rs.next()) {
				atime=rs.getDate("ADATETIME");
			}
			rs.close();
			long queryTime=System.currentTimeMillis()-startTime;
			if (queryTime>LONG_QUERY) logger.warn("Long timer query " + queryTime + " ms");
		} catch (SQLException e) {
			if (e.toString().contains("Timeout") || e.toString().contains("canceled"))
				logger.warn("Timer query timeout");
			else
				logger.error("Timer query error " + e);
		} finally {
			try {
				if (st!=null)
					st.close();
				if (con != null)
					con.close();
			} catch (Exception e) {
				logger.error("Error on close " + e);
				lastException=e;
			};
		}
		return atime;
	}	
	// CSMTXTABLE methods
	public boolean txInsert(String id,String txid,String type,String msg) {
		boolean success=true;
		Connection con=null;
		Statement stmt=null;
		lastException=null;
		if (ds==null) {
			lastException=new Exception("Datasource not initialized");
		} else {
			try {
				con = ds.getConnection();    // Connect using datasource username/pwd etc.

				stmt = con.createStatement();

				String sql = "INSERT INTO CSMTXTABLE (id,txid,type,adatetime,ndatetime,status,reason,msg) " +
						"VALUES ('" + id + "', '" + txid + "', '" + type + "', NOW(), NOW(),  '', '', '" + msg + "')";
				stmt.executeUpdate(sql);
			} catch (SQLException e) {
				success=false;
				if (!e.toString().contains("Unique index or primary key violation"))
					logger.error("Insert error " + e);
			} finally {
				try {
					if (stmt!=null)
						stmt.close();
					if (con != null)
						con.close();
				} catch (Exception e) {
					logger.error("Error on close " + e);
					lastException=e;
				};
			}
		}
    	return success;
	}
	
	public void txStatusUpdate(String txid,String status,String reason, Date aTime, float value, String type) {
		Connection con=null;
		Statement stmt=null;
		lastException=null;
		if (ds==null) {
			lastException=new Exception("Datasource not initialized");
		} else {
			try {
				con = ds.getConnection();    // Connect using datasource username/pwd etc.
				stmt = con.createStatement();

	    		java.sql.Timestamp sTime = new java.sql.Timestamp(aTime.getTime());

				String sql = "UPDATE CSMTXTABLE SET status='" + status + "', reason= '" + reason +
					"', adatetime= '" + sTime +"', ndatetime=NOW(), value="+value+" WHERE TXID='"+txid+"' AND TYPE='"+type+"'";
				stmt.executeUpdate(sql);
			} catch (SQLException e) {
				logger.error("Update tx status error " + e);
				lastException=e;
			} finally {
				try {
					if (stmt!=null)
						stmt.close();
					if (con != null)
						con.close();
				} catch (Exception e) {
					logger.error("Error on close " + e);
				};
			}
		}
	}
	
    public String txQueryKey(String id, String key) {
    	long startTime=System.currentTimeMillis();
    	Connection con = null;
		Statement st=null;
    	String data=null;
		lastException=null;
		if (ds==null) {
			lastException=new Exception("Datasource not initialized");
		} else {
			try {
				con = ds.getConnection();
				st = con.createStatement();
				String sql;
				if (!key.equalsIgnoreCase("ID")&&!key.equalsIgnoreCase("TXID"))
					key="ID";
				if (id.equals("%"))
					sql = "SELECT * FROM CSMTXTABLE WHERE txid IN (SELECT txid FROM CSMTXTABLE WHERE ndatetime = SELECT max(ndatetime) FROM CSMTXTABLE)";
				else if (id.contains("%"))
					sql = "SELECT TYPE,ID,TXID,NDATETIME,MSG FROM CSMTXTABLE WHERE " + key + " LIKE '" + id + "' LIMIT 100";
				else
					sql = "SELECT TYPE,ID,TXID,NDATETIME,MSG FROM CSMTXTABLE WHERE " + key + " = '" + id + "'";
				ResultSet rs = st.executeQuery(sql);
				data = "";
				int cnt = 0;
				while (rs.next()) {
					cnt++;
					data = data + "\n" +
							rs.getString("TYPE") + "\n" +
							rs.getString("ID") + "\n" +
							rs.getString("TXID") + "\n" +
							rs.getString("NDATETIME") + "\n" +
							rs.getString("MSG");
				}
				rs.close();
				if (cnt > 0) data = data + "\nCount " + cnt;
			} catch (SQLException e) {
				logger.error("Query error " + e);
			} finally {
				try {
					if (st!=null)
						st.close();
					if (con != null)
						con.close();
				} catch (Exception e) {
					logger.error("Error on close " + e);
					lastException=e;
				};
				long queryTime=System.currentTimeMillis()-startTime;
				if (queryTime>LONG_QUERY) logger.warn("Long tx key query " + queryTime + " ms, key="+key);
			}
		}
        return data;
    }
    
    public String getTXmsg(String id,String type) {
    	long startTime=System.currentTimeMillis();
    	Connection con = null;
		Statement st=null;
    	String data=null;
		lastException=null;
		if (ds==null) {
			lastException=new Exception("Datasource not initialized");
		} else {
			try {
				con = ds.getConnection();
				st = con.createStatement();
				String sql = "SELECT MSG FROM CSMTXTABLE WHERE TXID = '" + id + "' AND TYPE='"+type+"'";
				ResultSet rs = st.executeQuery(sql);
				if (rs.next()) {
					data = rs.getString("MSG");
				}
				rs.close();
			} catch (SQLException e) {
				logger.error("Query error " + e);
			} finally {
				try {
					if (st!=null)
						st.close();
					if (con != null)
						con.close();
				} catch (Exception e) {
					logger.error("Error on close " + e);
					lastException=e;
				};
				long queryTime=System.currentTimeMillis()-startTime;
				if (queryTime>LONG_QUERY) logger.warn("Long tx query " + queryTime + " ms");
			}
		}
        return data;
    }
    
    public TXstatus getTXstatus(String txid, String type) {
    	long startTime=System.currentTimeMillis();
    	Connection con=null;
		Statement st=null;
    	TXstatus data=null;
		lastException=null;
		if (ds==null) {
			lastException=new Exception("Datasource not initialized");
		} else {
			try {
				con = ds.getConnection();
				st = con.createStatement();
				String sql= "SELECT * FROM CSMTXTABLE WHERE TXID='"+txid+"' AND TYPE='"+type+"'";
				ResultSet rs = st.executeQuery(sql);
				if (rs.next()) {
					data = new TXstatus();
					data.status=rs.getString("STATUS");
					data.txid=rs.getString("TXID");
					data.reason=rs.getString("REASON");
					data.adatetime=rs.getString("ADATETIME");
					data.ndatetime=rs.getString("NDATETIME");
					data.value=rs.getFloat("VALUE");
				}
				rs.close();

				long queryTime=System.currentTimeMillis()-startTime;
				if (queryTime>LONG_QUERY) logger.warn("Long tx status query " + queryTime + " ms");
    		} catch (SQLException e) {
    			if (e.toString().contains("Timeout") || e.toString().contains("canceled"))
    				logger.warn("Status timer query timeout");
    			else
    				logger.error("Status timer query error " + e);
    		} catch (java.lang.IllegalStateException e) {
    			logger.error("Lock error " + e);
    		} finally {
				try {
					if (st!=null)
						st.close();
					if (con != null)
						con.close();
				} catch (Exception e) {
					logger.error("Error on close " + e);
					lastException=e;
				};
			}
    	}
        return data;
    }
    
    public String deleteTX(String id, String key) {
    	long startTime=System.currentTimeMillis();
    	Connection con = null;
		Statement st=null;
    	String data=null;
		lastException=null;
		if (ds==null) {
			lastException=new Exception("Datasource not initialized");
		} else {
			try {
				con = ds.getConnection();
				st = con.createStatement();
				String sql;
				// Truncate or delete CSMTXTABLE
				if (id.equals("%")) {
					sql = "TRUNCATE TABLE CSMTXTABLE";
					data = "Truncated "+txCount();
					st.executeUpdate(sql);
				} else {
					if (!key.equalsIgnoreCase("ID")&&!key.equalsIgnoreCase("TXID"))
						key="ID";
					sql = "DELETE FROM CSMTXTABLE WHERE "+key+" LIKE '" + id + "'";
					st.executeUpdate(sql);
					int cnt = st.getUpdateCount();
					data = "Deleted " + cnt + ", key="+key;
				}
				// Truncate or delete CSMSTATUSTABLE
				if (id.equals("%")) {
					sql = "TRUNCATE TABLE CSMtimer";
					st.executeUpdate(sql);
				} else {
					if (key.equalsIgnoreCase("TXID")) {
						sql = "DELETE FROM CSMSTATUSTABLE WHERE TXID LIKE '" + id + "'";
						st.executeUpdate(sql);
					}
				}
			} catch (SQLException e) {
				logger.error("Query error " + e);
				lastException=e;
			} finally {
				try {
					if (st!=null)
						st.close();
					if (con != null)
						con.close();
				} catch (Exception e) {
					logger.error("Error on close " + e);
				};
				long queryTime=System.currentTimeMillis()-startTime;
				if (queryTime>LONG_QUERY) logger.warn("Long tx key query " + queryTime + " ms, key="+key);
			}
		}
        return data;
    }
    
    public int txCount() {
    	int res=0;
    	Connection con=null;
		Statement st=null;
		lastException=null;
		if (ds==null) {
			lastException=new Exception("Datasource not initialized");
		} else
        try {
        	con = ds.getConnection();	// Connect using datasource username/pwd etc.

            st = con.createStatement();
            String sql="SELECT COUNT(*) FROM CSMTXTABLE";
            ResultSet rs = st.executeQuery(sql);

            if (rs.next()) {
            	res=rs.getInt(1);
            }
            rs.close();
        } catch (SQLException e) {
	       	logger.error("Count error "+e);
			lastException=e;
        } finally {
			try {
				if (st!=null)
					st.close();
				if (con != null)
					con.close();
			} catch (Exception e) {
				logger.error("Error on close " + e);
			};
        } 
        return res;
    }
 
    @PostConstruct
    public void initialize () {
        // Initialize here objects which will be used
        // by the session bean
    	Connection con=null;
        try { // Create the transaction table
        	InitialContext ctx= new InitialContext();
        	// Get data source (Wildfly naming subsystem <bindings>:
        	// <simple name="java:global/IPdatasource" value="jboss/datasources/ExampleDS"/>
			String datasourceStr=System.getProperty(datasourceProperty);
			if (datasourceStr==null) datasourceStr=(String)ctx.lookup("/global/"+datasourceProperty);	
        	ds=(DataSource)ctx.lookup(datasourceStr);// Example: java:jboss/datasources/ExampleDS
        	// String url = "jdbc:h2:mem:test";
        	// con = DriverManager.getConnection(url,"sa","sa");
        	con = ds.getConnection();	// Connect using datasource username/pwd etc.
        	logger.info("Connected.");
        	Statement stmt = null;
        	try {
	        	stmt = con.createStatement();
	            String sql = "CREATE TABLE CSMTXTABLE " +
	                         "(id VARCHAR(255), " +
	                         " txid VARCHAR(255), " + 
	                         " type VARCHAR(255), " + 
	                         " adatetime DATETIME, " +
	                         " ndatetime DATETIME, " +
	                         " status CHAR(4), " +
	                         " reason CHAR(4), " +
	                         " value FLOAT," +
	                         " msg VARCHAR(4096), " + 
	                         " PRIMARY KEY ( id ))"; 
	            stmt.executeUpdate(sql);
	            stmt.executeUpdate("CREATE INDEX csmtxdtIndex ON CSMTXTABLE (ndatetime)");
	            stmt.executeUpdate("CREATE UNIQUE INDEX csmtxidIndex ON CSMTXTABLE (txid,type)");
	            stmt.close();
	        	logger.info("Initialized - csmtxtable created.");
        	}catch (SQLException e) {
            	if (e.toString().contains("already exists")) {
    				logger.trace("Initialized - txtable already present.");
    			} else {
    				logger.error("Initialization error " + e);
    			}
            	if (stmt!=null) stmt.close();
        	}

        	String timerCacheSQLswitchStr=System.getProperty("CSMtimerSQL");
        	if (timerCacheSQLswitchStr!=null && (!timerCacheSQLswitchStr.equals("true")&&!timerCacheSQLswitchStr.equals("true"))) {
        		logger.warn("Bad CSMtimerSQL value "+timerCacheSQLswitchStr);
        	}
        	if (timerCacheSQLswitchStr==null||timerCacheSQLswitchStr.equals("false")) {
        		// If this property is set we use a memory Hashtable cache of open timer requests
        		// instead of the table created below. 
        		timerCache=new Hashtable<String, Timer>();
        	}
        	try { // Create table to hold open timer check requests for the timer task (created even if cache present)
	        	stmt = con.createStatement();
	            String sql = "CREATE TABLE CSMTIMER " +
	                         "(txid VARCHAR(255), " +
	                         " debtorbic VARCHAR(8), " +
	                         " adatetime DATETIME, " +
	                         " ndatetime DATETIME, " + 
	                         " msg VARCHAR(4096), " +
	                         " PRIMARY KEY ( txid ))"; 
	            stmt.executeUpdate(sql);
	            stmt.executeUpdate("CREATE INDEX csmtimerdtIndex ON CSMtimer (adatetime)");
	            stmt.close();
	        	logger.info("Initialized - csmtimer created.");
        	}catch (SQLException e) {
            	if (e.toString().contains("already exists")) {
    				logger.trace("Initialized - csmtimer already present.");
    			} else {
    				logger.error("Initialization error " + e);
    			}
            	if (stmt!=null) stmt.close();
        	}

        	try {	// Create bank status table - liquidity and last echo received time 
	        	stmt = con.createStatement();
	        	String sql = "CREATE TABLE CSMSTATUS " +
	                         "(bic VARCHAR(8), " +
	                         " liquidity DECIMAL, " + 
	                         " lastecho DATETIME, " +  
	                         " PRIMARY KEY ( bic ))"; 
	            stmt.executeUpdate(sql);
	            stmt.executeUpdate("INSERT INTO CSMSTATUS (bic,liquidity,lastecho) VALUES ('ANDLNL2A',1000000000,NOW())");
	            stmt.close();
	        	logger.info("Initialized - csmstatus created.");
        	}catch (SQLException e) {
            	if (e.toString().contains("already exists")) {
    				logger.trace("Initialized - csmstatus already present.");
    			} else {
    				logger.error("Initialization error " + e);
    			}
            	if (stmt!=null) stmt.close();
        	}

        	synchronized (this) {
        		if (liquidityCache==null) {
		        	// get bank info and fill liquidity cache 
        			liquidityCache=new Hashtable<String, Float>();
			    	DecimalFormat df = new DecimalFormat("#.##");
		        	BankStatus banks[]=getStatus("%");
		        	for (int i=0;i<banks.length;i++) {
		        		liquidityCache.put(banks[i].bic, banks[i].liquidity);
		        		logger.info("Liquidity set "+banks[i].bic+" "+df.format(banks[i].liquidity));
		        	}
        		}
        	}
       	
        } catch (SQLException e) {
        	logger.error("Initialization error " + e);
        } catch (NamingException ce) {
        	logger.error("Datasource not defined "+ce);
        } finally {
        	if (con!=null) {
				try {
					con.close();
				} catch (Exception e) {
					logger.error("Error on close " + e);
				}
			}
        }
    }
 
    @PreDestroy
    public void destroyBean() {
        // Free here resources acquired by the session bean
        logger.info("Destroyed.");
    } 
}

class TXstatus {
	String txid;
	String status;
	String reason;
	String adatetime;
	String ndatetime;
	float value;
}

class Timer {
	String txid;
	Date adatetime;
	String debtorBIC;
	String msg;
}

class BankStatus {
	String bic;
	Float liquidity;
	String lastecho;
}
