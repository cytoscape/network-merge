package org.cytoscape.network.merge.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.network.merge.internal.NetworkMerge.Operation;
import org.cytoscape.network.merge.internal.conflict.AttributeConflictCollector;
import org.cytoscape.network.merge.internal.model.AttributeMap;
import org.cytoscape.network.merge.internal.model.NetColumnMap;
import org.cytoscape.network.merge.internal.model.NodeListList;
import org.cytoscape.network.merge.internal.model.NodeSpec;
import org.cytoscape.network.merge.internal.task.NetworkMergeCommandTask;
import org.cytoscape.network.merge.internal.util.AttributeValueMatcher;
import org.cytoscape.network.merge.internal.util.ColumnType;

/*
 *	An object responsible for the first pass of merging sets of nodes.
 *
 *
 *	It builds interim lists of nodes that match across networks, and
 *	those that have no counterpart.  These are used to compute union,
 *	intersection and difference sets, as well as a nodeNodeMap that 
 *	maps between original nodes and their equivalent in the target network.
 *
 *	Once the target nodes are determined, a pass is made to copy the
 *  attributes forward.  This can present conflicts and other type
 *  conversion problems.
 *  
 *  The NodeMerger's match function is used by the EdgeMerger in 
 *  the subsequent step.
 */

public class NodeMerger {
	protected final AttributeConflictCollector conflictCollector;
	private NodeListList matchList;
	private List<NodeSpec> unmatchedList;
	private final Map<CyNode, CyNode> nodeNodeMap = new HashMap<CyNode, CyNode>();		// original -> target
	private AttributeMap nodeAttributeMap;
	private CyNetwork targetNetwork;
	private NetColumnMap matchingAttribute;
	private AttributeValueMatcher attributeValueMatcher;
	private CyColumn countColumn;
	private CyColumn matchColumn;
	private List<CyNetwork> networks;
	
	public NodeMerger(final AttributeConflictCollector conflictCollector, AttributeMap nodeAttributeMapping) {
		this.conflictCollector = conflictCollector;
	}
	boolean verbose = true;

	public void mergeNodes(List<CyNetwork> sources, CyNetwork targetNetwork, Operation operation, AttributeMap nodeAttribute, 
			NetColumnMap matchingAttribute, AttributeValueMatcher attributeValueMatcher, CyColumn matchColumn, CyColumn countColumn) 
	{
		if (verbose) System.out.println("C:  build the list of node list that will merge------------" );
		
		this.nodeAttributeMap = nodeAttribute;
		this.matchingAttribute = matchingAttribute;
		this.attributeValueMatcher = attributeValueMatcher;
		this.targetNetwork = targetNetwork;
		this.matchColumn = matchColumn;
		this.countColumn = countColumn;
		this.networks = sources;
		matchList = new NodeListList();
		unmatchedList = new ArrayList<NodeSpec>();

		for (CyNetwork net : networks)		// build matchedList and unmatchedList
			for (CyNode node : net.getNodeList())
				match(new NodeSpec(net, node));
//		if (verbose)
//		{
//			matchList.dump();
//			System.out.println("Unmatched:");
//			for (NodeSpec n : unmatchedList)
//				System.out.println(n);
//		}
		
		if (verbose) System.out.println("D:  merge nodes in the matchedList ---------------------------" );
		// if not ALL nets have a node in the match, remove from intersection
		if (operation == Operation.INTERSECTION)
			for (int i = matchList.size()-1; i>=0; i--)
			{
				List<NodeSpec> nodes = matchList.get(i);
				 if (nodes.size() < networks.size())	
					matchList.remove(nodes);
			}
		
		if (operation == Operation.UNION || operation == Operation.INTERSECTION)		// for Union OR Intersection, add the matches to our target
		{
			for (List<NodeSpec> nodes : matchList)
				addNodeListToTarget(nodes);
		}

		if (operation == Operation.UNION)			//for Union, also add unmatched nodes to target network and nodeNodeMap
		{
			for (NodeSpec node : unmatchedList)
				addNodeToTarget(node);
		}
		
		if (operation == Operation.DIFFERENCE)			//for difference,  add ONLY nodes from first network that aren't matches
		{
			CyNetwork firstNet = networks.get(0);
			for (CyNode node : firstNet.getNodeList())
			{
				CyNode equiv = nodeNodeMap.get(node);
				boolean matched = equiv != null;
				if (!matched)
					addNodeToTarget(new NodeSpec(firstNet, node));
			}
		}	
		
		if (verbose) System.out.println("D:  NodeNodeMap has " + nodeNodeMap.size() + " has keys: " + nodeNodeMap.keySet());
		if (verbose) System.out.println("mergedNetwork.size = " + targetNetwork.getNodeCount());
			
	}
	//----------------------------------------------------------------
	public void addNodeToTarget(NodeSpec node)
	{
		List<NodeSpec> list = new ArrayList<NodeSpec>();
		list.add(node);
		addNodeListToTarget(list);
	}

