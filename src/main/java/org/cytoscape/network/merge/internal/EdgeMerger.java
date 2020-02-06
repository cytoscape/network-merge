package org.cytoscape.network.merge.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyIdentifiable;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.network.merge.internal.NetworkMerge.Operation;
import org.cytoscape.network.merge.internal.conflict.AttributeConflictCollector;
import org.cytoscape.network.merge.internal.model.AttributeMap;
import org.cytoscape.network.merge.internal.task.NetworkMergeCommandTask;
import org.cytoscape.network.merge.internal.util.ColumnType;

public class EdgeMerger  {
	protected final AttributeConflictCollector conflictCollector;
	NodeMerger nodeMerger;
	final CyNetwork targetNetwork;
	final AttributeMap edgeAttributeMapping;
	
	public EdgeMerger(CyNetwork targetNetwork, NodeMerger nodeMerger, final AttributeConflictCollector conflictCollector,final AttributeMap edgeAttributeMapping) 
	{
		this.targetNetwork = targetNetwork;
		this.nodeMerger = nodeMerger;
		this.conflictCollector = conflictCollector;
		this.edgeAttributeMapping = edgeAttributeMapping;
	}
	//===============================================================================================
boolean verbose = false;

	List<EdgeSpec> unmatchedEdges = new ArrayList<EdgeSpec>();
	List<List<EdgeSpec>> matchedEdges = new ArrayList<List<EdgeSpec>>();
	/**
	*
	*	mergeEdges starts with all edges from source networks
	*	It makes EdgeSpecs and edgeMatch searches existing specs while tracking matched vs. unmatched
	*	Then, process the lists of matched and unmatched edges into the target network, depending on Operation
 	 */
	public void mergeEdges(List<CyNetwork> networks, CyNetwork targetNetwork, Operation operation, NodeMerger nodeMerger, AttributeMap edgeAttributeMapping	) {

// match edges
		if (verbose) System.err.println(" F  ----------EDGE MERGE-----------------");
//		System.err.print("matchedEdgeList: " );

		// create matchedEdges and unmatchedEdges lists
		for (CyNetwork net : networks)
		{
			if (verbose) System.out.println("adding edges from " + NetworkMergeCommandTask.getNetworkName(net));
			for (CyEdge edge : net.getEdgeList())
				processEdge(new EdgeSpec(net,edge));
			if (verbose) System.out.println(matchedEdges.size() + " / " + unmatchedEdges.size());
		}
		
		// dump
		if (verbose) 
		{
			for (List<EdgeSpec> match : matchedEdges)
			{
				for (EdgeSpec e : match)
					System.out.print(e.toString() + ", ");
				System.out.println();
			}
			for (EdgeSpec e : unmatchedEdges)
			{
				System.out.println(e.toString());
			}
		}
		
		
		// if its the intersection, there must be an edge for every network
		if (operation == Operation.INTERSECTION) 
		{
			for (int i = matchedEdges.size()-1; i >= 0; i--)
				if (matchedEdges.get(i).size() < networks.size())
					matchedEdges.remove(i);
		} 
 
		
		// if it's the difference, add edges for each unmatched edge from the first network
		if (operation == Operation.DIFFERENCE) 
		{
			final CyNetwork net1 = networks.get(0);
			for (CyEdge edge : net1.getEdgeList())
			{
				EdgeSpec e = new EdgeSpec(net1, edge);
				for (List<EdgeSpec> specs : matchedEdges)
					if (!edgeMatch(specs.get(0), e ))	
						addEdgeToTarget(nodeMerger, new EdgeSpec(net1, edge));
			}
		}
		else		// union and intersection both include matchedEdges
		{
			for (List<EdgeSpec> edges: matchedEdges)
			{
				if (edges == null || edges.isEmpty()) return;
				EdgeSpec edge = edges.get(0);
				CyNode targSrc = nodeMerger.targetLookup(edge.getSource());
				CyNode targTarg = nodeMerger.targetLookup(edge.getTarget());
				
				CyEdge newEdge = targetNetwork.addEdge(targSrc, targTarg, edge.isDirected());
				mergeEdge(edges, newEdge, targetNetwork);
			}
			
			// for union operation, we want unmatched edges too
			if (operation == Operation.UNION) 
			{
				for (EdgeSpec unmatched: unmatchedEdges)
					addEdgeToTarget(nodeMerger, unmatched);
			}	
		}

	}
	//----------------------------------------------------------------------------
	/*  compare an edge to our matched and unmatched lists, adding it to the 
	 * 	appropriate list.  When you add a match to an unmatched edge, delete it from unmatchedEdges
	 * 	
	 */
	private void processEdge(EdgeSpec edge) {

		// first, inspect all edges in all known matches, add to match list if found
		for (List<EdgeSpec> matches : matchedEdges)
		{
			for (EdgeSpec spec : matches)
				if (edgeMatch(spec, edge))
				{
					matches.add(edge);
					return;
				}
		}
		// next, search backwards thru unmatchedEdges, so we can delete entries as they match
		for (int i = unmatchedEdges.size()-1; i >= 0; i--)
		{
			EdgeSpec unmatched = unmatchedEdges.get(i);
			if (edgeMatch(unmatched, edge))
			{
				List<EdgeSpec> matches = new ArrayList<EdgeSpec>();
				matches.add(edge);
				matches.add(unmatched);
				matchedEdges.add(matches);
				unmatchedEdges.remove(i);
				if (verbose) System.out.println("adding edge match");
				return;
			}
		}
		unmatchedEdges.add(edge);
	}


