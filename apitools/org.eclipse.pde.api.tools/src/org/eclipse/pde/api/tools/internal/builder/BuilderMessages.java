/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.api.tools.internal.builder;

import org.eclipse.osgi.util.NLS;

public class BuilderMessages extends NLS {
	private static final String BUNDLE_NAME = "org.eclipse.pde.api.tools.internal.builder.buildermessages"; //$NON-NLS-1$
	public static String api_analysis_builder;
	public static String api_analysis_on_0;
	public static String default_profile_not_set;
	public static String building_workspace_profile;
	public static String checking_binary_compat;
	public static String checking_api_usage;
	public static String binary_incompat_change_0;
	
	public static String ApiAnalyserTaskName;
	public static String ApiProblemFactory_problem_message_not_found;
	public static String ApiProblemReporter_creating_problem_markers;
	public static String ApiProblemReporter_creating_problem_markers_on_0;
	public static String Compatibility_Analysis;
	public static String Analyzing_0_1;

	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, BuilderMessages.class);
	}

	private BuilderMessages() {
	}
}
