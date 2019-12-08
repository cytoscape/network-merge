package org.cytoscape.network.merge.internal.task;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkFactory;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.network.merge.internal.Merge;
import org.cytoscape.network.merge.internal.NetworkMerge;
import org.cytoscape.network.merge.internal.NodeSpec;
import org.cytoscape.network.merge.internal.model.AttributeMap;
import org.cytoscape.network.merge.internal.model.NetColumnMap;
import org.cytoscape.network.merge.internal.util.ColumnType;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.session.CyNetworkNaming;
import org.cytoscape.task.create.CreateNetworkViewTaskFactory;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.ContainsTunables;
import org.cytoscape.work.FinishStatus;
import org.cytoscape.work.ObservableTask;
import org.cytoscape.work.SynchronousTaskManager;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskManager;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.TaskObserver;
import org.cytoscape.work.Tunable;
import org.cytoscape.work.json.JSONResult;

public class NetworkMergeCommandTask extends AbstractTask implements ObservableTask {

	@ContainsTunables
//	@Tunable(description="Network", context="nogui", longDescription="The name of the resultant network", exampleStringValue=StringToModel.CY_NETWORK_EXAMPLE_STRING)
//	public CyNetwork network;

//	@Tunable (description="Namespace for table", context="nogui", longDescription="A syntactic prefix used to differentiate the table from others that may contain the same columns.")
//	public String namespace = "a OR b";

//	  @Tunable(
//				description = "NetworkList", context= Tunable.NOGUI_CONTEXT,
//				longDescription="The list of networks to merge", 
//				exampleStringValue = "net1, net2"
//		)
//	String networks = "<this and that>";
//
	  @Tunable(
				description = "Type of Merge", context= Tunable.NOGUI_CONTEXT,
				longDescription="Whether the networks are merged by union, intersection or difference", 
				exampleStringValue = "union"
		)
	  public String operation;

	@Tunable(
				description = "Name of the output network", context= Tunable.NOGUI_CONTEXT,
				longDescription="An override of the name for the network created by this merge", 
				exampleStringValue = "Merged Network"
		)
	  public String netName;

		@Tunable(
				description="Source Networks", context="nogui", 
				longDescription="The comma-delimited names of the input network", 
				exampleStringValue="a, b")
		public String sources;

		@Tunable(
				description = "Node Columns", context= Tunable.NOGUI_CONTEXT,
				longDescription="The comma-delimited, order-dependent list of node attributes needed to match across source networks", 
				exampleStringValue = "name , shared name"
		)
	public  String nodeKeys;

		@Tunable(
				description = "Node Merge Map", context= Tunable.NOGUI_CONTEXT,
				longDescription="A list of column merge records, each containing a list of column names corresponding to the network list", 
				exampleStringValue = "{name,display name, mergedName,String}, {COMMON, COMMON, COMMON, String}"
		)
	public  String nodeMergeMap;

		//nodeColumns="{display name,name,new display,String},{...}" #n+2
		//edgeColumns="{n1 col,n2 col,merged net col,merged net col type},{...}" #n+2

	@Tunable(
			description = "Edge Key Columns", context= Tunable.NOGUI_CONTEXT,
			longDescription="The list of edge attributes needed to match edges across source networks", 
			exampleStringValue = "interaction"
	)
	public  String edgeKeys;

	@Tunable(
			description = "Edge Merge Map", context= Tunable.NOGUI_CONTEXT,
			longDescription="A list of column merge records, each containing a list of column names from the edge table corresponding to the network list", 
			exampleStringValue = "{interaction, shared interaction, relation , String},{name, name, name, String},{EdgeBetweenness, EdgeBetweenness, Betweenness, String}"
	)
	public  String edgeMergeMap;

  
	@Tunable(
				description = "Retain Position", context= Tunable.NOGUI_CONTEXT,
				longDescription="If true, this will conserve x and y coordinates as properties in the result", 
				exampleStringValue = "true"
		)
	public boolean retainPosition;
	  
	
	@Tunable(
				description = "Nodes only", context= Tunable.NOGUI_CONTEXT,
				longDescription="If true, this will merge the node tables and dismiss edges.", 
				exampleStringValue = "false"
		)
	public boolean nodesOnly;
	  
