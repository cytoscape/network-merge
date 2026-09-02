package org.cytoscape.network.merge.internal;

import static org.cytoscape.work.ServiceProperties.COMMAND;
import static org.cytoscape.work.ServiceProperties.COMMAND_DESCRIPTION;
import static org.cytoscape.work.ServiceProperties.COMMAND_EXAMPLE_JSON;
import static org.cytoscape.work.ServiceProperties.COMMAND_LONG_DESCRIPTION;
import static org.cytoscape.work.ServiceProperties.COMMAND_NAMESPACE;
import static org.cytoscape.work.ServiceProperties.COMMAND_SUPPORTS_JSON;
import static org.cytoscape.work.ServiceProperties.ID;

import java.util.Properties;

import org.cytoscape.application.swing.CyAction;
import org.cytoscape.network.merge.internal.task.NetworkMergeCommandTaskFactory;
import org.cytoscape.service.util.AbstractCyActivator;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.work.TaskFactory;
import org.osgi.framework.BundleContext;

/*
 * #%L
 * Cytoscape Merge Impl (network-merge-impl)
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2006 - 2026 The Cytoscape Consortium
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

public class CyActivator extends AbstractCyActivator {
	
	@Override
	public void start(BundleContext bc) {
		var serviceRegistrar = getService(bc, CyServiceRegistrar.class);

		{
			// Registered as a CyAction with a well-known id so other apps (e.g. PSICQUIC) can invoke it
			var mergeAction = new NetworkMergeAction(serviceRegistrar);
			
			var props = new Properties();
			props.setProperty(ID, NetworkMergeAction.ID);
			registerService(bc, mergeAction, CyAction.class, props);
		}
		{
			var mergeTask = new NetworkMergeCommandTaskFactory(serviceRegistrar);
			var props = new Properties();
			props.setProperty(COMMAND_NAMESPACE, "network");
			props.setProperty(COMMAND, "merge");
			props.setProperty(COMMAND_DESCRIPTION, "Merge two or more networks");
			props.setProperty(COMMAND_LONG_DESCRIPTION, "Combine networks via union, intersection, or difference.  Lots of parameters apply!");
			props.setProperty(COMMAND_SUPPORTS_JSON, "true");
			props.setProperty(COMMAND_EXAMPLE_JSON, "{\"Merged Table\":\"12345\"}");
			registerService(bc, mergeTask, TaskFactory.class, props);
		}
	}
}
