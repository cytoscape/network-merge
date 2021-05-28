package org.cytoscape.network.merge.internal.task;


import java.util.ArrayList;
import java.util.Arrays;
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
import org.cytoscape.network.merge.internal.NetworkMerge;
import org.cytoscape.network.merge.internal.NetworkMerge.Operation;
import org.cytoscape.network.merge.internal.conflict.AttributeConflictCollector;
import org.cytoscape.network.merge.internal.conflict.AttributeConflictCollectorImpl;
import org.cytoscape.network.merge.internal.model.AttributeMapping;
import org.cytoscape.network.merge.internal.model.AttributeMappingImpl;
import org.cytoscape.network.merge.internal.model.ColumnMergeRecord;
import org.cytoscape.network.merge.internal.model.MatchingAttribute;
import org.cytoscape.network.merge.internal.model.MatchingAttributeImpl;
import org.cytoscape.network.merge.internal.util.ParseUtils;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.session.CyNetworkNaming;
import org.cytoscape.task.create.CreateNetworkViewTaskFactory;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.view.vizmap.VisualMappingManager;
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
import org.cytoscape.work.util.ListSingleSelection;


public class NetworkMergeCommandTask extends AbstractTask implements ObservableTask {

	@ContainsTunables

	  @Tunable(
				description = "Type of Merge", context= Tunable.NOGUI_CONTEXT,
				longDescription="Whether the networks are merged by union, intersection or difference",
				exampleStringValue = "union"
		)
	  public ListSingleSelection<String> operation = new ListSingleSelection<String>("union","intersection","difference");

	@Tunable(
				description = "Name of the output network", context= Tunable.NOGUI_CONTEXT,
				longDescription="An override of the name for the network created by this merge",
				exampleStringValue = "Merged Network"
		)
	  public String netName;

		@Tunable(
				description="Source Networks", context="nogui",
				longDescription="The comma-delimited names of the input networks",
				exampleStringValue="a, b")
		public String sources;

		@Tunable(
				description = "Matching Node Columns", context= Tunable.NOGUI_CONTEXT,
				longDescription="The comma-delimited, order-dependent list of columns to match each node in the source networks",
				exampleStringValue = "name, shared name"
		)
	public  String nodeKeys = "name, name";

		@Tunable(
				description = "Node Merge Map", context= Tunable.NOGUI_CONTEXT,
				longDescription="A list of column merge records, each containing a list of column names corresponding to the network list of the form {column1, column2, merged column, type}",
				exampleStringValue = "{name, display name, mergedName, String}, {COMMON, COMMON, COMMON, String}"
		)
	public  String nodeMergeMap;

		//nodeColumns="{display name,name,new display,String},{...}" #n+2
		//edgeColumns="{n1 col,n2 col,merged net col,merged net col type},{...}" #n+2

	@Tunable(
			description = "Matching Edge Columns", context= Tunable.NOGUI_CONTEXT,
			longDescription="The comma-delimited, order-dependent list of columns to match each edge in the source netw  orks",
			exampleStringValue = "name, name"
	)
	public  String edgeKeys;

	@Tunable(
			description = "Edge Merge Map", context= Tunable.NOGUI_CONTEXT,
			longDescription="A list of column merge records, each containing a list of column names from the edge table corresponding to the network list of the form {column1, column2, merged column, type}",
			exampleStringValue = "{interaction, shared interaction, relation , String},{name, name, name, String},{EdgeBetweenness, EdgeBetweenness, Betweenness, Double}"
	)
	public  String edgeMergeMap;

	@Tunable(
			description = "Network Merge Map", context= Tunable.NOGUI_CONTEXT,
			longDescription="A list of column merge records, each containing a list of column names from the network table corresponding to the network list of the form {column1, column2, merged column, type}",
			exampleStringValue = "{_Annotations, _Annotations, _Annotations, List},{name, name, name, String}"
	)
	public  String networkMergeMap;

	@Tunable(
				description = "Nodes only", context= Tunable.NOGUI_CONTEXT,
				longDescription="If true, this will merge the node tables and dismiss edges.",
				exampleStringValue = "false"
		)
	public boolean nodesOnly = false;

	@Tunable(
				description = "Enable merging nodes/edges in the same network", context=Tunable.NOGUI_CONTEXT,
				longDescription="If true, nodes and edges with matching attributes in the same network will be merged",
				exampleStringValue = "true"
		)
	public boolean inNetworkMerge = true;

	//--------------------------------------------------------------------------------------
	private CyServiceRegistrar registrar;

	private boolean verbose = false;

	// List<CyNetwork> networkList;
	NetworkMergeTask nmTask;

	public NetworkMergeCommandTask(CyServiceRegistrar reg) {
		registrar = reg;
		operation.setSelectedValue("union");
	}

