/*******************************************************************************
 * Copyright (c) 2004, 2007 IBM Corporation and others. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: IBM - Initial API and implementation
 ******************************************************************************/
package org.eclipse.pde.internal.build.site;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.pde.internal.build.Utils;
import org.eclipse.update.configurator.ConfiguratorUtils;
import org.eclipse.update.configurator.IPlatformConfiguration;

public class PluginPathFinder {
	private static final String URL_PROPERTY = "org.eclipse.update.resolution_url"; //$NON-NLS-1$
	private static final String EMPTY_STRING = ""; //$NON-NLS-1$

	/**
	 * 
	 * @param platformHome
	 * @param linkFile
	 * @param features false for plugins, true for features
	 * @return path of plugins or features directory of an extension site
	 */
	private static String getSitePath(String platformHome, File linkFile, boolean features) {
		String prefix = new Path(platformHome).removeLastSegments(1).toString();
		Properties properties = new Properties();
		try {
			FileInputStream fis = new FileInputStream(linkFile);
			properties.load(fis);
			fis.close();
			String path = properties.getProperty("path"); //$NON-NLS-1$
			if (path != null) {
				if (!new Path(path).isAbsolute())
					path = prefix + IPath.SEPARATOR + path;
				path += IPath.SEPARATOR + "eclipse" + IPath.SEPARATOR; //$NON-NLS-1$
				if (features)
					path += "features"; //$NON-NLS-1$
				else
					path += "plugins"; //$NON-NLS-1$
				if (new File(path).exists()) {
					return path;
				}
			}
		} catch (IOException e) {
			//ignore
		}
		return null;
	}

	/**
	 * 
	 * @param platformHome
	 * @param features false for plugin sites, true for feature sites
	 * @return array of ".../plugins" or ".../features" Files
	 */
	private static File[] getSites(String platformHome, boolean features) {
		ArrayList sites = new ArrayList();

		File file = new File(platformHome, features ? "features" : "plugins"); //$NON-NLS-1$ //$NON-NLS-2$
		if (!features && !file.exists())
			file = new File(platformHome);
		if (file.exists())
			sites.add(file);

		File[] linkFiles = new File(platformHome + IPath.SEPARATOR + "links").listFiles(); //$NON-NLS-1$	
		if (linkFiles != null) {
			for (int i = 0; i < linkFiles.length; i++) {
				String path = getSitePath(platformHome, linkFiles[i], features);
				if (path != null) {
					sites.add(new File(path));
				}
			}
		}
		return (File[]) sites.toArray(new File[sites.size()]);
	}

	public static File[] getFeaturePaths(String platformHome) {
		return getPaths(platformHome, true);
	}
	
	public static File[] getPluginPaths(String platformHome) {
		return getPaths(platformHome, false);
	}
	
	public static File[] getPaths(String platformHome, boolean features) {
		File file = new File(platformHome, "configuration/org.eclipse.update/platform.xml"); //$NON-NLS-1$
		if (file.exists()) {
			try {
				String value = new Path(platformHome).toFile().toURL().toExternalForm();
				System.setProperty(URL_PROPERTY, value);
				try {
					IPlatformConfiguration config = ConfiguratorUtils.getPlatformConfiguration(file.toURL());
					return Utils.asFile(getConfiguredSitesPaths(platformHome, config, features));
				} finally {
					System.setProperty(URL_PROPERTY, EMPTY_STRING);
				}
			} catch (MalformedURLException e) {
				//ignore
			} catch (IOException e) {
				//ignore
			}
		}

		return Utils.asFile(scanLocations(getSites(platformHome, features)));
	}

	private static URL[] getConfiguredSitesPaths(String platformHome, IPlatformConfiguration configuration, boolean features) {
		List installPlugins = scanLocations(new File[] {new File(platformHome, features ? "features" : "plugins")}); //$NON-NLS-1$ //$NON-NLS-2$
		List extensionPlugins = getExtensionPluginURLs(configuration, features);

		Set all = new LinkedHashSet();
		all.addAll(installPlugins);
		all.addAll(extensionPlugins);
		
		return (URL[]) all.toArray(new URL[all.size()]);
	}

	/**
	 * 
	 * @param config
	 * @param features true for features false for plugins
	 * @return URLs for features or plugins on the site
	 */
	private static List getExtensionPluginURLs(IPlatformConfiguration config, boolean features) {
		ArrayList extensionPlugins = new ArrayList();
		IPlatformConfiguration.ISiteEntry[] sites = config.getConfiguredSites();
		for (int i = 0; i < sites.length; i++) {
			URL url = sites[i].getURL();
			if ("file".equalsIgnoreCase(url.getProtocol())) { //$NON-NLS-1$
				String[] entries;
				if (features)
					entries = sites[i].getFeatures();
				else
					entries = sites[i].getPlugins();
				for (int j = 0; j < entries.length; j++) {
					try {
						extensionPlugins.add(new File(url.getFile(), entries[j]).toURL());
					} catch (MalformedURLException e) {
						//ignore
					}
				}
			}
		}
		return extensionPlugins;
	}

	/**
	 * Scan given plugin/feature directories or jars for existence
	 * @param sites
	 * @return URLs to plugins/features
	 */
	private static List scanLocations(File[] sites) {
		ArrayList result = new ArrayList();
		for (int i = 0; i < sites.length; i++) {
			if (!sites[i].exists())
				continue;
			File[] children = sites[i].listFiles();
			if (children != null) {
				for (int j = 0; j < children.length; j++) {
					try {
						result.add(children[j].toURL());
					} catch (MalformedURLException e) {
						//ignore
					}
				}
			}
		}
		return result;
	}
}