	private void addNodeListToTarget(List<NodeSpec> nodes)
	{
		CyNode targetNode = targetNetwork.addNode();
		mergeMatchedNodes(nodes, targetNetwork, targetNode );
		for (NodeSpec node : nodes)
			nodeNodeMap.put(node.getNode(), targetNode);
	}
    
	//----------------------------------------------------------------
	public void mergeMatchedNodes(List<NodeSpec> matchedNodes, CyNetwork targetNetwork, CyNode targetNode) {

		if (matchedNodes == null || matchedNodes.isEmpty())			return;
		final int nattr = nodeAttributeMap.getSizeMergedAttributes();
		CyRow row = targetNetwork.getRow(targetNode);
		CyTable t = row.getTable();
		if (countColumn != null)
			row.set(countColumn.getName(), matchedNodes.size());
		if (matchColumn != null)
		{
			NodeSpec firstNode = matchedNodes.get(0);
			CyNetwork srcNetwork = firstNode.getNet();
			CyColumn col1 = matchingAttribute.get(srcNetwork);
			CyRow srcRow = srcNetwork.getRow(firstNode.getNode());
			String firstVal = srcRow.get(col1.getName(), String.class);
			row.set(matchColumn.getName(), firstVal);
		}

		for (int i = 0; i < nattr; i++) 
		{
			String attribute = nodeAttributeMap.getMergedAttribute(i);
			CyColumn targetColumn = t.getColumn(attribute);

			// build a node to column map
			Map<CyNode, CyColumn> nodeToColMap = new HashMap<CyNode, CyColumn>();
			for (NodeSpec spec : matchedNodes)
			{
				final CyTable table = nodeAttributeMap.getCyTable(spec.getNet());
				if (table == null) continue;

				final String attrName = nodeAttributeMap.getOriginalAttribute(spec.getNet(), i);
				if (attrName == null) continue;
				if (targetNode == null) { System.err.println("null target node");continue; }
				if (targetColumn == null) continue;
				if (attrName.equals(attribute))
					mergeAttribute(nodeToColMap, targetNode, targetColumn, null, targetNetwork);
				
				final CyColumn colum = (table == null) ? null : table.getColumn(attrName);
				for (NodeSpec node : matchedNodes)
					nodeToColMap.put(node.getNode(), colum);
			}
			mergeAttribute(nodeToColMap, targetNode, targetColumn, null, targetNetwork);
//			for (CyNetwork net : mapNetToNodes.map.keySet())
//			{
//				boolean isJoinColumn = matchingAttribute.contains(net, attribute);
//				if (isJoinColumn)
//					nodeMerger.mergeAttribute(nodeToColMap, targetNode, matchColumn, countColumn, targetNetwork);
//			}
		}
//		if (Merge.verbose) System.out.println("Node Merged");
	}	
	private void match(final NodeSpec spec) {
		
		for (List<NodeSpec> matches : matchList) 
		{
			for (NodeSpec node : matches)
				if (matchNode(spec, node))
				{
					matches.add(spec);
					return;
				}
		}
		for (int i = unmatchedList.size()-1; i >= 0; i--)
		{
			NodeSpec unmatched = unmatchedList.get(i);  
			if (matchNode(spec, unmatched))
			{
				List<NodeSpec>  matches = new ArrayList<NodeSpec>();
				matches.add(unmatched);
				matches.add(spec);
				matchList.add(matches);
				unmatchedList.remove(i);
				return;
			}
		}
		unmatchedList.add(spec);
	}
	
	private boolean matchNode(NodeSpec a, NodeSpec b)
	{
		return matchNode(a.getNet(), a.getNode(), b.getNet(), b.getNode());
	}

	protected boolean matchNode(final CyNetwork net1, final CyNode n1, final CyNetwork net2, final CyNode n2) {
		if (net1 == null || n1 == null || net2 == null || n2 == null)
			throw new NullPointerException();

		// it matches if the same node is sent twice, but they are nodes in different networks, so we shouldn't see this case
		if (n1 == n2)   return true;

		CyColumn col1 = matchingAttribute.get(net1);
		CyColumn col2 = matchingAttribute.get(net2);
		
		if (col1 == null || col2 == null)
			throw new IllegalArgumentException("Please specify the matching table column first");
		
		boolean result = attributeValueMatcher.matched(n1, col1, n2, col2);
//		if (result && verbose)
//			System.out.println((result? "MATCH " : "NOMATCH   ") + nodeName(net1, n1) + " " + net1.getSUID() + ": " + col1.getName() 
//				+ ", "  + nodeName(net2, n2) + " " + net2.getSUID() + ": " + col2.getName());
		return result; 
	}
	
	//===============================================================================================


