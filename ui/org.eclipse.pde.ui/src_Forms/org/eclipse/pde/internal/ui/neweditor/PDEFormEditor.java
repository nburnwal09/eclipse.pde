/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.neweditor;

import java.io.File;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.*;
import org.eclipse.jface.action.*;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.pde.internal.ui.*;
import org.eclipse.pde.internal.ui.editor.SystemFileEditorInput;
import org.eclipse.pde.internal.ui.neweditor.context.*;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.*;
import org.eclipse.ui.forms.editor.*;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;
/**
 * A simple multi-page form editor that uses Eclipse Forms support.
 * Example plug-in is configured to create one instance of
 * form colors that is shared between multiple editor instances.
 */
public abstract class PDEFormEditor extends FormEditor implements IInputContextListener {
	private Clipboard clipboard;
	private Menu contextMenu;
	protected InputContextManager inputContextManager;
	private IContentOutlinePage formOutline;
	private PDEMultiPageContentOutline contentOutline;
	/**
	 *  
	 */
	public PDEFormEditor() {
		PDEPlugin.getDefault().getLabelProvider().connect(this);
		inputContextManager = new InputContextManager();
		inputContextManager.addInputContextListener(this);
	}

/**
 * Tests whether this editor has a context with
 * a provided id. The test can be used to check
 * whether to add certain pages.
 * @param contextId
 * @return <code>true</code> if provided context is
 * present, <code>false</code> otherwise.
 */	
	public boolean hasInputContext(String contextId) {
		return inputContextManager.hasContext(contextId);
	}
	public InputContextManager getContextManager() {
		return inputContextManager;
	}
	protected void createInputContexts(InputContextManager contextManager) {
		IEditorInput input = getEditorInput();
		if (input instanceof IFileEditorInput) {
			// resource - find the project
			createResourceContexts(contextManager, (IFileEditorInput)input);
		}
		else if (input instanceof SystemFileEditorInput) {
			// system file - find the file system folder
			createSystemFileContexts(contextManager, (SystemFileEditorInput)input);
		}
		else if (input instanceof IStorageEditorInput) {
			createStorageContexts(contextManager, (IStorageEditorInput)input);
		}
	}
	