	//----------------------------------------------------------------------------

	private String edgeName(EdgeSpec target) {
		return nodeMerger.nodeName(target.net, target.getSource()) + " -> "+ nodeMerger.nodeName(target.net, target.getTarget());
	}

	private boolean edgeMatch(EdgeSpec test, EdgeSpec e) {
		
		boolean sourceMatches = nodeMerger.matchNode(test.net, test.getSource(),e.net, e.getSource());
		boolean targetMatches = nodeMerger.matchNode(test.net, test.getTarget(), e.net, e.getTarget());
		boolean fullMatch =  sourceMatches && targetMatches;
		if (fullMatch || test.isDirected())  return fullMatch;
		
		// undirected graphs can also match in opposite order
		sourceMatches = nodeMerger.matchNode(test.net, test.getSource(), e.net, e.getTarget());
		targetMatches = nodeMerger.matchNode(test.net, test.getTarget(), e.net, e.getSource());
		return sourceMatches && targetMatches;
	}
//
	//----------------------------------------------------------------------------

	private void addEdgeToTarget(NodeMerger nodeMerger, EdgeSpec edge) {
		
		CyNode  targSrc = nodeMerger.targetLookup(edge.getSource());
		CyNode targTarg = nodeMerger.targetLookup(edge.getTarget());
		if (targSrc == null)  { if (verbose)  System.err.println("targSrc == null");    return; 	};
		if (targTarg == null)  { if (verbose)  System.err.println("targTarg == null");   return; 	};
		
		CyEdge newEdge = targetNetwork.addEdge(targSrc, targTarg, edge.isDirected());
		List<EdgeSpec> list = new ArrayList<EdgeSpec>();
		list.add(edge);
		mergeEdge(list, newEdge, targetNetwork);
		if (verbose) 
			System.out.println(edgeName(edge));
	}

