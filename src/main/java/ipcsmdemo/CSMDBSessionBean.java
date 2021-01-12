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
	private static Hashtable<String, Long> liquidityCache = null;
	
	static final String datasourceProperty="IPCSMdatasource";
	
	public Exception lastException=null;

	// CSMSTATUS methods
	public void setEchoStatus(String bic,Date echoTime) {
		Connection con=null;
		Statement stmt=null;
		lastException=null;
		if (ds==null) {
			lastException=new Exception("Datasource not initialized");
			logger.error("setEchoStatus "+lastException);
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
    public BankStatus[] getStatus(String bic) {
    	long startTime=System.currentTimeMillis();
    	Connection con = null;
		Statement st=null;
    	BankStatus[]data=null;
		lastException=null;
		if (ds==null) {
			lastException=new Exception("Datasource not initialized");
			logger.error("getEchoStatus "+lastException);
		} else {
			try {
				con = ds.getConnection();
				st = con.createStatement();
				String sql= "SELECT * FROM CSMSTATUS WHERE BIC LIKE '"+bic+"' ORDER BY BIC";
				ResultSet rs = st.executeQuery(sql);
		        java.util.ArrayList<BankStatus> dataList =
                        new java.util.ArrayList<BankStatus>();

				while (rs.next()) {
					BankStatus row = new BankStatus();
					row.bic=rs.getString("BIC");
					row.name=rs.getString("NAME");
					row.lastecho=rs.getString("LASTECHO");
					row.liquidity=rs.getLong("LIQUIDITY");
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
			logger.error("insertStatus "+lastException);
		} else {
    		java.sql.Timestamp sTime = new java.sql.Timestamp(echoTime.getTime());
			try {
				con = ds.getConnection();    // Connect using datasource username/pwd etc.

				stmt = con.createStatement();

				String sql = "INSERT INTO CSMSTATUS (bic,liquidity,lastecho) " +
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

	// Liquidity methods
	public void setLiquidity(String bic,long liquidity) {
		// Set liquidity in DB status table - only used if no cache
		Connection con=null;
		Statement stmt=null;
		lastException=null;
		if (ds==null) {
			lastException=new Exception("Datasource not initialized");
			logger.error("setliquidity "+lastException);
		} else {
			try {
				con = ds.getConnection();    // Connect using datasource username/pwd etc.

				stmt = con.createStatement();

				String sql = "UPDATE CSMSTATUS SET liquidity=" + liquidity + 
						" WHERE bic='" + bic + "'";
				stmt.executeUpdate(sql);
				stmt.close();
			} catch (SQLException e) {
				logger.error("Set liqudity error " + e);
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
	public long getLiquidity(String bic) {
		// Get liquidity from DB - last entry in liquidity table - only used if no cache
		Connection con=null;
		Statement stmt=null;
		lastException=null;
		long liquidity=0;
		if (ds==null) {
			lastException=new Exception("Datasource not initialized");
			logger.error("getliquidity "+lastException);
		} else {
			try {
				con = ds.getConnection();    // Connect using datasource username/pwd etc.

				stmt = con.createStatement();
				
				// Get liquidity from the status table
				String sql = "SELECT liquidity from CSMSTATUS WHERE bic='" + bic + "'";
				ResultSet rs = stmt.executeQuery(sql);
				if (rs.first()) {
					liquidity=rs.getLong("LIQUIDITY");
				} else{
					logger.warn("No liquidity "+bic);
				}

				sql = "SELECT sum(value) as liquidity from CSMLIQUIDITY WHERE bic='" + bic + "'";
				rs = stmt.executeQuery(sql);
				long newLiquidity=0;
				if (rs.first()) {
					newLiquidity=rs.getLong("LIQUIDITY");
					liquidity=liquidity+newLiquidity;
				}
				rs.close();
				stmt.close();
			} catch (SQLException e) {
				logger.error("Get liquidity error " + e);
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
		return liquidity;
	}
	public long getDeleteLiquidity(String bic) {
		// Get liquidity from DB - last entry in liquidity table - only used if no cache
		Connection con=null;
		Statement stmt=null;
		lastException=null;
		long liquidity=0;
		if (ds==null) {
			lastException=new Exception("Datasource not initialized");
			logger.error("getDeleteliquidity "+lastException);
		} else {
			try {
				con = ds.getConnection();    // Connect using datasource username/pwd etc.

				stmt = con.createStatement();
				
				// Get liquidity from the status table
				String sql = "SELECT liquidity from CSMSTATUS WHERE bic='" + bic + "'";
				ResultSet rs = stmt.executeQuery(sql);
				if (rs.first()) {
					liquidity=rs.getLong("LIQUIDITY");
				} else{
					logger.warn("No liquidity "+bic);
				}
				// Get recent liquidity changes and update liquidity value (will be copied to CSMSTATUS later)
				sql = "SELECT value,_ROWID_ from CSMLIQUIDITY WHERE bic='" + bic + "'";
				rs = stmt.executeQuery(sql);
				String rowIDs="";
				while (rs.next()) {
					long value=rs.getLong("VALUE");
					long rowid=rs.getLong("_ROWID_");
					liquidity=liquidity+value;
					//sql = "DELETE FROM CSMLIQUIDITY WHERE _ROWID_ = '" + rowid + "'";
					//dstmt.executeUpdate(sql);
					rowIDs=rowIDs+"'"+rowid+"',";
				}
				rs.close();
				stmt.close();
				if (!rowIDs.isEmpty()) {
					Statement dstmt=con.createStatement();
					sql = "DELETE FROM CSMLIQUIDITY WHERE _ROWID_ IN (" + rowIDs.substring(0,rowIDs.length()-1) + ")";
					dstmt.executeUpdate(sql);
					dstmt.close();
				}
			} catch (SQLException e) {
				logger.error("Get liquidity error " + e);
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
		return liquidity;
	}
	public boolean updateLiquidity(String bic,long value) {
		boolean success=true;
		lastException=null;
		Connection con=null;
		Statement stmt=null;
		lastException=null;

		if (ds==null) {
			lastException=new Exception("Datasource not initialized");
			logger.error("updateliquidity "+lastException);
		} else {
			if (liquidityCache==null) {
				try {
					con = ds.getConnection();    // Connect using datasource username/pwd etc.
					stmt = con.createStatement();
					// Get latest liquidity
					long liquidity=getLiquidity(bic);
					if (liquidity+value<0) {
						success=false;
					} else {
						// Insert a new liquidity
						java.sql.Timestamp sTime = new java.sql.Timestamp(new Date().getTime());
						liquidity=liquidity+value;
						String sql = "INSERT INTO CSMLIQUIDITY (bic,liquidity,value,timestamp) " +
								"VALUES ('" + bic + "', '" + liquidity + "', '" + value + "', '" + sTime + "')";
						stmt.executeUpdate(sql);			
					}
					stmt.close();
				} catch (SQLException e) {
					logger.error("Update liquidity error " + e);
					lastException=e;
					success=false;
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
			} else
			synchronized (liquidityCache) {
				Long liquidityObject=liquidityCache.get(bic);
				if (liquidityObject==null) {
					success=false;
				} else {
					if (((long)liquidityObject)+value<0) {
						success=false;
					} else {
						long liquidity=((long)liquidityObject)+value;
						liquidityCache.replace(bic,new Long(liquidity));
					}
				}			
			}
		}
    	return success;
	}
	public int saveLiquidityStatus() {
		// Save cache if the cache is in use - otherwise do nothing - called from the status timer task at intervals
		Connection con=null;
		Statement stmt=null;
		lastException=null;
		int changes=0;
		if (ds==null) {
			lastException=new Exception("Datasource not initialized");
			logger.error("saveliquidityStatus "+lastException);
		} else {
			if (liquidityCache==null)
			try {
				// Update liquidity from the liquidity table
				long liquidity=0;
				con = ds.getConnection();    // Connect using datasource username/pwd etc.
				stmt = con.createStatement();
				// Get latest liquidity
				String sql = "SELECT bic from CSMSTATUS";
				ResultSet rs = stmt.executeQuery(sql);
				while (rs.next()) {
					String bic=rs.getString("BIC");
					liquidity=getDeleteLiquidity(bic);
					setLiquidity(bic,liquidity);
				}
				rs.close();
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
			} else
			try {	// Update from cache
				con = ds.getConnection();    // Connect using datasource username/pwd etc.
				stmt = con.createStatement();

	    		Set<String> keys = liquidityCache.keySet();
	    		for(String bic: keys){
	    			long value=liquidityCache.get(bic);

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
	public void deleteLiquidity(String bic) {
		// Delete liquidity records from DB 
		Connection con=null;
		Statement stmt=null;
		lastException=null;

		if (ds==null) {
			lastException=new Exception("Datasource not initialized");
			logger.error("deleteliquidity "+lastException);
		} else {
			try {
				con = ds.getConnection();    // Connect using datasource username/pwd etc.

				stmt = con.createStatement();

				String sql = "DELETE CSMLIQUIDITY WHERE bic='" + bic + "'";
				stmt.executeUpdate(sql);
				stmt.close();
			} catch (SQLException e) {
				logger.error("Delete liquidity error " + e);
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
	public void deleteLiquidity() {
		// Delete liquidity for all BICs records from DB 
		Connection con=null;
		Statement stmt=null;
		lastException=null;

		if (ds==null) {
			lastException=new Exception("Datasource not initialized");
			logger.error("deleteliquidity "+lastException);
		} else {
			if (liquidityCache==null)
			try {
				// Delete liquidity from the liquidity table
				con = ds.getConnection();
				stmt = con.createStatement();
				// Get latest liquidity
				String sql = "SELECT bic from CSMSTATUS";
				ResultSet rs = stmt.executeQuery(sql);
				while (rs.next()) {
					String bic=rs.getString("BIC");
					deleteLiquidity(bic);
				}
				rs.close();
			} catch (SQLException e) {
				logger.error("Delete liquidity error " + e);
				lastException=e;
			} finally {
				try {
					if (stmt!=null)
						stmt.close();
					if (con != null)
						con.close();
				} catch (Exception e) {
					logger.error("Error on close " + e);
				}
			}
		}
	}

	// CSMTXTABLE methods
	public boolean txInsert(String id,String txid,String type,String msg) {
		boolean success=true;
		Connection con=null;
		Statement stmt=null;
		lastException=null;
		if (ds==null) {
			lastException=new Exception("Datasource not initialized");
			logger.error("txInsert "+lastException);
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
	
	public void txStatusUpdate(String txid,String status,String reason, Date aTime, long value, String type) {
		Connection con=null;
		Statement stmt=null;
		lastException=null;
		if (ds==null) {
			lastException=new Exception("Datasource not initialized");
			logger.error("txStatusUpdate "+lastException);
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
			logger.error("txQueryKey "+lastException);
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
			logger.error("getTXmsg "+lastException);
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
			logger.error("getTXstatus "+lastException);
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
					data.value=rs.getLong("VALUE");
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
			logger.error("deleteTX "+lastException);
		} else {
			try {
				con = ds.getConnection();
				st = con.createStatement();
				String sql;
				// Truncate or delete from CSMTXTABLE
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
			logger.error("txCount "+lastException);
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
	                         " value BIGINT," +
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

        	try {	// Create bank status table - liquidity total and last echo received time 
	        	stmt = con.createStatement();
	        	String sql = "CREATE TABLE CSMSTATUS " +
	                         "(bic VARCHAR(8), " +
	                         " name VARCHAR(255)," + // Name as used in queue names
	                         " liquidity BIGINT, " + // Euro cents
	                         " lastecho DATETIME, " +  
	                         " PRIMARY KEY ( bic ))"; 
	            stmt.executeUpdate(sql);
	            stmt.executeUpdate("INSERT INTO CSMSTATUS (bic,name,liquidity,lastecho) VALUES ('ANDLNL2A','anadolu',1000000000,NOW())");
	            stmt.executeUpdate("INSERT INTO CSMSTATUS (bic,name,liquidity,lastecho) VALUES ('MYBKNL2A','mybank',1000000000,NOW())");
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

        	try {	// Create liquidity change table - liquidity updates - not used if cache used instead
	        	stmt = con.createStatement();
	        	String sql = "CREATE TABLE CSMLIQUIDITY " +
	                         "(bic VARCHAR(8), " +
	                         " value BIGINT, " + // Euro cents
	                         " liquidity BIGINT, " + // Euro cents
	                         " timestamp DATETIME )";
	            stmt.executeUpdate(sql);
	            stmt.executeUpdate("CREATE INDEX csmLiquidityIndex ON CSMLIQUIDITY (bic)");
	            stmt.close();
	        	logger.info("Initialized - csmliqudity created.");
        	}catch (SQLException e) {
            	if (e.toString().contains("already exists")) {
    				logger.trace("Initialized - csmliquidity already present.");
    			} else {
    				logger.error("Initialization error " + e);
    			}
            	if (stmt!=null) stmt.close();
        	}

        	String liquidityCacheSQLswitchStr=System.getProperty("CSMliquiditySQL");
        	if (liquidityCacheSQLswitchStr==null)
        		liquidityCacheSQLswitchStr="true";
        	else
        	if (!liquidityCacheSQLswitchStr.equals("true")&&!liquidityCacheSQLswitchStr.equals("false")) {
        		logger.warn("Bad CSMliquiditySQL value "+liquidityCacheSQLswitchStr);
        	}
        	if (liquidityCacheSQLswitchStr.equals("false"))
        	synchronized (this) {
        		// If this property is set false we use a memory Hashtable cache of the liquidity instead of SQL updates.
        		// The status table is updated from the cache periodically via the time task. 
        		if (liquidityCache==null) {
		        	// get bank info and fill liquidity cache 
        			liquidityCache=new Hashtable<String, Long>();
		        	BankStatus banks[]=getStatus("%");
		        	for (int i=0;i<banks.length;i++) {
		        		liquidityCache.put(banks[i].bic, banks[i].liquidity);
		        		logger.info("Liquidity set "+banks[i].bic+" "+banks[i].liquidity);
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
	long value;
}

class BankStatus {
	String bic;
	long liquidity;
	String lastecho;
	String name;
}
