package org.cytoscape.network.merge.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyIdentifiable;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.network.merge.internal.conflict.AttributeConflictCollector;
import org.cytoscape.network.merge.internal.util.ColumnType;

public class EdgeMerger  {
	protected final AttributeConflictCollector conflictCollector;

	public EdgeMerger() {this(null);	}
	public EdgeMerger(final AttributeConflictCollector conflictCollector) {
		this.conflictCollector = conflictCollector;
	}
	
	public <CyEdge extends CyIdentifiable> void mergeAttribute(final Map<CyEdge, CyColumn> mapGOAttr, final CyEdge graphObject, final CyColumn column,
			final CyNetwork network) {
		if ((mapGOAttr == null) || (graphObject == null) || (column == null) || (network == null))
			throw new java.lang.IllegalArgumentException("Required parameters cannot be null.");

		final CyRow cyRow = network.getRow(graphObject);
		final ColumnType colType = ColumnType.getType(column);

		for (Map.Entry<CyEdge, CyColumn> entryGOAttr : mapGOAttr.entrySet()) {
			final CyEdge from = entryGOAttr.getKey();
			final CyColumn fromColumn = entryGOAttr.getValue();
			final CyTable fromTable = fromColumn.getTable();
			final CyRow fromCyRow = fromTable.getRow(from.getSUID());
			final ColumnType fromColType = ColumnType.getType(fromColumn);

			if (colType == ColumnType.STRING) {
				final String fromValue = fromCyRow.get(fromColumn.getName(), String.class);
				final String o2 = cyRow.get(column.getName(), String.class);
				
				if (o2 == null || o2.length() == 0) { // null or empty attribute
					cyRow.set(column.getName(), fromValue);
				} else if (fromValue != null && fromValue.equals(o2)) { // TODO: necessary?
					// the same, do nothing
				} else { // attribute conflict
					// add to conflict collector
					if (conflictCollector != null)
						conflictCollector.addConflict(from, fromColumn, graphObject, column);
				}
			} else if (!colType.isList()) { // simple type (Integer, Long,
											// Double, Boolean)
				Object o1 = fromCyRow.get(fromColumn.getName(), fromColType.getType());
				if (fromColType != colType) {
					o1 = colType.castService(o1);
				}

				Object o2 = cyRow.get(column.getName(), colType.getType());
				if (o2 == null) {
					cyRow.set(column.getName(), o1);
					// continue;
				} else if (o1.equals(o2)) {
					// continue; // the same, do nothing
				} else { // attribute conflict

					// add to conflict collector
					if (conflictCollector != null)
						conflictCollector.addConflict(from, fromColumn, graphObject, column);
					// continue;
				}
			} else { // toattr is list type
				// TODO: use a conflict handler to handle this part?
				ColumnType plainType = colType.toPlain();

				List l2 = cyRow.getList(column.getName(), plainType.getType());
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
							cyRow.set(column.getName(), l2);
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
					cyRow.set(column.getName(), l2);
				}
			}
		}
	}

}
