package org.gusdb.wdk.model.implementation;

import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.QueryInstance;
import org.gusdb.wdk.model.ResultList;
import org.gusdb.wdk.model.ResultFactory;
import org.gusdb.wdk.model.Column;
import org.gusdb.wdk.model.RDBMSPlatformI;

import org.gusdb.wdk.model.process.WdkProcessClient;

import java.rmi.RemoteException;
import javax.xml.rpc.ServiceException;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.net.URL;
import java.net.MalformedURLException;

import java.util.Map;
import java.util.Set;
import java.util.Iterator;

public class WSQueryInstance extends QueryInstance  {

    public WSQueryInstance (WSQuery query) {
        super(query);
    }

    public String getLowLevelQuery() throws WdkModelException {
	return null;
    }

    protected ResultList getNonpersistentResult() throws WdkModelException {
	WSQuery wsQuery = (WSQuery)query;

	try {
	    WdkProcessClient client = new WdkProcessClient(getServiceUrl());

	    Map valMap = getValuesMap();
	    Set keys = valMap.keySet();
	    String[] paramNames = new String[keys.size()];
	    String[] paramVals = new String[keys.size()];
	    Iterator iter = keys.iterator();
	    int i=0;
	    while (iter.hasNext()) {
		String key = (String)iter.next();
		paramNames[i] = key;
		paramVals[i] = (String)valMap.get(key);
		System.err.println(paramNames[i] + ": " + paramVals[i]);
		i++;
	    }


	    Column[] columns = query.getColumns();
	    String[] columnNames = new String[columns.length];
	    i=0;
	    for (Column column : columns) {
		columnNames[i] = column.getName();
		System.err.println(columnNames[i]);
		i++;
	    }
	
	    System.err.println("WSQI invoking " + wsQuery.getProcessName());
	    String[][] result = client.invoke(wsQuery.getProcessName(), 
					      paramNames, 
					      paramVals, 
					      columnNames);
	    return new WSResultList(this, result);

	} catch (RemoteException e) {
	    throw new WdkModelException(e);
	} catch (ServiceException e) {
	    throw new WdkModelException(e);
	}
    }

    protected void writeResultToTable(String resultTableName, 
            ResultFactory rf) throws WdkModelException {

	RDBMSPlatformI platform = rf.getRDBMSPlatform();
	DataSource dataSource = platform.getDataSource();

	Column[] columns = query.getColumns();
	StringBuffer createSqlB = new StringBuffer("create table " +
						   resultTableName + "(");

	for (Column column : columns) {
	    createSqlB.append(column.getName() + " varchar(" + 
		       column.getWidth() + "),");

	}

	String createSql = 
	    createSqlB.substring(0, createSqlB.length()-1);  // lose last comma
	createSql += ")";

	String insertSql = "insert into " + resultTableName + " values (";

	ResultList resultList = getNonpersistentResult();

	try {
	
	    SqlUtils.execute(dataSource, createSql);

	    platform.addIndexColumn(dataSource, resultTableName);
	    
	    while(resultList.next()) {
		StringBuffer insertSqlB = new StringBuffer(insertSql);
		for (Column column : columns) {
		    String val = 
			(String)resultList.getValueFromResult(column.getName());
		    insertSqlB.append(val + ",");
		}
		String s = 
		    insertSqlB.substring(0, insertSqlB.length()-1) + ")"; 
		SqlUtils.execute(dataSource, s);
	    }

	} catch (SQLException e) {
	    throw new WdkModelException(e);
	}
    }

    private URL getServiceUrl() throws WdkModelException {
	try {
	    return new URL(((WSQuery)query).getWebServiceUrl());
	} catch (MalformedURLException e) {
	    throw new WdkModelException(e);
	}
     }

}
