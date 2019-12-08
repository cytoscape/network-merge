package org.cytoscape.network.merge.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.network.merge.internal.conflict.AttributeConflictCollector;
import org.cytoscape.network.merge.internal.task.NetworkMergeCommandTask;
import org.cytoscape.network.merge.internal.util.ColumnType;

public class NodeMerger {
	protected final AttributeConflictCollector conflictCollector;

	public NodeMerger() {this(null);	}
	public NodeMerger(final AttributeConflictCollector conflictCollector) {
		this.conflictCollector = conflictCollector;
	}

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
		
		if (targColType == ColumnType.STRING) {
			final String fromValue = fromCyRow.get(fromColumn.getName(), String.class);
			final String o2 = targetRow.get(targetColumn.getName(), String.class);
			
//			System.out.println("mergeAttribute: " + fromValue + " - " + o2);
			if (o2 == null || o2.length() == 0)  // null or empty attribute
			{
				targetRow.set(targetColumn.getName(), fromValue);							// <-------
				if (countColumn != null) targetRow.set(countColumn.getName(), 1);							// <-------
			}
			else if (fromValue != null && fromValue.equals(o2)) { } // the same, do nothing
			else  if (conflictCollector != null)
					conflictCollector.addConflict(from, fromColumn, target, targetColumn);
		} else if (!targColType.isList()) 
		{ // simple type (Integer, Long, Double, Boolean)
			Object o1 = fromCyRow.get(fromColumn.getName(), fromColType.getType());
			if (fromColType != targColType) 
				o1 = targColType.castService(o1);
			if (o1 == null) return;
			Object o2 = targetRow.get(targetColumn.getName(), targColType.getType());
			if (o2 == null) 
			{
				targetRow.set(targetColumn.getName(), o1);					// <-------
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

}