	@Tunable(
				description = "Ignore Direction", context= Tunable.NOGUI_CONTEXT,
				longDescription="If true, this will combine edges disregarding the source and target designations.", 
				exampleStringValue = "true"
		)
	public boolean ignoreDirection;
	  
	//--------------------------------------------------------------------------------------
	private CyServiceRegistrar registrar;

	List<CyNetwork> networkList;
//	List<String> columnNames;
	
	public NetworkMergeCommandTask(CyServiceRegistrar reg) {	
		registrar = reg;
	}

	public static boolean verbose = Merge.verbose;
	@Override
	public void run(TaskMonitor taskMonitor) throws Exception {

		if (verbose) System.out.println("run6");

		CyNetworkNaming cyNetworkNaming = registrar.getService(CyNetworkNaming.class);
		CyNetworkFactory cyNetworkFactory = registrar.getService(CyNetworkFactory.class);
		CreateNetworkViewTaskFactory netViewCreator = registrar.getService(CreateNetworkViewTaskFactory.class);
		CyNetworkManager cnm = registrar.getService(CyNetworkManager.class);

		if (verbose) System.err.println("A: build network list ---------------- " );
		
		if (verbose) dumpInfo();
		if (netName == null)
			netName = operation + ": " + sources;
		netName = cyNetworkNaming.getSuggestedNetworkTitle(netName);
		if (verbose) System.out.println(netName);

		networkList = buildNetworkList(cnm);
		if (verbose) 
			for (CyNetwork n: networkList)
			System.out.println(n.getSUID() + " = " + getNetworkName(n));
		
		if (networkList.size() < 2)
		{
			if (verbose) 
				System.err.println("networkList.size() < 2" );
			return;
		}

		NetColumnMap matchingAttribute = 	buildMatchingAttribute();
		AttributeMap nodeAttributeMapping = buildNodeAttributeMapping();
		AttributeMap edgeAttributeMapping = buildEdgeAttributeMapping();

		final NetworkMerge.Operation op = NetworkMerge.lookup(operation);
		if (verbose) 
			System.err.println("Operation: " + op.toString() );
		
		
		boolean useDiference = op == NetworkMerge.Operation.DIFFERENCE; //TODO getDifference1Btn().isSelected();
		boolean inNetworkMerge = false; // getInNetMergeCkb().isSelected(); TODO

		//nodeColumns="{net1_name,net2_name,new_name,String},{...}" #n+2
		//edgeColumns="{n1 col,n2 col,merged net col,merged net col type},{...}" #n+2
		
//		String tgtType = "";
		final TaskManager<?, ?> taskMgr = registrar.getService(SynchronousTaskManager.class);
		final NetworkMergeTask nmTask = new NetworkMergeTask(cyNetworkFactory, cnm, netName, 
				matchingAttribute,	nodeAttributeMapping, edgeAttributeMapping, networkList, 
				op, useDiference, null, inNetworkMerge, true, netViewCreator);	

	      final TaskIterator taskIterator = new TaskIterator(nmTask);
	      taskIterator.append(new AbstractTask() {
	        public void run(TaskMonitor monitor) {
	          super.insertTasksAfterCurrentTask(taskIterator);
	        }
	      });
	     taskMgr.execute(taskIterator, new TaskObserver() {
	        public void taskFinished(ObservableTask t) {}
	        public void allFinished(FinishStatus status) { }
	 });	
	 
	}

