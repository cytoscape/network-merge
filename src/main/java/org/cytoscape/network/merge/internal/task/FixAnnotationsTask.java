package org.cytoscape.network.merge.internal.task;

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

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.view.presentation.annotations.Annotation;
import org.cytoscape.view.presentation.annotations.AnnotationFactory;
import org.cytoscape.view.presentation.annotations.AnnotationManager;
import org.cytoscape.view.presentation.annotations.ArrowAnnotation;
import org.cytoscape.view.presentation.annotations.BoundedTextAnnotation;
import org.cytoscape.view.presentation.annotations.GroupAnnotation;
import org.cytoscape.view.presentation.annotations.ImageAnnotation;
import org.cytoscape.view.presentation.annotations.ShapeAnnotation;
import org.cytoscape.view.presentation.annotations.TextAnnotation;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskMonitor;

public class FixAnnotationsTask extends AbstractTask {	
	final private CyServiceRegistrar serviceRegistrar; 

	private final CyNetworkViewManager viewManager;
	private final AnnotationManager annotationManager;
	private final CyNetwork newNetwork;
	private final Map<CyNetworkView, List<Annotation>> annotationMap;
	
	/**
	 * Constructor.<br>
	 * 
	 */
	public FixAnnotationsTask(final CyServiceRegistrar serviceRegistrar,
			final CyNetwork newNetwork, final Map<CyNetworkView, List<Annotation>> annotationMap) {

		this.serviceRegistrar = serviceRegistrar;
		this.viewManager = serviceRegistrar.getService(CyNetworkViewManager.class);
		this.annotationManager = serviceRegistrar.getService(AnnotationManager.class);

		this.newNetwork = newNetwork;
		this.annotationMap = annotationMap;
	}

	@Override
	public void run(TaskMonitor taskMonitor) throws Exception {
		taskMonitor.setStatusMessage("Fixing annotations...");

		// Step 1: get our new network view
		CyNetworkView newNetworkView = new ArrayList<CyNetworkView>(viewManager.getNetworkViews(newNetwork)).get(0);
		List<Annotation> newAnnotations = new ArrayList<>();

		// Step 2: for each original network view determine the X,Y translation
		for (CyNetworkView view: annotationMap.keySet()) {
			double centerX = view.getVisualProperty(BasicVisualLexicon.NETWORK_CENTER_X_LOCATION);
			double centerY = view.getVisualProperty(BasicVisualLexicon.NETWORK_CENTER_Y_LOCATION);
			for (Annotation ann: annotationMap.get(view)) {
				Map<String,String> argMap = ann.getArgMap();
				double x = Double.parseDouble(argMap.get(Annotation.X));
				double y = Double.parseDouble(argMap.get(Annotation.Y));
				// Adjust
				argMap.put(Annotation.X, String.valueOf(x-centerX));
				argMap.put(Annotation.Y, String.valueOf(y-centerY));

				// Step 3: create it in the new network
				Annotation newAnnotation = createAnnotation(argMap, ann, newNetworkView);
				newAnnotations.add(newAnnotation);
			}
		}
		annotationManager.addAnnotations(newAnnotations);
	}

	private Annotation createAnnotation(Map<String, String> argMap, Annotation ann, CyNetworkView newNetworkView) {
		String type = argMap.get("type");
		if (type.contains("ShapeAnnotation")) {
			AnnotationFactory<ShapeAnnotation> factory = 
				serviceRegistrar.getService(AnnotationFactory.class, "(type=ShapeAnnotation.class)");
			return factory.createAnnotation(ShapeAnnotation.class, newNetworkView, argMap);
		}
		if (type.contains("GroupAnnotation")) {
			AnnotationFactory<GroupAnnotation> factory = 
				serviceRegistrar.getService(AnnotationFactory.class, "(type=GroupAnnotation.class)");
			return factory.createAnnotation(GroupAnnotation.class, newNetworkView, argMap);
		}
		if (type.contains("TextAnnotation")) {
			AnnotationFactory<TextAnnotation> factory = 
				serviceRegistrar.getService(AnnotationFactory.class, "(type=TextAnnotation.class)");
			return factory.createAnnotation(TextAnnotation.class, newNetworkView, argMap);
		}
		if (type.contains("BoundedTextAnnotation")) {
			AnnotationFactory<BoundedTextAnnotation> factory = 
				serviceRegistrar.getService(AnnotationFactory.class, "(type=BoundedTextAnnotation.class)");
			return factory.createAnnotation(BoundedTextAnnotation.class, newNetworkView, argMap);
		}
		if (type.contains("ImageAnnotation")) {
			AnnotationFactory<ImageAnnotation> factory = 
				serviceRegistrar.getService(AnnotationFactory.class, "(type=ImageAnnotation.class)");
			return factory.createAnnotation(ImageAnnotation.class, newNetworkView, argMap);
		}
		if (type.contains("ArrowAnnotation")) {
			AnnotationFactory<ArrowAnnotation> factory = 
				serviceRegistrar.getService(AnnotationFactory.class, "(type=ArrowAnnotation.class)");
			return factory.createAnnotation(ArrowAnnotation.class, newNetworkView, argMap);
		}
		return null;
	}

	private AnnotationFactory<?> getAnnotationFactory(String type) {
		if (type.contains("ShapeAnnotation")) {
			return serviceRegistrar.getService(AnnotationFactory.class, "(type=ShapeAnnotation.class)");
		}
		if (type.contains("GroupAnnotation")) {
			return serviceRegistrar.getService(AnnotationFactory.class, "(type=GroupAnnotation.class)");
		}
		if (type.contains("TextAnnotation")) {
			return serviceRegistrar.getService(AnnotationFactory.class, "(type=TextAnnotation.class)");
		}
		if (type.contains("BoundedTextAnnotation")) {
			return serviceRegistrar.getService(AnnotationFactory.class, "(type=BoundedTextAnnotation.class)");
		}
		if (type.contains("ImageAnnotation")) {
			return serviceRegistrar.getService(AnnotationFactory.class, "(type=ImageAnnotation.class)");
		}
		if (type.contains("ArrowAnnotation")) {
			return serviceRegistrar.getService(AnnotationFactory.class, "(type=ArrowAnnotation.class)");
		}
		return null;
	}



}