	@Override
	public void run(TaskMonitor taskMonitor) throws Exception {
		CyNetworkNaming cyNetworkNaming = registrar.getService(CyNetworkNaming.class);
		CyNetworkFactory cyNetworkFactory = registrar.getService(CyNetworkFactory.class);
		CreateNetworkViewTaskFactory netViewCreator = registrar.getService(CreateNetworkViewTaskFactory.class);
		CyNetworkManager cnm = registrar.getService(CyNetworkManager.class);
		CyNetworkViewManager nvm = registrar.getService(CyNetworkViewManager.class);
		VisualMappingManager vmm = registrar.getService(VisualMappingManager.class);

		if (verbose) System.err.println("A: build network list ---------------- " );

		if (verbose) dumpInfo();
		if (netName == null)
			netName = operation.getSelectedValue() + ": " + sources;
		netName = cyNetworkNaming.getSuggestedNetworkTitle(netName);
		if (verbose) System.out.println(netName);

		List<CyNetwork> networkList = buildNetworkList(cnm);
		if (verbose)
			for (CyNetwork n: networkList)
			System.out.println(n.getSUID() + " = " + getNetworkName(n));

		if (networkList.size() < 2)
		{
			if (verbose)
				System.err.println("networkList.size() < 2" );
			return;
		}

		MatchingAttribute matchingAttribute = 	buildMatchingAttribute(networkList);
		AttributeMapping nodeAttributeMapping = buildNodeAttributeMapping(networkList, nodeMergeMap);
		AttributeMapping edgeAttributeMapping = buildEdgeAttributeMapping(networkList, edgeMergeMap);
		AttributeMapping networkAttributeMapping = buildNetworkAttributeMapping(networkList, networkMergeMap);

		NetworkMerge.Operation op = Operation.UNION;
		if (operation.getSelectedValue().equals("union"))
			op = Operation.UNION;
		else if (operation.getSelectedValue().equals("intersection"))
			op = Operation.INTERSECTION;
		else if (operation.getSelectedValue().equals("difference"))
			op = Operation.DIFFERENCE;

		if (verbose)
			System.err.println("Operation: " + op.toString() );


		boolean useDiference = op == Operation.DIFFERENCE; //TODO getDifference1Btn().isSelected();
		final AttributeConflictCollector conflictCollector = new AttributeConflictCollectorImpl();

		nmTask = new NetworkMergeTask(registrar, netName,
				matchingAttribute,	nodeAttributeMapping, edgeAttributeMapping, networkAttributeMapping,
				networkList, op, useDiference, conflictCollector, inNetworkMerge, nodesOnly);

		TaskManager<?,?> tm = registrar.getService(SynchronousTaskManager.class);
		tm.execute(new TaskIterator(nmTask));
	}

	//---------------------------------------------------------------------
	private MatchingAttribute buildMatchingAttribute(List<CyNetwork> networkList) {

		MatchingAttribute joinColumns = new MatchingAttributeImpl();

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
					joinColumns.putAttributeForMatching(net, column);
					if (verbose) System.out.println(" putting: " + column.getName() + " for " + net);
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
	private AttributeMapping buildAttributeMapping(AttributeMapping mapping, List<CyNetwork> networkList, String mergeString) {
		List<ColumnMergeRecord> mergeColumns = ParseUtils.getMergeMap(mergeString, networkList);
		if (mergeColumns == null) return mapping;
		for (ColumnMergeRecord rec: mergeColumns)
		{
			String mergedAttribute = rec.outName;
			int mergedAttributeIndex = mapping.getMergedAttributeIndex(mergedAttribute);
			if (mergedAttributeIndex == -1) {
				// New attribute
				mapping.addAttributes(rec.columnNames, mergedAttribute);
			} else {
				for (CyNetwork net : networkList)
					mapping.setOriginalAttribute(net, rec.columnNames.get(net), mergedAttributeIndex);
				mapping.setMergedAttributeType(mergedAttributeIndex, rec.outType);
			}
		}
		return mapping;
	}

	private AttributeMapping buildNodeAttributeMapping(List<CyNetwork> networkList, String mergeString) {
		AttributeMapping nodeAttributeMapping = new AttributeMappingImpl();
		for (CyNetwork net : networkList)
		{
			CyTable nodeTable = net.getDefaultNodeTable();
			nodeAttributeMapping.addNetwork(net, nodeTable);
		}

		return buildAttributeMapping(nodeAttributeMapping, networkList, mergeString);
	}

	//---------------------------------------------------------------------
	private AttributeMapping buildNetworkAttributeMapping(List<CyNetwork> networkList, String mergeString) {
		AttributeMapping networkAttributeMapping = new AttributeMappingImpl();
		for (CyNetwork net : networkList)
		{
			CyTable netTable = net.getDefaultNetworkTable();
			networkAttributeMapping.addNetwork(net, netTable);
		}

		return buildAttributeMapping(networkAttributeMapping, networkList, mergeString);
	}

	//---------------------------------------------------------------------
	private AttributeMapping buildEdgeAttributeMapping(List<CyNetwork> networkList, String mergeString) {
		AttributeMapping edgeAttributeMapping = new AttributeMappingImpl();
		for (CyNetwork net : networkList)
		{
			CyTable edgeTable = net.getDefaultEdgeTable();
			edgeAttributeMapping.addNetwork(net, edgeTable);
		}

		return buildAttributeMapping(edgeAttributeMapping, networkList, mergeString);
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
		System.out.println("operation: " + operation.getSelectedValue());
		System.out.println("nodesOnly: " + (nodesOnly ? "T" : "F"));
	}

	//---------------------------------------------------------------------
	@Override
	public List<Class<?>> getResultClasses() {
		return Arrays.asList(String.class, CyNetwork.class, JSONResult.class);
	}

	@Override
	public <R> R getResults(Class<? extends R> type) {
		if (type.equals(String.class)) {
      return (R)nmTask.getResults(String.class);
    } else if (type.equals(JSONResult.class)) {
      return (R)nmTask.getResults(JSONResult.class);
		} else if (type.equals(CyNetwork.class)) {
      return (R)nmTask.getResults(CyNetwork.class);
		}
    return null;
  }

	public void cancel() {
	}


}
