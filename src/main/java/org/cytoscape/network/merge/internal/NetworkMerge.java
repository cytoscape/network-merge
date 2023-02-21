package org.cytoscape.network.merge.internal;

/*
 * #%L
 * Cytoscape Merge Impl (network-merge-impl)
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2006 - 2013 The Cytoscape Consortium
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */

import java.util.List;

import javax.swing.Icon;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.util.swing.IconManager;
import org.cytoscape.util.swing.TextIcon;

/**
 * Interface of merging networks
 */
public interface NetworkMerge {

	final String UNION_ICON = "s";
	final String INTERSECTION_ICON = "r";
	final String DIFFERENCE_ICON = "q";

	public enum Operation {
		UNION("Union", UNION_ICON),
		INTERSECTION("Intersection", INTERSECTION_ICON),
		DIFFERENCE("Difference", DIFFERENCE_ICON);

		private final String opName;
		private String iconText;

		private Operation(String opName, String iconText) {
			this.opName = opName;
			this.iconText = iconText;
		}

		public Icon getIcon(IconManager iconManager, float fontSize, int width, int height) {
			return new TextIcon(iconText, iconManager.getIconFont("cytoscape-3", fontSize), width, height);
		}

		@Override
		public String toString() {
			return opName;
		}
	}

	/**
	 * Merge networks into one.
	 *
	 * @param toNetwork
	 *            merge to this network
	 * @param fromNetworks
	 *            networks to be merged
	 * @param op
	 *            operation
	 * @param subtractOnlyUnconnectedNodes
	 *            only subtract nodes if all their edges are to be removed (applies to difference only)
	 * @param nodesOnly
	 *            merge only nodes and ignore edges
	 * @return the merged network.
	 */
	public CyNetwork mergeNetwork(CyNetwork toNetwork, List<CyNetwork> fromNetworks, Operation op, boolean subtractOnlyUnconnectedNodes, boolean nodesOnly);
}