	protected abstract void createResourceContexts(InputContextManager contexts, IFileEditorInput input);
	protected abstract void createSystemFileContexts(InputContextManager contexts, SystemFileEditorInput input);
	protected abstract void createStorageContexts(InputContextManager contexts, IStorageEditorInput input);
	
	
	/*
	 *  (non-Javadoc)
	 * @see org.eclipse.ui.forms.editor.FormEditor#createToolkit(org.eclipse.swt.widgets.Display)
	 */
	protected FormToolkit createToolkit(Display display) {
		// Create a toolkit that shares colors between editors.
		return new FormToolkit(PDEPlugin.getDefault().getFormColors(
				display));
	}
	/*
 	* When subclassed, don't forget to call 'super'
 	**/
	protected void createPages() {
		clipboard = new Clipboard(getContainer().getDisplay());	
		MenuManager manager = new MenuManager();
		IMenuListener listener = new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				contextMenuAboutToShow(manager);
			}
		};
		manager.setRemoveAllWhenShown(true);
		manager.addMenuListener(listener);
		contextMenu = manager.createContextMenu(getContainer());
		getContainer().setMenu(contextMenu);
		super.createPages();
		createInputContexts(inputContextManager);			
		String pageToShow = computeInitialPageId();
		if (pageToShow!=null)
				setActivePage(pageToShow);
	}
	
	protected void pageChange(int newPageIndex) {
		super.pageChange(newPageIndex);
		updateContentOutline(getActivePageInstance());
	}
	
	public Clipboard getClipboard() {
		return clipboard;
	}
	
	protected abstract void contextMenuAboutToShow(IMenuManager manager);

	protected String computeInitialPageId() {
		String firstPageId=null;
		String storedFirstPageId = loadDefaultPage();
		if (storedFirstPageId != null)
			firstPageId = storedFirstPageId;
		/*
		else if (EditorPreferencePage.getUseSourcePage())
			firstPageId = getSourcePageId();
		*/
		/*
		// Regardless what is the stored value,
		// use source page if model is not valid
		if (isModelCorrect(getModel())==false)
			firstPageId = getSourcePageId();
		*/
		return firstPageId;
	}	
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.ISaveablePart#doSave(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void doSave(IProgressMonitor monitor) {
		commitFormPages(true);
		inputContextManager.save(monitor);
		fireDirtyStateChanged();
	}
	public void doRevert() {
		/*
		PDESourcePage sourcePage = (PDESourcePage) getPage(getSourcePageId());
		sourcePage.doRevertToSaved();
		updateModel();
		((IEditable)getModel()).setDirty(false);
		fireSaveNeeded();
		*/
	}
	
	private void commitFormPages(boolean onSave) {

	}
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.ISaveablePart#doSaveAs()
	 */
	public void doSaveAs() {
	}
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.ISaveablePart#isSaveAsAllowed()
	 */
	public boolean isSaveAsAllowed() {
		return false;
	}
	
	private void storeDefaultPage() {
		IEditorInput input = getEditorInput();
		int lastPage = getCurrentPage();
		if (lastPage== -1) return;
		String pageId = ((IFormPage)pages.get(lastPage)).getId();

		if (input instanceof IFileEditorInput) {
			// load the setting from the resource
			IFile file = ((IFileEditorInput) input).getFile();
			if (file != null) {
				//set the settings on the resouce
				try {
					file.setPersistentProperty(
						IPDEUIConstants.DEFAULT_EDITOR_PAGE_KEY_NEW,
						pageId);
				} catch (CoreException e) {
				}
			}
		} else if (input instanceof SystemFileEditorInput) {
			File file =
				(File) ((SystemFileEditorInput) input).getAdapter(File.class);
			if (file == null)
				return;
			IDialogSettings section = getSettingsSection();

			section.put(file.getPath(), pageId);
		}
	}

	private String loadDefaultPage() {
		IEditorInput input = getEditorInput();

		if (input instanceof IFileEditorInput) {
			// load the setting from the resource
			IFile file = ((IFileEditorInput) input).getFile();
			try {
				return file.getPersistentProperty(
					IPDEUIConstants.DEFAULT_EDITOR_PAGE_KEY_NEW);
			} catch (CoreException e) {
				return null;
			}
		} else if (input instanceof SystemFileEditorInput) {
			File file =
				(File) ((SystemFileEditorInput) input).getAdapter(File.class);
			if (file == null)
				return null;
			IDialogSettings section = getSettingsSection();
			String key = file.getPath();
			return section.get(key);
		}
		return null;
	}
	
	public void dispose() {
		storeDefaultPage();
		//setSelection(new StructuredSelection());
		PDEPlugin.getDefault().getLabelProvider().disconnect(this);
		if (clipboard!=null) {
			clipboard.dispose();
			clipboard = null;
		}
		inputContextManager.dispose();
		inputContextManager = null;
	}
	
	public void fireDirtyStateChanged() {
		firePropertyChange(PROP_DIRTY);
		//PDEEditorContributor contributor = getContributor();
		//if (contributor != null)
			//contributor.updateActions();
	}
	
	public boolean isDirty() {
		return inputContextManager.isDirty();
	}

	public void fireSaveNeeded(IEditorInput input) {
		fireDirtyStateChanged();
		validateEdit(input);
	}

	private void validateEdit(IEditorInput input) {
		InputContext context = inputContextManager.getContext(input);
		context.validateEdit();
	}
	
	private IDialogSettings getSettingsSection() {
		// store the setting in dialog settings
		IDialogSettings root = PDEPlugin.getDefault().getDialogSettings();
		IDialogSettings section = root.getSection("multi-page-editor");
		if (section == null)
			section = root.addNewSection("multi-page-editor");
		return section;
	}
	public void setSelection(ISelection selection) {
		getSite().getSelectionProvider().setSelection(selection);
	}
	public ISelection getSelection() {
		return getSite().getSelectionProvider().getSelection();
	}

	public Object getAdapter(Class key) {
		if (key.equals(IContentOutlinePage.class)) {
			return getContentOutline();
		}
		/*
		if (key.equals(IPropertySheetPage.class)) {
			return getPropertySheet();
		}
		if (key.equals(IGotoMarker.class)) {
			return this;
		}
		*/
		return super.getAdapter(key);
	}
	
	public PDEMultiPageContentOutline getContentOutline() {
		if (contentOutline == null || contentOutline.isDisposed()) {
			contentOutline = new PDEMultiPageContentOutline();
			updateContentOutline(getActivePageInstance());
		}
		return contentOutline;
	}

	protected IContentOutlinePage getFormOutline() {
		if (formOutline==null) {
			formOutline = new FormOutlinePage(this);
		}
		return formOutline;
	}

	private void updateContentOutline(IFormPage page) {
		if (contentOutline==null) return;
		IContentOutlinePage outline = null;
		if (page instanceof PDESourcePage) {
			outline = ((PDESourcePage)page).getContentOutline();
		}
		else
			outline = getFormOutline();
		contentOutline.setPageActive(outline);
	}
	/* package */ IFormPage [] getPages() {
		return (IFormPage[])pages.toArray(new IFormPage[pages.size()]);
	}
	/* package */ void performGlobalAction(String actionId) {
	}
}