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
import org.cytoscape.network.merge.internal.util.ColumnType;

public class NodeMerger {
	protected final AttributeConflictCollector conflictCollector;

	public NodeMerger() {this(null);	}
	public NodeMerger(final AttributeConflictCollector conflictCollector) {
		this.conflictCollector = conflictCollector;
	}

	public void mergeAttribute(Map<CyNode, CyColumn> nodeColMap, CyNode node, CyColumn targetColumn, CyNetwork targetNet) 
	{
		if ((nodeColMap == null) || (node == null) || (targetColumn == null) || (targetNet == null))
			throw new java.lang.IllegalArgumentException("Required parameters cannot be null.");

//		System.out.println("NodeMerger.mergeAttribute: " + targetColumn.getName());

		for (CyNode from : nodeColMap.keySet()) {
//			AttributeBasedNetworkMerge.dumpRow(from);
			final CyColumn fromColumn = nodeColMap.get(from);
//			final CyRow fromCyRow = fromTable.getRow(from.getSUID());			
			merge(from, fromColumn, targetNet, node, targetColumn);
		}
	}
	
	private void merge(CyNode from, CyColumn fromColumn, CyNetwork targetNet, CyNode target, CyColumn targetColumn) {
		
		if (from == null) throw new NullPointerException("from");
		if (target == null) throw new NullPointerException("target");
		if (from == fromColumn) throw new NullPointerException("fromColumn");
		if (target == targetColumn) throw new NullPointerException("targetColumn");

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
				targetRow.set(targetColumn.getName(), fromValue);							// <-------
			else if (fromValue != null && fromValue.equals(o2)) { } // the same, do nothing
			else  if (conflictCollector != null)
					conflictCollector.addConflict(from, fromColumn, target, targetColumn);
		} else if (!targColType.isList()) { // simple type (Integer, Long,
										// Double, Boolean)
			Object o1 = fromCyRow.get(fromColumn.getName(), fromColType.getType());
			if (fromColType != targColType) 
				o1 = targColType.castService(o1);

			Object o2 = targetRow.get(targetColumn.getName(), targColType.getType());
			if (o2 == null) 	targetRow.set(targetColumn.getName(), o1);					// <-------
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
						targetRow.set(targetColumn.getName(), l2);		// <-------
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
				targetRow.set(targetColumn.getName(), l2);		// <-------
		}
	}

}



//
//public void mergeAttribute(Map<CyNode, CyColumn> nodeColMap, CyNode node, CyColumn targetColumn, CyNetwork target) 
//{
//	if ((nodeColMap == null) || (node == null) || (targetColumn == null) || (target == null))
//		throw new java.lang.IllegalArgumentException("Required parameters cannot be null.");
//
//	System.out.println("NodeMerger.mergeAttribute: " + targetColumn.getName());
//	final CyRow cyRow = target.getRow(node);
//	final ColumnType colType = ColumnType.getType(targetColumn);
//
//	for (Map.Entry<CyNode, CyColumn> entryGOAttr : nodeColMap.entrySet()) {
//		final CyNode from = entryGOAttr.getKey();
//		AttributeBasedNetworkMerge.dumpRow(from);
//		final CyColumn fromColumn = entryGOAttr.getValue();
//		final CyTable fromTable = fromColumn.getTable();
//		final CyRow fromCyRow = fromTable.getRow(from.getSUID());
//		final ColumnType fromColType = ColumnType.getType(fromColumn);
//
//		if (colType == ColumnType.STRING) {
//			final String fromValue = fromCyRow.get(fromColumn.getName(), String.class);
//			final String o2 = cyRow.get(targetColumn.getName(), String.class);
//			
//			System.out.println("mergeAttribute: " + fromValue + " - " + o2);
//			if (o2 == null || o2.length() == 0)  // null or empty attribute
//				cyRow.set(targetColumn.getName(), fromValue);							// <-------
//			else if (fromValue != null && fromValue.equals(o2)) { } // the same, do nothing
//			else  if (conflictCollector != null)
//					conflictCollector.addConflict(from, fromColumn, node, targetColumn);
//		} else if (!colType.isList()) { // simple type (Integer, Long,
//										// Double, Boolean)
//			Object o1 = fromCyRow.get(fromColumn.getName(), fromColType.getType());
//			if (fromColType != colType) 
//				o1 = colType.castService(o1);
//
//			Object o2 = cyRow.get(targetColumn.getName(), colType.getType());
//			if (o2 == null) 	cyRow.set(targetColumn.getName(), o1);					// <-------
//			else if (o1.equals(o2)) {}
//			else if (conflictCollector != null)
//					conflictCollector.addConflict(from, fromColumn, node, targetColumn);
//		} else { // toattr is list type
//			// TODO: use a conflict handler to handle this part?
//			ColumnType plainType = colType.toPlain();
//
//			List l2 = cyRow.getList(targetColumn.getName(), plainType.getType());
//			if (l2 == null) {
//				l2 = new ArrayList<Object>();
//			}
//
//			if (!fromColType.isList()) {
//				// Simple data type
//				Object o1 = fromCyRow.get(fromColumn.getName(), fromColType.getType());
//				if (o1 != null) {
//					if (plainType != fromColType) 
//						o1 = plainType.castService(o1);
//					if (!l2.contains(o1)) 
//						l2.add(o1);
//				if (!l2.isEmpty()) 
//						cyRow.set(targetColumn.getName(), l2);		// <-------
//				}
//			} else { // from list
//				final ColumnType fromPlain = fromColType.toPlain();
//				final List<?> list = fromCyRow.getList(fromColumn.getName(), fromPlain.getType());
//				if(list == null)				continue;
//				
//				for (final Object listValue:list) {
//					if(listValue == null)		continue;
//					
//					final Object validValue;
//					if (plainType != fromColType) 
//						validValue = plainType.castService(listValue);
//					else
//						validValue = listValue;
//					if (!l2.contains(validValue)) 
//						l2.add(validValue);
//				}
//			}
//
//			if(!l2.isEmpty()) 
//				cyRow.set(targetColumn.getName(), l2);		// <-------
//		}
//	}
//		
//}

