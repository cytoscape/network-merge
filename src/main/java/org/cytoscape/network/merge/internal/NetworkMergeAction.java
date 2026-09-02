package org.cytoscape.network.merge.internal;

import java.awt.Dialog;
import java.awt.event.ActionEvent;

import javax.swing.event.MenuEvent;
import javax.swing.event.PopupMenuEvent;

import org.cytoscape.application.swing.AbstractCyAction;
import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.network.merge.internal.ui.NetworkMergeDialog;
import org.cytoscape.service.util.CyServiceRegistrar;

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

/**
 * Opens the "Merge Networks" dialog from the Tools menu. It is registered as a {@link org.cytoscape.application.swing.CyAction}
 * service with the id {@code networkMergeAction} so that other apps (e.g. the PSICQUIC client) can look it up and invoke it.
 */
public class NetworkMergeAction extends AbstractCyAction {

	private static final long serialVersionUID = -597481727043928800L;
	
	/** Service property value other apps use to look this action up: {@code (id=networkMergeAction)}. */
	public static final String ID = "networkMergeAction";
	
	private static final String TITLE = "Networks...";
	private static final String PREFERRED_MENU = "Tools.Merge";
	private static final float MENU_GRAVITY = 0.1f;
	
	private final CyServiceRegistrar serviceRegistrar;

	public NetworkMergeAction(CyServiceRegistrar serviceRegistrar) {
		super(TITLE);
		this.serviceRegistrar = serviceRegistrar;
		
		setPreferredMenu(PREFERRED_MENU);
		setMenuGravity(MENU_GRAVITY);
	}

	/**
	 * Enabled whenever there is at least one network loaded, which is what the merge dialog needs to work with
	 * (same condition the previous TaskFactory used in its isReady()).
	 */
	@Override
	public void updateEnableState() {
		var netManager = serviceRegistrar.getService(CyNetworkManager.class);
		var networks = netManager != null ? netManager.getNetworkSet() : null;
		
		setEnabled(networks != null && !networks.isEmpty());
	}
	
	// AbstractCyAction's default menu listeners consult its internal "enableFor" support rather than
	// updateEnableState(), so route them here to make sure our own condition is what gets applied.
	@Override
	public void menuSelected(MenuEvent evt) {
		updateEnableState();
	}
	
	@Override
	public void popupMenuWillBecomeVisible(PopupMenuEvent evt) {
		updateEnableState();
	}

	@Override
	public void actionPerformed(ActionEvent evt) {
		var swingApp = serviceRegistrar.getService(CySwingApplication.class);
		
		var dialog = new NetworkMergeDialog(serviceRegistrar);
		dialog.setLocationRelativeTo(swingApp.getJFrame());
		dialog.setModalityType(Dialog.DEFAULT_MODALITY_TYPE);
		dialog.setVisible(true);
	}
}