	//---------------------------------------------------------------------
	private NetColumnMap buildMatchingAttribute() {
		
		NetColumnMap joinColumns = new NetColumnMap();
//		Set<String> columnNamesUnion = new HashSet<String>();
//		for (CyNetwork net : networkList)
//		{
//			matchingAttribute.addNetwork(net);
//			Collection<CyColumn> columns = net.getDefaultNodeTable().getColumns();
//			for (CyColumn column : columns)
//				columnNamesUnion.add(column.getName());
//		}
//		
//		System.out.println("columnNamesUnion {{ " + columnNamesUnion.toString());
		
		
		if (nodeKeys.trim().length() > 0)
		{
			List<String > columnNames = parseKeys(nodeKeys);
			if (networkList.size() != columnNames.size())
				System.err.println("size mismatch!!");

			int z = networkList.size();
			for (int i=0; i<z; i++)
			{
				String colname = columnNames.get(i);
				CyNetwork net = networkList.get(i);
				
//				System.out.print(net.getSUID() + "! " + colname);
				CyColumn column = net.getDefaultNodeTable().getColumn(colname);
				if (column != null)
				{
					joinColumns.put(net, column);
					if (verbose) System.out.println(" putting: " + column.getName() + " for " + net.getSUID());
				}
				else System.out.println(" not found:  ");
			}
		}
		return joinColumns;
	}
	//---------------------------------------------------------------------

	private List<String> parseKeys(String keys2) {
		List<String> strs = new ArrayList<String>();
		String[] keys = keys2.split(",");
		for (String key : keys)
			strs.add(key.trim());
		
		if (verbose) 
		{
			System.out.print("parseKeys: " );
			for (String s : keys) System.out.print(s + ", ");
			System.out.println(keys.length);
		}
		
		return strs;
	}
	
	//---------------------------------------------------------------------
	private List<CyNetwork> buildNetworkList(CyNetworkManager cnm) {
		if (verbose) System.out.println("A:  buildNetworkList ------------");
		Set<CyNetwork> sessionNets = cnm.getNetworkSet();
		List<CyNetwork> networkList = new ArrayList<CyNetwork>();
		List<String> sourceList = new ArrayList<String>();
		String[] strs = sources.split(",");
		for (String str : strs)
			sourceList.add(str.trim());
			
		if (verbose) 
		{
			System.out.print("sources: ");
			for (String str : sourceList)
				System.out.print(str + ", ");
			System.out.println("");
		}

		for (String src : sourceList)
		{
			CyNetwork sessionNet = find(sessionNets, src);
			String netName = getNetworkName(sessionNet);
			if (sessionNet != null)
			{
				networkList.add(sessionNet);
				if (verbose)  System.out.println(netName + "<-" + sessionNet.getSUID());
			}
			else if (verbose) System.out.println("net not found: " + netName);
		}
		if (verbose) System.out.println(networkList + " size: " + networkList.size());
		return networkList;
	}

	
	private CyNetwork find(Set<CyNetwork> sessionNets, String src) {
		for (CyNetwork net : sessionNets)
			if (getNetworkName(net).equals(src))
				return net;
		return null;
	}

	//---------------------------------------------------------------------
	//nodeColumns="{display name,name,new display,String},{...}" #n+2

	private AttributeMap buildNodeAttributeMapping() {
		
		MergeMap nodeMap = new MergeMap(nodeMergeMap, networkList);
		nodeMap.dump();
//		System.out.println("\n");
		AttributeMap nodeAttributeMapping = new AttributeMap();
		for (CyNetwork net : networkList)
		{
			CyTable nodeTable = net.getDefaultNodeTable();
			nodeAttributeMapping.addNetwork(net, nodeTable);
		}
//		System.out.println("--\n");
		for (int col = 0; col < nodeMap.columnsToMerge.size(); col++) 
		{
			ColumnMergeRecord rec  = nodeMap.columnsToMerge.get(col);
			for (int i=0; i<networkList.size(); i++)
			{
				CyNetwork net = networkList.get(i);
				System.out.println(getNetworkName(net) + ": " + rec.columnNames.get(net));
				nodeAttributeMapping.setOriginalAttribute(net, rec.columnNames.get(net), rec.outName);
			}
			nodeAttributeMapping.setMergedAttribute(col, rec.outName);
			nodeAttributeMapping.setMergedAttributeType(col, rec.outType);
	}
		return nodeAttributeMapping;
	}