	//----------------------------------------------------------------
	private void mergeEdge(final List<EdgeSpec> edges, CyEdge newEdge, CyNetwork newNetwork) 
	{
		if (edges == null || edges.isEmpty() || newEdge == null) 		throw new IllegalArgumentException();

		final int nattr = edgeAttributeMapping.getSizeMergedAttributes();
		CyTable t = newNetwork.getRow(newEdge).getTable();

		for (int i = 0; i < nattr; i++) {
			String str = edgeAttributeMapping.getMergedAttribute(i);
			System.out.println("Matching " + str);
			CyColumn attr_merged = t.getColumn(str);
			if (attr_merged == null) continue;
				// merge
			Map<EdgeSpec, CyColumn> edgeToColMap = new HashMap<EdgeSpec, CyColumn>();
			for (CyNetwork net : edgeAttributeMapping.getNetworkSet()) 
			{
				final String attrName = edgeAttributeMapping.getOriginalAttribute(net, i);
				final CyTable table = edgeAttributeMapping.getCyTable(net);
				if (attrName != null) 
				{	
					CyColumn col = table.getColumn(attrName);
					for (EdgeSpec ed : edges) 
							edgeToColMap.put(ed, col);
					mergeAttribute(edgeToColMap, newEdge, attr_merged, newNetwork);
				}
				
			}
		}
	}
	//----------------------------------------------------------------------------
	private <CyEdge extends CyIdentifiable> void mergeAttribute(final Map<EdgeSpec, CyColumn> edgeColumnMap, final CyEdge targetEdge, final CyColumn targetColumn,
			final CyNetwork network) {
		if (edgeColumnMap == null) 	throw new IllegalArgumentException("edgeColumnMap cannot be null.");
		if (targetEdge == null) 	throw new IllegalArgumentException("targetEdge cannot be null.");
		if (targetColumn == null) 	throw new IllegalArgumentException("targetColumn cannot be null.");
		if (network == null) 		throw new IllegalArgumentException("network cannot be null.");

		final CyRow cyRow = network.getRow(targetEdge);
		final ColumnType colType = ColumnType.getType(targetColumn);

		System.out.println("Merging " + targetColumn.getName());

		
		for (EdgeSpec from : edgeColumnMap.keySet()) {
//			final CyEdge from = entryGOAttr.getKey();
			final CyColumn fromColumn = edgeColumnMap.get(from);
			final CyTable fromTable = fromColumn.getTable();
			final CyRow fromCyRow = fromTable.getRow(from.edge.getSUID());
			final ColumnType fromColType = ColumnType.getType(fromColumn);

			if (colType == ColumnType.STRING) {
				String fromValue = "";
				String o2 = "";
				
				try
				{
					fromValue = fromCyRow.get(fromColumn.getName(), String.class);
					o2 = cyRow.get(targetColumn.getName(), String.class);
				
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
				if (o2 == null || o2.length() == 0) { // null or empty attribute
					cyRow.set(targetColumn.getName(), fromValue);
				} else if (fromValue != null && fromValue.equals(o2)) { // TODO: necessary?
					// the same, do nothing
				} else { // attribute conflict
					// add to conflict collector
					if (conflictCollector != null)
						conflictCollector.addConflict(from.edge, fromColumn, targetEdge, targetColumn);
				}
			} else if (!colType.isList()) { // simple type (Integer, Long,
											// Double, Boolean)
				Object o1 = fromCyRow.get(fromColumn.getName(), fromColType.getType());
				if (fromColType != colType) {
					o1 = colType.castService(o1);
				}

				Object o2 = cyRow.get(targetColumn.getName(), colType.getType());
				if (o2 == null) {
					cyRow.set(targetColumn.getName(), o1);
					// continue;
				} else if (o1.equals(o2)) {
					// continue; // the same, do nothing
				} else { // attribute conflict

					// add to conflict collector
					if (conflictCollector != null)
						conflictCollector.addConflict(from.edge, fromColumn, targetEdge, targetColumn);
					// continue;
				}
			} else { // toattr is list type
				// TODO: use a conflict handler to handle this part?
				ColumnType plainType = colType.toPlain();

				List l2 = cyRow.getList(targetColumn.getName(), plainType.getType());
				if (l2 == null) {
					l2 = new ArrayList<Object>();
				}

				if (!fromColType.isList()) {
					// Simple data type
					Object o1 = fromCyRow.get(fromColumn.getName(), fromColType.getType());
					if (o1 != null) {
						if (plainType != fromColType) {
							o1 = plainType.castService(o1);
						}

						if (!l2.contains(o1)) {
							l2.add(o1);
						}

						if (!l2.isEmpty()) {
							cyRow.set(targetColumn.getName(), l2);
						}
					}
				} else { // from list
					final ColumnType fromPlain = fromColType.toPlain();
					final List<?> list = fromCyRow.getList(fromColumn.getName(), fromPlain.getType());
					if(list == null)
						continue;
					
					for (final Object listValue:list) {
						if(listValue == null)
							continue;
						
						final Object validValue;
						if (plainType != fromColType) {
							validValue = plainType.castService(listValue);
						} else {
							validValue = listValue;
						}
						if (!l2.contains(validValue)) {
							l2.add(validValue);
						}
					}
				}

				if(!l2.isEmpty()) {
					cyRow.set(targetColumn.getName(), l2);
				}
			}
		}
	}

}