	public void mergeAttribute(Map<CyNode, CyColumn> nodeColMap, CyNode node, CyColumn targetColumn, CyColumn countColumn, CyNetwork targetNet) 
	{
	
//		if (Merge.verbose) System.out.println("mergeAttribute " + (node == null ? "NULLNODE" : node.getSUID()) + " " + (targetColumn == null ? "NULLTARGET" : targetColumn.getName()));
		if (nodeColMap == null) 	return;
		if (node == null) 			return;
		if (targetColumn == null) 	return;
		if (targetNet == null) 		return;


//		if (Merge.verbose) System.out.println("NodeMerger.mergeAttribute: " + targetColumn.getName());

		for (CyNode source : nodeColMap.keySet()) {
//			Merge.dumpRow(source);
			final CyColumn fromColumn = nodeColMap.get(source);
//			if (Merge.verbose) System.out.println("merge: " + fromColumn.getName() + " " + node.getSUID() + " " + targetNet.getSUID() + " " + targetColumn.getName());
			merge(source, fromColumn, targetNet, node, targetColumn, countColumn);
		}
	}
	
	private void merge(CyNode from, CyColumn fromColumn, CyNetwork targetNet, CyNode target, CyColumn targetColumn, CyColumn countColumn) {
		
		if (from == null) 			return;
		if (target == null) 		return;
		if (from == fromColumn) 	return;
		if (target == targetColumn) return;

		final ColumnType targColType = ColumnType.getType(targetColumn);
		final ColumnType fromColType = ColumnType.getType(fromColumn);
		final CyRow targetRow = targetNet.getRow(target);
		final CyTable fromTable = fromColumn.getTable();
		final CyRow fromCyRow = fromTable.getRow(from.getSUID());

		if (targetRow == null) throw new NullPointerException("targetRow");
		if (fromCyRow == null) throw new NullPointerException("fromCyRow");
		
		if (fromColType == ColumnType.STRING) {
			Class c = fromColType.getType().getClass();
			String s = fromColumn.getName();
			try
			{
				final String fromValue = fromCyRow.get(s, String.class);
				final String o2 = targetRow.get(targetColumn.getName(), String.class);
				
//				System.out.println("mergeAttribute: " + fromValue + " - " + o2);
				if (o2 == null || o2.length() == 0)  // null or empty attribute
				{
					targetRow.set(targetColumn.getName(), fromValue);						
					if (countColumn != null) targetRow.set(countColumn.getName(), 1);	
				}
				else if (fromValue != null && fromValue.equals(o2)) { } // the same, do nothing
				else  if (conflictCollector != null)
						conflictCollector.addConflict(from, fromColumn, target, targetColumn);
			}
			catch (IllegalArgumentException ex)
			{
				if (verbose) System.out.println("IllegalArgumentException");
			}
		} else if (!targColType.isList()) 
		{ // simple type (Integer, Long, Double, Boolean)
			Object o1 = fromCyRow.get(fromColumn.getName(), fromColType.getType());
			if (fromColType != targColType) 
				o1 = targColType.castService(o1);
			if (o1 == null) return;
			Object o2 = targetRow.get(targetColumn.getName(), targColType.getType());
			if (o2 == null) 
			{
				targetRow.set(targetColumn.getName(), o1);		
				if (countColumn != null) targetRow.set(countColumn.getName(), 2);			
			}
			else if (o1.equals(o2)) {}
			else if (conflictCollector != null)
					conflictCollector.addConflict(from, fromColumn, target, targetColumn);
		} else { // toattr is list type
			// TODO: use a conflict handler to handle this part?
			ColumnType plainType = targColType.toPlain();

			List l2 = targetRow.getList(targetColumn.getName(), plainType.getType());
			if (l2 == null) {
				l2 = new ArrayList<Object>();
			}

			if (!fromColType.isList()) {
				// Simple data type
				Object o1 = fromCyRow.get(fromColumn.getName(), fromColType.getType());
				if (o1 != null) {
					if (plainType != fromColType) 
						o1 = plainType.castService(o1);
					if (!l2.contains(o1)) 
						l2.add(o1);
				if (!l2.isEmpty()) 
				{
					targetRow.set(targetColumn.getName(), l2);		// <-------
					if (countColumn != null) targetRow.set(countColumn.getName(), l2.size());		// <-------
				}
				}
			} else { // from list
				final ColumnType fromPlain = fromColType.toPlain();
				final List<?> list = fromCyRow.getList(fromColumn.getName(), fromPlain.getType());
				if(list == null)				return;
				
				for (final Object listValue:list) {
					if(listValue == null)		continue;
					
					final Object validValue;
					if (plainType != fromColType) 
						validValue = plainType.castService(listValue);
					else
						validValue = listValue;
					if (!l2.contains(validValue)) 
						l2.add(validValue);
				}
			}

			if(!l2.isEmpty()) 
			{
				targetRow.set(targetColumn.getName(), l2);		// <-------
				if (countColumn != null) targetRow.set(countColumn.getName(), l2.size());		// <-------
			}
		}
	}

	public String nodeName(CyNetwork net, CyNode target) {
		return NetworkMergeCommandTask.getNodeName(net, target);
	}

	public CyNode targetLookup(CyNode source) {
		return nodeNodeMap.get(source);
	}
}