	//---------------------------------------------------------------------
	private AttributeMap buildEdgeAttributeMapping() {
		MergeMap edgeMap = new MergeMap(edgeMergeMap, networkList);
		edgeMap.dump();
		System.out.println("\n");
		AttributeMap edgeAttributeMapping = new AttributeMap();
		for (CyNetwork net : networkList)
		{
			CyTable edgeTable = net.getDefaultEdgeTable();
			edgeAttributeMapping.addNetwork(net, edgeTable);
		}
		
		for (int col = 0; col < edgeMap.columnsToMerge.size(); col++) 
		{
			ColumnMergeRecord rec  = edgeMap.columnsToMerge.get(col);
			for (int i=0; i<networkList.size(); i++)
			{
				CyNetwork net = networkList.get(i);
				edgeAttributeMapping.setOriginalAttribute(net, rec.columnNames.get(net), rec.outName);
			}
			edgeAttributeMapping.setColumnMerge(col, rec);
		}
		return edgeAttributeMapping;
	}
	
	//---------------------------------------------------------------------
	public static String getNetworkName(CyNetwork net)
	{
		CyTable table = net.getDefaultNetworkTable();
		if (table == null)return "ERR1";
		CyRow row = table.getAllRows().get(0);
		if (row == null) return "ERR2";
		String netName = row.get("name", String.class);
		return netName;
		
	}
	
	//---------------------------------------------------------------------
	public static String getNodeName(NodeSpec spec)
	{
		return getNodeName(spec.getNet(), spec.getNode());
	}
	//---------------------------------------------------------------------
	public static String getNodeName(CyNetwork net, CyNode node)
	{
		if (net == null) return "No Net";
		if (node == null) return "No node";
		String ret = "" + node.getSUID();
		try
		{
			CyTable table = net.getDefaultNodeTable();
			CyRow row = table.getRow(node.getSUID());
			if (row != null)
			ret = row.get(CyNetwork.NAME, String.class) + " [" + node.getSUID() + "]";
		}
		catch(Exception e) { e.printStackTrace();}
		return ret;
	}
//	//---------------------------------------------------------------------
//	public static String getNodeName(CyNode node)
//	{
//		CyNetwork net = node.getNetworkPointer();
//		return net.getRow(node).get(CyNetwork.NAME, String.class);
//		
//	}
	//---------------------------------------------------------------------
	public static String getNetAndNodeName(CyNode node)
	{
		CyNetwork net = node.getNetworkPointer();
		return (net == null) ? "" + node.getSUID() : (getNetworkName(net) + ":" + getNodeName(net, node));
		
	}
	//---------------------------------------------------------------------
	public static String edgeName(CyNetwork net, CyEdge edge)
	{
		return getNodeName(net,edge.getSource()) + " -> " + getNodeName(net,edge.getTarget());
		
	}
	
	public static String getEdgeSet(CyNetwork netw, Set<CyEdge> edgeSet) {
		
		StringBuilder build = new StringBuilder( "[");
		for (CyEdge e : edgeSet)
			build.append(edgeName(netw, e)).append(", ");
		return build.toString() + "]";
	}

	//---------------------------------------------------------------------
	//---------------------------------------------------------------------
	private void dumpInfo() {
		System.out.println("sources: " + sources);
		System.out.println("keys: " + nodeKeys);
		System.out.println("nodeMergeMap: " + nodeMergeMap);
		System.out.println("edgeKeys: " + edgeKeys);
		System.out.println("edgeMergeMap: " + edgeMergeMap);
		System.out.println("operation: " + operation);
		System.out.println("retainPosition: " + (retainPosition ? "T" : "F"));
		System.out.println("nodesOnly: " + (nodesOnly ? "T" : "F"));
		System.out.println("ignoreDirection: " + (ignoreDirection ? "T" : "F") + "\n\n");
	}

	//---------------------------------------------------------------------
	@Override
	public List<Class<?>> getResultClasses() {
		return Arrays.asList(String.class, JSONResult.class);
	}	

	@Override
	public <R> R getResults(Class<? extends R> type) {
	    String response = "String Response";
		if (type.equals(String.class)) {
      return (R)response;
    } else if (type.equals(JSONResult.class)) {
			JSONResult res = () -> { return "{\"response\":\"the merge was a resounding success\"}";  };	
			return (R)res;
		}
    return null;
  }

	
	public void cancel() {
	}


}
