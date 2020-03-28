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

import java.util.Properties;

import org.cytoscape.application.swing.CyAction;
import org.cytoscape.service.util.AbstractCyActivator;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.work.ServiceProperties;
import org.cytoscape.work.TaskFactory;
import org.osgi.framework.BundleContext;

import org.cytoscape.network.merge.internal.task.NetworkMergeCommandTaskFactory;

import static org.cytoscape.work.ServiceProperties.COMMAND;
import static org.cytoscape.work.ServiceProperties.COMMAND_DESCRIPTION;
import static org.cytoscape.work.ServiceProperties.COMMAND_EXAMPLE_JSON;
import static org.cytoscape.work.ServiceProperties.COMMAND_LONG_DESCRIPTION;
import static org.cytoscape.work.ServiceProperties.COMMAND_NAMESPACE;
import static org.cytoscape.work.ServiceProperties.COMMAND_SUPPORTS_JSON;


public class CyActivator extends AbstractCyActivator {
	
	public CyActivator() {
		super();
	}

	@Override
	public void start(BundleContext bc) {

		CyServiceRegistrar serviceRegistrar = getService(bc, CyServiceRegistrar.class);

		{
			NetworkMergeAction networkMergeAction = new NetworkMergeAction(serviceRegistrar);

			final Properties props = new Properties();
			props.setProperty(ServiceProperties.ID, "networkMergeAction");
			registerService(bc, networkMergeAction, CyAction.class, props); 
		}

		{
			NetworkMergeCommandTaskFactory mergeTask = new NetworkMergeCommandTaskFactory(serviceRegistrar);
			Properties props = new Properties();
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
