package org.eclipse.pde.core.plugin;
/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import org.eclipse.core.runtime.CoreException;
/**
 * A model object that represents the content of the fragment.xml
 * file.
 * <p>
 * <b>Note:</b> This field is part of an interim API that is still under development and expected to
 * change significantly before reaching stability. It is being made available at this early stage to solicit feedback
 * from pioneering adopters on the understanding that any code that uses this API will almost certainly be broken
 * (repeatedly) as the API evolves.
 * </p>
 */
public interface IFragment extends IPluginBase {
	/**
	 * A property that will be used to notify
	 * that a plugin id has changed.
	 * <p>
	 * <b>Note:</b> This field is part of an interim API that is still under development and expected to
	 * change significantly before reaching stability. It is being made available at this early stage to solicit feedback
	 * from pioneering adopters on the understanding that any code that uses this API will almost certainly be broken
	 * (repeatedly) as the API evolves.
	 * </p>
	 */
	public static final String P_PLUGIN_ID = "plugin-id";
	/**
	 * A property that will be used to notify
	 * that a plugin version has changed.
	 * <p>
	 * <b>Note:</b> This field is part of an interim API that is still under development and expected to
	 * change significantly before reaching stability. It is being made available at this early stage to solicit feedback
	 * from pioneering adopters on the understanding that any code that uses this API will almost certainly be broken
	 * (repeatedly) as the API evolves.
	 * </p>
	 */
	public static final String P_PLUGIN_VERSION = "plugin-version";
	/**
	 * A property that will be used to notify
	 * that a plugin version match rule has changed.
	 * <p>
	 * <b>Note:</b> This field is part of an interim API that is still under development and expected to
	 * change significantly before reaching stability. It is being made available at this early stage to solicit feedback
	 * from pioneering adopters on the understanding that any code that uses this API will almost certainly be broken
	 * (repeatedly) as the API evolves.
	 * </p>
	 */
	public static final String P_RULE = "rule";
	/**
	 * Returns the id of the plug-in that is the target
	 * of this fragment.
	 * @return target plug-in id
	 * <p>
	 * <b>Note:</b> This field is part of an interim API that is still under development and expected to
	 * change significantly before reaching stability. It is being made available at this early stage to solicit feedback
	 * from pioneering adopters on the understanding that any code that uses this API will almost certainly be broken
	 * (repeatedly) as the API evolves.
	 * </p>
	 */
	String getPluginId();
	/**
	 * Returns the version of the plug-in that is the target
	 * of this fragment.
	 * @return target plug-in version
	 * <p>
	 * <b>Note:</b> This field is part of an interim API that is still under development and expected to
	 * change significantly before reaching stability. It is being made available at this early stage to solicit feedback
	 * from pioneering adopters on the understanding that any code that uses this API will almost certainly be broken
	 * (repeatedly) as the API evolves.
	 * </p>
	 */
	String getPluginVersion();
	/**
	 * Returns an optional version match rule as defined in
	 * IMatchRule interface.
	 * @see IMatchRule
	 * <p>
	 * <b>Note:</b> This field is part of an interim API that is still under development and expected to
	 * change significantly before reaching stability. It is being made available at this early stage to solicit feedback
	 * from pioneering adopters on the understanding that any code that uses this API will almost certainly be broken
	 * (repeatedly) as the API evolves.
	 * </p>
	 */
	int getRule();
	/**
	 * Sets the id of the plug-in that will be the target of this fragment.
	 * @param id the id of the referenced plug-in.
	 * @exception org.eclipse.core.runtime.CoreException attempts to modify a read-only fragment will result in an exception
	 * <p>
	 * <b>Note:</b> This field is part of an interim API that is still under development and expected to
	 * change significantly before reaching stability. It is being made available at this early stage to solicit feedback
	 * from pioneering adopters on the understanding that any code that uses this API will almost certainly be broken
	 * (repeatedly) as the API evolves.
	 * </p>
	 */
	void setPluginId(String id) throws CoreException;
	/**
	 * Sets the version of the plug-in that will be the target of this fragment.'
	 * @param version the version of the referenced version.
	 * @exception org.eclipse.core.runtime.CoreException attempts to modify a read-only fragment will result in an exception
	 * <p>
	 * <b>Note:</b> This field is part of an interim API that is still under development and expected to
	 * change significantly before reaching stability. It is being made available at this early stage to solicit feedback
	 * from pioneering adopters on the understanding that any code that uses this API will almost certainly be broken
	 * (repeatedly) as the API evolves.
	 * </p>
	 */
	void setPluginVersion(String version) throws CoreException;
	/**
	 * Sets the optional version match rule as defined in IMatchRules. This
	 * rule will be used when attempting to match the referenced plug-in
	 * version.
	 * @param rule the match rule to be used when locating the referenced the plug-in.
	 * @exception org.eclipse.core.runtime.CoreException attempts to modify a read-only fragment will result in an exception
	 * <p>
	 * <b>Note:</b> This field is part of an interim API that is still under development and expected to
	 * change significantly before reaching stability. It is being made available at this early stage to solicit feedback
	 * from pioneering adopters on the understanding that any code that uses this API will almost certainly be broken
	 * (repeatedly) as the API evolves.
	 * </p>
	 */
	void setRule(int rule) throws CoreException;
}