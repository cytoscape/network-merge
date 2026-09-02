package org.cytoscape.network.merge.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Set;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.junit.Before;
import org.junit.Test;

public class NetworkMergeActionTest {

	private CyNetworkManager netManager;
	private NetworkMergeAction action;

	@Before
	public void setUp() {
		netManager = mock(CyNetworkManager.class);
		CyServiceRegistrar registrar = mock(CyServiceRegistrar.class);
		when(registrar.getService(CyNetworkManager.class)).thenReturn(netManager);
		action = new NetworkMergeAction(registrar);
	}

	@Test
	public void menuLocation() {
		assertEquals("Networks...", action.getName());
		assertEquals("Tools.Merge", action.getPreferredMenu());
		assertTrue(action.isInMenuBar());
	}

	@Test
	public void disabledWithoutNetworks() {
		when(netManager.getNetworkSet()).thenReturn(Collections.emptySet());
		action.menuSelected(null);
		assertFalse(action.isEnabled());
	}

	@Test
	public void enabledWithNetworks() {
		when(netManager.getNetworkSet()).thenReturn(Set.of(mock(CyNetwork.class)));
		action.menuSelected(null);
		assertTrue(action.isEnabled());
	}
}
