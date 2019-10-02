package org.cytoscape.network.merge.internal.task;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkFactory;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.network.merge.internal.NetworkMerge;
import org.cytoscape.network.merge.internal.model.AttributeMapping;
import org.cytoscape.network.merge.internal.model.AttributeMappingImpl;
import org.cytoscape.network.merge.internal.model.MatchingAttribute;
import org.cytoscape.network.merge.internal.model.MatchingAttributeImpl;
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
//	  
		@Tunable(
				description="Source Networks", context="nogui", 
				longDescription="The names of the input network", 
				exampleStringValue="a, b")
		public String sources;
//
//		@Tunable(
//				description="List Source Networks", context="nogui", 
//				longDescription="A list of the names of the input network", 
//				exampleStringValue=StringToModel.CY_NETWORK_EXAMPLE_STRING)
//		public List<String> sourceList;
//
//		@Tunable(
//				description="Array Source Networks", context="nogui", 
//				longDescription="An array of the names of the input network", 
//				exampleStringValue="[ \"a\", \"b\" ]")
//		public String[] sourceArray;
//

		@Tunable(
				description = "Node Columns", context= Tunable.NOGUI_CONTEXT,
				longDescription="The list of node attributes needed to match across source networks", 
				exampleStringValue = "name , shared name, Betweenness"
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
	List<String> columnNames;
	
	public NetworkMergeCommandTask(CyServiceRegistrar reg) {	
		registrar = reg;
	}

	public static boolean verbose = true;
	@Override
	public void run(TaskMonitor taskMonitor) throws Exception {

		if (verbose) System.out.println("run6");

		CyNetworkNaming cyNetworkNaming = registrar.getService(CyNetworkNaming.class);
		CyNetworkFactory cyNetworkFactory = registrar.getService(CyNetworkFactory.class);
		CreateNetworkViewTaskFactory netViewCreator = registrar.getService(CreateNetworkViewTaskFactory.class);
		CyNetworkManager cnm = registrar.getService(CyNetworkManager.class);

		
		if (verbose) dumpInfo();
		final String netName = cyNetworkNaming.getSuggestedNetworkTitle(operation + ": " + sources);

		networkList = buildNetworkList(cnm);
		if (networkList.size() < 2)
		{
			if (verbose) 
				System.err.println("networkList.size() < 2" );
			return;
		}

		columnNames = parseKeys(nodeKeys);
		
		MatchingAttribute matchingAttribute = buildMatchingAttribute();
	
		AttributeMapping nodeAttributeMapping = buildNodeAttributeMapping();
		AttributeMapping edgeAttributeMapping = buildEdgeAttributeMapping();
		final NetworkMerge.Operation op = NetworkMerge.lookup(operation);
		boolean useDiference = op == NetworkMerge.Operation.DIFFERENCE && false; //TODO getDifference1Btn().isSelected();
		boolean inNetworkMerge = false; // getInNetMergeCkb().isSelected(); TODO

		//nodeColumns="{display name,name,new display,String},{...}" #n+2
		//edgeColumns="{n1 col,n2 col,merged net col,merged net col type},{...}" #n+2
		
		
		String tgtType = "";
		final TaskManager<?, ?> taskMgr = registrar.getService(SynchronousTaskManager.class);
		final NetworkMergeTask nmTask = new NetworkMergeTask(cyNetworkFactory, cnm, netName, matchingAttribute,
				nodeAttributeMapping, edgeAttributeMapping, networkList, op, useDiference, null, 
				tgtType, inNetworkMerge, netViewCreator);	

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

	private MatchingAttribute buildMatchingAttribute() {
		
		MatchingAttribute matchingAttribute = new MatchingAttributeImpl();
		for (CyNetwork net : networkList)
			matchingAttribute.addNetwork(net);

		for (String col : columnNames)
		{
			boolean found = true;
			for (CyNetwork net : networkList)
			{
				CyColumn column = net.getDefaultNodeTable().getColumn(col);
				if (column == null)  found = false;
			}
			if (found)
			{
				for (CyNetwork net : networkList)
				{
					CyColumn column = net.getDefaultNodeTable().getColumn(col);
					matchingAttribute.putAttributeForMatching(net, column);
				}
				if (verbose) 
					System.out.println("Matched: " + col);
			}
		}
		return null;
	}

	private List<String> parseKeys(String keys2) {
		List<String> strs = new ArrayList<String>();
		String[] keys = keys2.split(",");
		for (String key : keys)
			strs.add(key.trim());
		return strs;
	}

	private List<CyNetwork> buildNetworkList(CyNetworkManager cnm) {
		if (verbose) System.out.println("buildNetworkList ");
		Set<CyNetwork> sessionNets = cnm.getNetworkSet();
		List<CyNetwork> networkList = new ArrayList<CyNetwork>();
		List<String> sourceList = new ArrayList<String>();
		String[] strs = sources.split(",");
		for (String str : strs)
			sourceList.add(str.trim());
			
		if (verbose) 
		{
			System.out.println("sources: ");
			for (String str : sourceList)
				System.out.println(str);
		}

		for (CyNetwork net : sessionNets)
		{
			String netName = getNetworkName(net);
			if (sourceList.contains(netName))
			{
				networkList.add(net);
				if (verbose) System.out.println("net found: " + netName);

			}
			else if (verbose) System.out.println("net not found: " + netName);
				
		}
		 if (verbose) System.out.println(networkList + "  size: " + networkList.size());
		return networkList;
	}

	String getNetworkName(CyNetwork net)
	{
		CyTable table = net.getDefaultNetworkTable();
		if (table == null)return "ERR1";
		CyRow row = table.getAllRows().get(0);
		if (row == null) return "ERR2";
		String netName = row.get("name", String.class);
		return netName;
		
	}
	//nodeColumns="{display name,name,new display,String},{...}" #n+2

	private AttributeMapping buildNodeAttributeMapping() {
		
		MergeMap nodeMap = new MergeMap(nodeMergeMap, networkList);
		nodeMap.dump();
		System.out.println("\n");
		AttributeMapping nodeAttributeMapping = new AttributeMappingImpl();
		for (CyNetwork net : networkList)
		{
			CyTable nodeTable = net.getDefaultNodeTable();
			nodeAttributeMapping.addNetwork(net, nodeTable);
		}
		System.out.println("--\n");
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

	private AttributeMapping buildEdgeAttributeMapping() {
		MergeMap edgeMap = new MergeMap(edgeMergeMap, networkList);
		edgeMap.dump();
		System.out.println("\n");
		AttributeMapping edgeAttributeMapping = new AttributeMappingImpl();
		for (CyNetwork net : networkList)
		{
			CyTable nodeTable = net.getDefaultEdgeTable();
			edgeAttributeMapping.addNetwork(net, nodeTable);
		}
		
		for (int col = 0; col < edgeMap.columnsToMerge.size(); col++) 
		{
			ColumnMergeRecord rec  = edgeMap.columnsToMerge.get(col);
			for (int i=0; i<networkList.size(); i++)
			{
				CyNetwork net = networkList.get(i);
				edgeAttributeMapping.setOriginalAttribute(net, rec.columnNames.get(net), rec.outName);
			}
			edgeAttributeMapping.setMergedAttribute(col, rec.outName);
			edgeAttributeMapping.setMergedAttributeType(col, rec.outType);
		}
		return edgeAttributeMapping;
	}
	

	class MergeMap 
	{
		List<CyNetwork> sourceNetworks;
		List<ColumnMergeRecord> columnsToMerge = new ArrayList<ColumnMergeRecord>();
		
		MergeMap(String s, List<CyNetwork> nets)
		{
			sourceNetworks = nets;
			int ptr = 0;
			while (ptr >= 0)
			{
				int start = s.indexOf('{', ptr);
				int end = s.indexOf('}', start+1);
				String chunk = s.substring(start+1, end);
				ColumnMergeRecord rec = new ColumnMergeRecord(chunk, nets);
				columnsToMerge.add(rec);
				ptr = s.indexOf(',', end+1);
			}
		}

		public void dump() {
			for (ColumnMergeRecord rec : columnsToMerge)
				rec.dump();
			
		}
	}
	
	class ColumnMergeRecord 
	{
		HashMap<CyNetwork, String> columnNames = new HashMap<CyNetwork, String>();
		String outName;
		ColumnType outType;
		
		ColumnMergeRecord(String str, List<CyNetwork> netw)
		{
			String[] names = str.split(",");
			int size = names.length;
			if (size != netw.size() + 2)
				throw new IllegalArgumentException("Wrong number of arguments, given size of network list");
			for (int i=0; i<size-2; i++)
				columnNames.put(netw.get(i), names[i]);
			outName = names[size-2];
			outType = ColumnType.STRING;
			
		}
		
		void dump()
		{
			System.out.println(outName + " # " + outType.toString());
			for (CyNetwork net : columnNames.keySet())
				System.out.println(getNetworkName(net) + ": " + columnNames.get(net));
			System.out.println("\n");
		}
	}
	
	
	
	
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
