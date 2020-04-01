package org.cytoscape.network.merge.internal.task;

import java.awt.Dialog;
import javax.swing.SwingUtilities;

import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskFactory;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskMonitor;

import org.cytoscape.network.merge.internal.ui.NetworkMergeDialog;

public class NetworkMergeTaskFactory implements TaskFactory {

	final CyServiceRegistrar registrar;
	final CyNetworkManager netManager;

	public NetworkMergeTaskFactory(CyServiceRegistrar reg) {  
		registrar = reg;
		netManager = reg.getService(CyNetworkManager.class);
	}

	public boolean isReady() {
		if (netManager.getNetworkSet() != null && netManager.getNetworkSet().size() > 0)
			return true;
		return false;
	}

	@Override
	public TaskIterator createTaskIterator() {
		return new TaskIterator(new NetworkMergeDialogTask(registrar));		//, application, merge
	}

	class NetworkMergeDialogTask extends AbstractTask {
		final CyServiceRegistrar registrar;
		final CySwingApplication swingApp;

		NetworkMergeDialogTask(final CyServiceRegistrar registrar) {
			this.registrar = registrar;
			this.swingApp = registrar.getService(CySwingApplication.class);
		}

		public void run(TaskMonitor monitor) {
			SwingUtilities.invokeLater(() -> 
			{
				final NetworkMergeDialog dialog = new NetworkMergeDialog(registrar);
				dialog.setLocationRelativeTo(swingApp.getJFrame());
				dialog.setModalityType(Dialog.DEFAULT_MODALITY_TYPE);
				dialog.setVisible(true);
			});
		}
	}
}
