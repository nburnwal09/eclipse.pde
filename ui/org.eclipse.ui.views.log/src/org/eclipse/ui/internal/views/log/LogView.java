/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Jacek Pospychala <jacek.pospychala@pl.ibm.com> - bugs 202583, 202584, 207344
 *     													bugs 207323, 207931, 207101
 *     													bugs 172658, 216341
 *     Michael Rennie <Michael_Rennie@ca.ibm.com> - bug 208637
 *******************************************************************************/

package org.eclipse.ui.internal.views.log;

import com.ibm.icu.text.DateFormat;
import com.ibm.icu.text.SimpleDateFormat;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.List;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.*;
import org.eclipse.jface.dialogs.*;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.util.Policy;
import org.eclipse.jface.viewers.*;
import org.eclipse.jface.window.Window;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.*;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.dialogs.FilteredTree;
import org.eclipse.ui.dialogs.PatternFilter;
import org.eclipse.ui.part.ViewPart;

public class LogView extends ViewPart implements ILogListener {
	public static final String P_LOG_WARNING = "warning"; //$NON-NLS-1$
	public static final String P_LOG_ERROR = "error"; //$NON-NLS-1$
	public static final String P_LOG_INFO = "info"; //$NON-NLS-1$
	public static final String P_LOG_LIMIT = "limit"; //$NON-NLS-1$
	public static final String P_USE_LIMIT = "useLimit"; //$NON-NLS-1$
	public static final String P_SHOW_ALL_SESSIONS = "allSessions"; //$NON-NLS-1$
	private static final String P_COLUMN_1 = "column2"; //$NON-NLS-1$
	private static final String P_COLUMN_2 = "column3"; //$NON-NLS-1$
	private static final String P_COLUMN_3 = "column4"; //$NON-NLS-1$
	public static final String P_ACTIVATE = "activate"; //$NON-NLS-1$
	public static final String P_SHOW_FILTER_TEXT = "show_filter_text"; //$NON-NLS-1$
	public static final String P_ORDER_TYPE = "orderType"; //$NON-NLS-1$
	public static final String P_ORDER_VALUE = "orderValue"; //$NON-NLS-1$
	public static final String P_GROUP_BY = "groupBy"; //$NON-NLS-1$

	private int MESSAGE_ORDER;
	private int PLUGIN_ORDER;
	private int DATE_ORDER;

	public final static byte MESSAGE = 0x0;
	public final static byte PLUGIN = 0x1;
	public final static byte DATE = 0x2;
	public static int ASCENDING = 1;
	public static int DESCENDING = -1;

	public static final int GROUP_BY_NONE = 0;
	public static final int GROUP_BY_SESSION = 1;
	public static final int GROUP_BY_PLUGIN = 2;

	private List elements;
	private Map groups;
	private LogSession currentSession;

	private List batchedEntries;
	private boolean batchEntries;

	private Clipboard fClipboard;

	private IMemento fMemento;
	private File fInputFile;
	private String fDirectory;

	private Comparator fComparator;

	// hover text
	private boolean fCanOpenTextShell;
	private Text fTextLabel;
	private Shell fTextShell;

	private boolean fFirstEvent = true;

	private TreeColumn fColumn1;
	private TreeColumn fColumn2;
	private TreeColumn fColumn3;

	private Tree fTree;
	private FilteredTree fFilteredTree;
	private LogViewLabelProvider fLabelProvider;

	private Action fPropertiesAction;
	private Action fDeleteLogAction;
	private Action fReadLogAction;
	private Action fCopyAction;
	private Action fActivateViewAction;
	private Action fOpenLogAction;
	private Action fExportAction;

	/**
	 * Action called when user selects "Group by -> ..." from menu.
	 */
	class GroupByAction extends Action {
		private int groupBy;

		public GroupByAction(String text, int groupBy) {
			super(text, Action.AS_RADIO_BUTTON);

			this.groupBy = groupBy;

			if (fMemento.getInteger(LogView.P_GROUP_BY).intValue() == groupBy) {
				setChecked(true);
			}
		}

		public void run() {
			if (fMemento.getInteger(LogView.P_GROUP_BY).intValue() != groupBy) {
				fMemento.putInteger(LogView.P_GROUP_BY, groupBy);
				reloadLog();
			}
		}
	}

	/**
	 * Constructor
	 */
	public LogView() {
		elements = new ArrayList();
		groups = new HashMap();
		batchedEntries = new ArrayList();
		fInputFile = Platform.getLogFileLocation().toFile();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.part.WorkbenchPart#createPartControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createPartControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.horizontalSpacing = 0;
		layout.verticalSpacing = 0;
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		composite.setLayout(layout);

		readLogFile();
		createViewer(composite);
		getSite().setSelectionProvider(fFilteredTree.getViewer());
		createActions();
		fClipboard = new Clipboard(fTree.getDisplay());
		fTree.setToolTipText(""); //$NON-NLS-1$
		initializeViewerSorter();

		makeHoverShell();

		Platform.addLogListener(this);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(fTree, IHelpContextIds.LOG_VIEW);
		getSite().getWorkbenchWindow().addPerspectiveListener(new IPerspectiveListener2() {

			public void perspectiveChanged(IWorkbenchPage page, IPerspectiveDescriptor perspective, IWorkbenchPartReference partRef, String changeId) {
				if (!(partRef instanceof IViewReference))
					return;

				IWorkbenchPart part = partRef.getPart(false);
				if (part == null) {
					return;
				}

				if (part.equals(LogView.this)) {
					if (changeId.equals(IWorkbenchPage.CHANGE_VIEW_SHOW)) {
						if (!batchedEntries.isEmpty()) {
							pushBatchedEntries();
						}

						batchEntries = false;
					} else if (changeId.equals(IWorkbenchPage.CHANGE_VIEW_HIDE)) {
						batchEntries = true;
					}
				}
			}

			public void perspectiveActivated(IWorkbenchPage page, IPerspectiveDescriptor perspective) {
				// empty
			}

			public void perspectiveChanged(IWorkbenchPage page, IPerspectiveDescriptor perspective, String changeId) {
				// empty
			}

		});
	}

	/**
	 * Creates the actions for the viewsite action bars
	 */
	private void createActions() {
		IActionBars bars = getViewSite().getActionBars();

		fCopyAction = createCopyAction();
		bars.setGlobalActionHandler(ActionFactory.COPY.getId(), fCopyAction);

		IToolBarManager toolBarManager = bars.getToolBarManager();

		fExportAction = createExportAction();
		toolBarManager.add(fExportAction);

		final Action importLogAction = createImportLogAction();
		toolBarManager.add(importLogAction);

		toolBarManager.add(new Separator());

		final Action clearAction = createClearAction();
		toolBarManager.add(clearAction);

		fDeleteLogAction = createDeleteLogAction();
		toolBarManager.add(fDeleteLogAction);

		fOpenLogAction = createOpenLogAction();
		toolBarManager.add(fOpenLogAction);

		fReadLogAction = createReadLogAction();
		toolBarManager.add(fReadLogAction);

		toolBarManager.add(new Separator());

		IMenuManager mgr = bars.getMenuManager();

		mgr.add(createGroupByAction());

		mgr.add(new Separator());

		mgr.add(createFilterAction());

		mgr.add(new Separator());

		fActivateViewAction = createActivateViewAction();
		mgr.add(fActivateViewAction);

		mgr.add(createShowTextFilter());

		createPropertiesAction();

		MenuManager popupMenuManager = new MenuManager("#PopupMenu"); //$NON-NLS-1$
		IMenuListener listener = new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				manager.add(fCopyAction);
				manager.add(new Separator());
				manager.add(clearAction);
				manager.add(fDeleteLogAction);
				manager.add(fOpenLogAction);
				manager.add(fReadLogAction);
				manager.add(new Separator());
				manager.add(fExportAction);
				manager.add(importLogAction);
				manager.add(new Separator());

				((EventDetailsDialogAction) fPropertiesAction).setComparator(fComparator);
				TreeItem[] selection = fTree.getSelection();
				if ((selection.length > 0) && (selection[0].getData() instanceof LogEntry)) {
					manager.add(fPropertiesAction);
				}

				manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
			}
		};
		popupMenuManager.addMenuListener(listener);
		popupMenuManager.setRemoveAllWhenShown(true);
		getSite().registerContextMenu(popupMenuManager, getSite().getSelectionProvider());
		Menu menu = popupMenuManager.createContextMenu(fTree);
		fTree.setMenu(menu);
	}

	private Action createActivateViewAction() {
		Action action = new Action(Messages.LogView_activate) { //       	
			public void run() {
				fMemento.putString(P_ACTIVATE, isChecked() ? "true" : "false"); //$NON-NLS-1$ //$NON-NLS-2$
			}
		};
		action.setChecked(fMemento.getString(P_ACTIVATE).equals("true")); //$NON-NLS-1$
		return action;
	}

	private Action createClearAction() {
		Action action = new Action(Messages.LogView_clear) {
			public void run() {
				handleClear();
			}
		};
		action.setImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_CLEAR));
		action.setDisabledImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_CLEAR_DISABLED));
		action.setToolTipText(Messages.LogView_clear_tooltip);
		action.setText(Messages.LogView_clear);
		return action;
	}

	private Action createCopyAction() {
		Action action = new Action(Messages.LogView_copy) {
			public void run() {
				copyToClipboard(fFilteredTree.getViewer().getSelection());
			}
		};
		action.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_TOOL_COPY));
		return action;
	}

	private Action createDeleteLogAction() {
		Action action = new Action(Messages.LogView_delete) {
			public void run() {
				doDeleteLog();
			}
		};
		action.setToolTipText(Messages.LogView_delete_tooltip);
		action.setImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_REMOVE_LOG));
		action.setDisabledImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_REMOVE_LOG_DISABLED));
		action.setEnabled(fInputFile.exists() && fInputFile.equals(Platform.getLogFileLocation().toFile()));
		return action;
	}

	private Action createExportAction() {
		Action action = new Action(Messages.LogView_export) {
			public void run() {
				handleExport();
			}
		};
		action.setToolTipText(Messages.LogView_export_tooltip);
		action.setImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_EXPORT));
		action.setDisabledImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_EXPORT_DISABLED));
		action.setEnabled(fInputFile.exists());
		return action;
	}

	private Action createFilterAction() {
		Action action = new Action(Messages.LogView_filter) {
			public void run() {
				handleFilter();
			}
		};
		action.setToolTipText(Messages.LogView_filter);
		action.setImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_FILTER));
		action.setDisabledImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_FILTER_DISABLED));
		return action;
	}

	private Action createImportLogAction() {
		Action action = new Action(Messages.LogView_import) {
			public void run() {
				handleImport();
			}
		};
		action.setToolTipText(Messages.LogView_import_tooltip);
		action.setImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_IMPORT));
		action.setDisabledImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_IMPORT_DISABLED));
		return action;
	}

	private Action createOpenLogAction() {
		Action action = null;
		try {
			// TODO this isn't the best way to check... we should be smarter and use package admin
			// check to see if org.eclipse.ui.ide is available
			Class.forName("org.eclipse.ui.ide.IDE"); //$NON-NLS-1$
			// check to see if org.eclipse.core.filesystem is available
			Class.forName("org.eclipse.core.filesystem.IFileStore"); //$NON-NLS-1$
			action = new OpenIDELogFileAction(this);
		} catch (ClassNotFoundException e) {
			action = new Action() {
				public void run() {
					if (fInputFile.exists()) {
						Job job = getOpenLogFileJob();
						job.setUser(false);
						job.setPriority(Job.SHORT);
						job.schedule();
					}
				}
			};
		}
		action.setText(Messages.LogView_view_currentLog);
		action.setImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_OPEN_LOG));
		action.setDisabledImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_OPEN_LOG_DISABLED));
		action.setEnabled(fInputFile.exists());
		action.setToolTipText(Messages.LogView_view_currentLog_tooltip);
		return action;
	}

	private void createPropertiesAction() {
		fPropertiesAction = new EventDetailsDialogAction(fTree.getShell(), fFilteredTree.getViewer(), fMemento);
		fPropertiesAction.setImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_PROPERTIES));
		fPropertiesAction.setDisabledImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_PROPERTIES_DISABLED));
		fPropertiesAction.setToolTipText(Messages.LogView_properties_tooltip);
		fPropertiesAction.setEnabled(false);
	}

	private Action createReadLogAction() {
		Action action = new Action(Messages.LogView_readLog_restore) {
			public void run() {
				fInputFile = Platform.getLogFileLocation().toFile();
				reloadLog();
			}
		};
		action.setToolTipText(Messages.LogView_readLog_restore_tooltip);
		action.setImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_READ_LOG));
		action.setDisabledImageDescriptor(SharedImages.getImageDescriptor(SharedImages.DESC_READ_LOG_DISABLED));
		return action;
	}

	/**
	 * Creates the Show Text Filter view menu action 
	 * @return the new action for the Show Text Filter 
	 */
	private Action createShowTextFilter() {
		Action action = new Action(Messages.LogView_show_filter_text) {
			public void run() {
				showFilterText(isChecked());
			}
		};
		boolean visible = fMemento.getBoolean(P_SHOW_FILTER_TEXT).booleanValue();
		action.setChecked(visible);
		showFilterText(visible);
		return action;
	}

	/**
	 * Shows/hides the filter text control from the filtered tree. This method also sets the 
	 * P_SHOW_FILTER_TEXT preference to the visible state
	 * 
	 * @param visible if the filter text control should be shown or not
	 */
	private void showFilterText(boolean visible) {
		fMemento.putBoolean(P_SHOW_FILTER_TEXT, visible);
		Composite ctrl = fFilteredTree.getFilterControl().getParent();
		GridData gd = (GridData) ctrl.getLayoutData();
		gd.exclude = !visible;
		ctrl.setVisible(visible);
		gd.verticalIndent = 8;
		gd.horizontalIndent = 4;
		if (!visible) // reset control if we aren't visible
			fFilteredTree.getFilterControl().setText(Messages.LogView_show_filter_initialText);
		fFilteredTree.layout(false);
	}

	private IContributionItem createGroupByAction() {
		IMenuManager manager = new MenuManager(Messages.LogView_GroupBy);
		manager.add(new GroupByAction(Messages.LogView_GroupBySession, LogView.GROUP_BY_SESSION));
		manager.add(new GroupByAction(Messages.LogView_GroupByPlugin, LogView.GROUP_BY_PLUGIN));
		manager.add(new GroupByAction(Messages.LogView_GroupByNone, LogView.GROUP_BY_NONE));
		return manager;
	}

	private void createViewer(Composite parent) {

		fFilteredTree = new FilteredTree(parent, SWT.FULL_SELECTION, new PatternFilter() {
			protected boolean isLeafMatch(Viewer viewer, Object element) {
				if (element instanceof LogEntry) {
					LogEntry logEntry = (LogEntry) element;
					String message = logEntry.getMessage();
					String plugin = logEntry.getPluginId();
					String date = logEntry.getFormattedDate();
					return wordMatches(message) || wordMatches(plugin) || wordMatches(date);
				}
				return false;
			}
		});
		fFilteredTree.setInitialText(Messages.LogView_show_filter_initialText);
		fTree = fFilteredTree.getViewer().getTree();
		fTree.setLinesVisible(true);
		createColumns(fTree);
		fFilteredTree.getViewer().setAutoExpandLevel(2);
		fFilteredTree.getViewer().setContentProvider(new LogViewContentProvider(this));
		fFilteredTree.getViewer().setLabelProvider(fLabelProvider = new LogViewLabelProvider(this));
		fLabelProvider.connect(this);
		fFilteredTree.getViewer().addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent e) {
				handleSelectionChanged(e.getSelection());
				if (fPropertiesAction.isEnabled())
					((EventDetailsDialogAction) fPropertiesAction).resetSelection();
			}
		});
		fFilteredTree.getViewer().addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				((EventDetailsDialogAction) fPropertiesAction).setComparator(fComparator);
				fPropertiesAction.run();
			}
		});
		fFilteredTree.getViewer().setInput(this);
		addMouseListeners();
	}

	private void createColumns(Tree tree) {
		fColumn1 = new TreeColumn(tree, SWT.LEFT);
		fColumn1.setText(Messages.LogView_column_message);
		fColumn1.setWidth(fMemento.getInteger(P_COLUMN_1).intValue());
		fColumn1.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				MESSAGE_ORDER *= -1;
				ViewerComparator comparator = getViewerComparator(MESSAGE);
				fFilteredTree.getViewer().setComparator(comparator);
				boolean isComparatorSet = ((EventDetailsDialogAction) fPropertiesAction).resetSelection(MESSAGE, MESSAGE_ORDER);
				setComparator(MESSAGE);
				if (!isComparatorSet)
					((EventDetailsDialogAction) fPropertiesAction).setComparator(fComparator);
				fMemento.putInteger(P_ORDER_VALUE, MESSAGE_ORDER);
				fMemento.putInteger(P_ORDER_TYPE, MESSAGE);
				setColumnSorting(fColumn1, MESSAGE_ORDER);
			}
		});

		fColumn2 = new TreeColumn(tree, SWT.LEFT);
		fColumn2.setText(Messages.LogView_column_plugin);
		fColumn2.setWidth(fMemento.getInteger(P_COLUMN_2).intValue());
		fColumn2.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				PLUGIN_ORDER *= -1;
				ViewerComparator comparator = getViewerComparator(PLUGIN);
				fFilteredTree.getViewer().setComparator(comparator);
				boolean isComparatorSet = ((EventDetailsDialogAction) fPropertiesAction).resetSelection(PLUGIN, PLUGIN_ORDER);
				setComparator(PLUGIN);
				if (!isComparatorSet)
					((EventDetailsDialogAction) fPropertiesAction).setComparator(fComparator);
				fMemento.putInteger(P_ORDER_VALUE, PLUGIN_ORDER);
				fMemento.putInteger(P_ORDER_TYPE, PLUGIN);
				setColumnSorting(fColumn2, PLUGIN_ORDER);
			}
		});

		fColumn3 = new TreeColumn(tree, SWT.LEFT);
		fColumn3.setText(Messages.LogView_column_date);
		fColumn3.setWidth(fMemento.getInteger(P_COLUMN_3).intValue());
		fColumn3.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				DATE_ORDER *= -1;
				ViewerComparator comparator = getViewerComparator(DATE);
				fFilteredTree.getViewer().setComparator(comparator);
				setComparator(DATE);
				((EventDetailsDialogAction) fPropertiesAction).setComparator(fComparator);
				fMemento.putInteger(P_ORDER_VALUE, DATE_ORDER);
				fMemento.putInteger(P_ORDER_TYPE, DATE);
				setColumnSorting(fColumn3, DATE_ORDER);
			}
		});

		tree.setHeaderVisible(true);
	}

	private void initializeViewerSorter() {
		byte orderType = fMemento.getInteger(P_ORDER_TYPE).byteValue();
		ViewerComparator comparator = getViewerComparator(orderType);
		fFilteredTree.getViewer().setComparator(comparator);
		if (orderType == MESSAGE)
			setColumnSorting(fColumn1, MESSAGE_ORDER);
		else if (orderType == PLUGIN)
			setColumnSorting(fColumn2, PLUGIN_ORDER);
		else if (orderType == DATE)
			setColumnSorting(fColumn3, DATE_ORDER);
	}

	private void setColumnSorting(TreeColumn column, int order) {
		fTree.setSortColumn(column);
		fTree.setSortDirection(order == ASCENDING ? SWT.UP : SWT.DOWN);
	}

	public void dispose() {
		writeSettings();
		Platform.removeLogListener(this);
		fClipboard.dispose();
		if (fTextShell != null)
			fTextShell.dispose();
		fLabelProvider.disconnect(this);
		fFilteredTree.dispose();
		super.dispose();
	}

	private void handleImport() {
		FileDialog dialog = new FileDialog(getViewSite().getShell());
		dialog.setFilterExtensions(new String[] {"*.log"}); //$NON-NLS-1$
		if (fDirectory != null)
			dialog.setFilterPath(fDirectory);
		String path = dialog.open();
		if (path == null) { // cancel
			return;
		}

		File file = new Path(path).toFile();
		if (file.exists()) {
			handleImportPath(path);
		} else {
			String msg = NLS.bind(Messages.LogView_FileCouldNotBeFound, file.getName());
			MessageDialog.openError(getViewSite().getShell(), Messages.LogView_OpenFile, msg);
		}
	}

	public void handleImportPath(String path) {
		if (path != null && new Path(path).toFile().exists()) {
			fInputFile = new Path(path).toFile();
			fDirectory = fInputFile.getParent();
			IRunnableWithProgress op = new IRunnableWithProgress() {
				public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
					monitor.beginTask(Messages.LogView_operation_importing, IProgressMonitor.UNKNOWN);
					readLogFile();
				}
			};
			ProgressMonitorDialog pmd = new ProgressMonitorDialog(getViewSite().getShell());
			try {
				pmd.run(true, true, op);
			} catch (InvocationTargetException e) {
			} catch (InterruptedException e) {
			} finally {
				fReadLogAction.setText(Messages.LogView_readLog_reload);
				fReadLogAction.setToolTipText(Messages.LogView_readLog_reload);
				asyncRefresh(false);
				resetDialogButtons();
			}
		}
	}

	private void handleExport() {
		FileDialog dialog = new FileDialog(getViewSite().getShell(), SWT.SAVE);
		dialog.setFilterExtensions(new String[] {"*.log"}); //$NON-NLS-1$
		if (fDirectory != null)
			dialog.setFilterPath(fDirectory);
		String path = dialog.open();
		if (path != null) {
			if (path.indexOf('.') == -1 && !path.endsWith(".log")) //$NON-NLS-1$
				path += ".log"; //$NON-NLS-1$
			File outputFile = new Path(path).toFile();
			fDirectory = outputFile.getParent();
			if (outputFile.exists()) {
				String message = NLS.bind(Messages.LogView_confirmOverwrite_message, outputFile.toString());
				if (!MessageDialog.openQuestion(getViewSite().getShell(), Messages.LogView_exportLog, message))
					return;
			}
			copy(fInputFile, outputFile);
		}
	}

	private void copy(File inputFile, File outputFile) {
		BufferedReader reader = null;
		BufferedWriter writer = null;
		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), "UTF-8")); //$NON-NLS-1$
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8")); //$NON-NLS-1$
			while (reader.ready()) {
				writer.write(reader.readLine());
				writer.write(System.getProperty("line.separator")); //$NON-NLS-1$
			}
		} catch (IOException e) {
		} finally {
			try {
				if (reader != null)
					reader.close();
				if (writer != null)
					writer.close();
			} catch (IOException e1) {
			}
		}
	}

	private void handleFilter() {
		FilterDialog dialog = new FilterDialog(Activator.getDefault().getWorkbench().getActiveWorkbenchWindow().getShell(), fMemento);
		dialog.create();
		dialog.getShell().setText(Messages.LogView_FilterDialog_title);
		if (dialog.open() == Window.OK)
			reloadLog();
	}

	private void doDeleteLog() {
		String title = Messages.LogView_confirmDelete_title;
		String message = Messages.LogView_confirmDelete_message;
		if (!MessageDialog.openConfirm(fTree.getShell(), title, message))
			return;
		if (fInputFile.delete() || elements.size() > 0) {
			elements.clear();
			groups.clear();
			currentSession.removeAllChildren();
			asyncRefresh(false);
			resetDialogButtons();
		}
	}

	public void fillContextMenu(IMenuManager manager) {
	}

	public AbstractEntry[] getElements() {
		return (AbstractEntry[]) elements.toArray(new AbstractEntry[elements.size()]);
	}

	protected void handleClear() {
		BusyIndicator.showWhile(fTree.getDisplay(), new Runnable() {
			public void run() {
				elements.clear();
				groups.clear();
				currentSession.removeAllChildren();
				asyncRefresh(false);
				resetDialogButtons();
			}
		});
	}

	protected void reloadLog() {
		IRunnableWithProgress op = new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
				monitor.beginTask(Messages.LogView_operation_reloading, IProgressMonitor.UNKNOWN);
				readLogFile();
			}
		};
		ProgressMonitorDialog pmd = new ProgressMonitorDialog(getViewSite().getShell());
		try {
			pmd.run(true, true, op);
		} catch (InvocationTargetException e) {
		} catch (InterruptedException e) {
		} finally {
			fReadLogAction.setText(Messages.LogView_readLog_restore);
			fReadLogAction.setToolTipText(Messages.LogView_readLog_restore);
			asyncRefresh(false);
			resetDialogButtons();
		}
	}

	private void readLogFile() {
		if (!fInputFile.exists())
			return;

		elements.clear();
		groups.clear();

		List result = new ArrayList();
		currentSession = LogReader.parseLogFile(fInputFile, result, fMemento);
		group(result);
		limitEntriesCount();
	}

	/**
	 * Add new entries to correct groups in the view.
	 * @param entries new entries to show up in groups in the view.
	 */
	private void group(List entries) {
		if (fMemento.getInteger(P_GROUP_BY).intValue() == GROUP_BY_NONE) {
			elements.addAll(entries);
		} else {
			for (Iterator i = entries.iterator(); i.hasNext();) {
				LogEntry entry = (LogEntry) i.next();
				Group group = getGroup(entry);
				group.addChild(entry);
			}
		}
	}

	/**
	 * Limits the number of entries according to the max entries limit set in
	 * memento.
	 */
	private void limitEntriesCount() {
		int limit = Integer.MAX_VALUE;
		if (fMemento.getString(LogView.P_USE_LIMIT).equals("true")) {//$NON-NLS-1$
			limit = fMemento.getInteger(LogView.P_LOG_LIMIT).intValue();
		}

		int entriesCount = getEntriesCount();

		if (entriesCount <= limit) {
			return;
		} else { // remove oldest
			Comparator dateComparator = new Comparator() {
				public int compare(Object o1, Object o2) {
					Date l1 = ((LogEntry) o1).getDate();
					Date l2 = ((LogEntry) o2).getDate();
					if ((l1 != null) && (l2 != null)) {
						return l1.before(l2) ? -1 : 1;
					} else if ((l1 == null) && (l2 == null)) {
						return 0;
					} else
						return (l1 == null) ? -1 : 1;
				}
			};

			if (fMemento.getInteger(P_GROUP_BY).intValue() == GROUP_BY_NONE) {
				elements.subList(0, elements.size() - limit).clear();
			} else {
				List copy = new ArrayList(entriesCount);
				for (Iterator i = elements.iterator(); i.hasNext();) {
					AbstractEntry group = (AbstractEntry) i.next();
					copy.addAll(Arrays.asList(group.getChildren(group)));
				}

				Collections.sort(copy, dateComparator);
				List toRemove = copy.subList(0, copy.size() - limit);

				for (Iterator i = elements.iterator(); i.hasNext();) {
					AbstractEntry group = (AbstractEntry) i.next();
					group.removeChildren(toRemove);
				}
			}
		}

	}

	private int getEntriesCount() {
		if (fMemento.getInteger(P_GROUP_BY).intValue() == GROUP_BY_NONE) {
			return elements.size();
		} else {
			int size = 0;
			for (Iterator i = elements.iterator(); i.hasNext();) {
				AbstractEntry group = (AbstractEntry) i.next();
				size += group.size();
			}
			return size;
		}
	}

	/**
	 * Returns group appropriate for the entry. Group depends on P_GROUP_BY
	 * preference, or is null if grouping is disabled (GROUP_BY_NONE), or group
	 * could not be determined. May create group if it haven't existed before.
	 * 
	 * @param entry entry to be grouped
	 * @return group or null if grouping is disabled
	 */
	protected Group getGroup(LogEntry entry) {
		int groupBy = fMemento.getInteger(P_GROUP_BY).intValue();
		Object elementGroupId = null;
		String groupName = null;

		switch (groupBy) {
			case GROUP_BY_PLUGIN :
				groupName = entry.getPluginId();
				elementGroupId = groupName;
				break;

			case GROUP_BY_SESSION :
				elementGroupId = entry.getSession();
				break;

			default : // grouping is disabled
				return null;
		}

		if (elementGroupId == null) { // could not determine group
			return null;
		}

		Group group = (Group) groups.get(elementGroupId);
		if (group == null) {
			if (groupBy == GROUP_BY_SESSION) {
				group = entry.getSession();
			} else {
				group = new Group(groupName);
			}
			groups.put(elementGroupId, group);
			elements.add(group);
		}

		return group;
	}

	public void logging(IStatus status, String plugin) {
		if (!fInputFile.equals(Platform.getLogFileLocation().toFile()))
			return;

		if (batchEntries) {
			// create LogEntry immediately to don't loose IStatus creation date.
			LogEntry entry = createLogEntry(status);
			batchedEntries.add(entry);
			return;
		}

		if (fFirstEvent) {
			readLogFile();
			asyncRefresh(true);
			fFirstEvent = false;
		} else {
			LogEntry entry = createLogEntry(status);

			if (!batchedEntries.isEmpty()) {
				// batch new entry as well, to have only one asyncRefresh()
				batchedEntries.add(entry);
				pushBatchedEntries();
			} else {
				pushEntry(entry);
				asyncRefresh(true);
			}
		}
	}

	/**
	 * Push batched entries to log view.
	 */
	private void pushBatchedEntries() {
		Job job = new Job(Messages.LogView_AddingBatchedEvents) {
			protected IStatus run(IProgressMonitor monitor) {
				for (int i = 0; i < batchedEntries.size(); i++) {
					if (!monitor.isCanceled()) {
						LogEntry entry = (LogEntry) batchedEntries.get(i);
						pushEntry(entry);
						batchedEntries.remove(i);
					}
				}
				asyncRefresh(true);
				return Status.OK_STATUS;
			}
		};
		job.schedule();
	}

	private LogEntry createLogEntry(IStatus status) {
		LogEntry entry = new LogEntry(status);
		entry.setSession(currentSession);
		return entry;
	}

	private synchronized void pushEntry(LogEntry entry) {
		if (LogReader.isLogged(entry, fMemento)) {
			group(Collections.singletonList(entry));
			limitEntriesCount();
		}
		asyncRefresh(true);
	}

	private void asyncRefresh(final boolean activate) {
		if (fTree.isDisposed())
			return;
		Display display = fTree.getDisplay();
		final ViewPart view = this;
		if (display != null) {
			display.asyncExec(new Runnable() {
				public void run() {
					if (!fTree.isDisposed()) {
						fFilteredTree.getViewer().refresh();
						fFilteredTree.getViewer().expandToLevel(2);
						fDeleteLogAction.setEnabled(fInputFile.exists() && fInputFile.equals(Platform.getLogFileLocation().toFile()));
						fOpenLogAction.setEnabled(fInputFile.exists());
						fExportAction.setEnabled(fInputFile.exists());
						if (activate && fActivateViewAction.isChecked()) {
							IWorkbenchPage page = Activator.getDefault().getWorkbench().getActiveWorkbenchWindow().getActivePage();
							if (page != null)
								page.bringToTop(view);
						}
					}
				}
			});
		}
	}

	public void setFocus() {
		if (fFilteredTree != null && !fFilteredTree.isDisposed())
			fFilteredTree.setFocus();
	}

	private void handleSelectionChanged(ISelection selection) {
		updateStatus(selection);
		fCopyAction.setEnabled((!selection.isEmpty()) && ((IStructuredSelection) selection).getFirstElement() instanceof LogEntry);
		fPropertiesAction.setEnabled(!selection.isEmpty());
	}

	private void updateStatus(ISelection selection) {
		IStatusLineManager status = getViewSite().getActionBars().getStatusLineManager();
		if (selection.isEmpty())
			status.setMessage(null);
		else {
			Object element = ((IStructuredSelection) selection).getFirstElement();
			status.setMessage(((LogViewLabelProvider) fFilteredTree.getViewer().getLabelProvider()).getColumnText(element, 0));
		}
	}

	private void copyToClipboard(ISelection selection) {
		StringWriter writer = new StringWriter();
		PrintWriter pwriter = new PrintWriter(writer);
		if (selection.isEmpty())
			return;
		LogEntry entry = (LogEntry) ((IStructuredSelection) selection).getFirstElement();
		entry.write(pwriter);
		pwriter.flush();
		String textVersion = writer.toString();
		try {
			pwriter.close();
			writer.close();
		} catch (IOException e) {
		}
		if (textVersion.trim().length() > 0) {
			// set the clipboard contents
			fClipboard.setContents(new Object[] {textVersion}, new Transfer[] {TextTransfer.getInstance()});
		}
	}

	public void init(IViewSite site, IMemento memento) throws PartInitException {
		super.init(site, memento);
		if (memento == null)
			this.fMemento = XMLMemento.createWriteRoot("LOGVIEW"); //$NON-NLS-1$
		else
			this.fMemento = memento;
		readSettings();

		// initialize column ordering 
		final byte type = this.fMemento.getInteger(P_ORDER_TYPE).byteValue();
		switch (type) {
			case DATE :
				DATE_ORDER = this.fMemento.getInteger(P_ORDER_VALUE).intValue();
				MESSAGE_ORDER = DESCENDING;
				PLUGIN_ORDER = DESCENDING;
				break;
			case MESSAGE :
				MESSAGE_ORDER = this.fMemento.getInteger(P_ORDER_VALUE).intValue();
				DATE_ORDER = DESCENDING;
				PLUGIN_ORDER = DESCENDING;
				break;
			case PLUGIN :
				PLUGIN_ORDER = this.fMemento.getInteger(P_ORDER_VALUE).intValue();
				MESSAGE_ORDER = DESCENDING;
				DATE_ORDER = DESCENDING;
				break;
			default :
				DATE_ORDER = DESCENDING;
				MESSAGE_ORDER = DESCENDING;
				PLUGIN_ORDER = DESCENDING;
		}
		setComparator(fMemento.getInteger(P_ORDER_TYPE).byteValue());
	}

	private void initializeMemento() {
		if (fMemento.getString(P_USE_LIMIT) == null) {
			fMemento.putString(P_USE_LIMIT, "true"); //$NON-NLS-1$
		}
		if (fMemento.getInteger(P_LOG_LIMIT) == null) {
			fMemento.putInteger(P_LOG_LIMIT, 50);
		}
		if (fMemento.getString(P_LOG_INFO) == null) {
			fMemento.putString(P_LOG_INFO, "true"); //$NON-NLS-1$
		}
		if (fMemento.getString(P_LOG_WARNING) == null) {
			fMemento.putString(P_LOG_WARNING, "true"); //$NON-NLS-1$
		}
		if (fMemento.getString(P_LOG_ERROR) == null) {
			fMemento.putString(P_LOG_ERROR, "true"); //$NON-NLS-1$
		}
		if (fMemento.getString(P_SHOW_ALL_SESSIONS) == null) {
			fMemento.putString(P_SHOW_ALL_SESSIONS, "true"); //$NON-NLS-1$
		}
		Integer width = fMemento.getInteger(P_COLUMN_1);
		if (width == null || width.intValue() == 0) {
			fMemento.putInteger(P_COLUMN_1, 300);
		}
		width = fMemento.getInteger(P_COLUMN_2);
		if (width == null || width.intValue() == 0) {
			fMemento.putInteger(P_COLUMN_2, 150);
		}
		width = fMemento.getInteger(P_COLUMN_3);
		if (width == null || width.intValue() == 0) {
			fMemento.putInteger(P_COLUMN_3, 150);
		}
		if (fMemento.getString(P_ACTIVATE) == null) {
			fMemento.putString(P_ACTIVATE, "true"); //$NON-NLS-1$
		}
		if (fMemento.getBoolean(P_SHOW_FILTER_TEXT) == null) {
			fMemento.putBoolean(P_SHOW_FILTER_TEXT, true);
		}
		fMemento.putInteger(P_ORDER_VALUE, DESCENDING);
		fMemento.putInteger(P_ORDER_TYPE, DATE);
		if (fMemento.getInteger(P_GROUP_BY) == null) {
			fMemento.putInteger(P_GROUP_BY, GROUP_BY_NONE);
		}
	}

	public void saveState(IMemento memento) {
		if (this.fMemento == null || memento == null)
			return;
		this.fMemento.putInteger(P_COLUMN_1, fColumn1.getWidth());
		this.fMemento.putInteger(P_COLUMN_2, fColumn2.getWidth());
		this.fMemento.putInteger(P_COLUMN_3, fColumn3.getWidth());
		this.fMemento.putString(P_ACTIVATE, fActivateViewAction.isChecked() ? "true" : "false"); //$NON-NLS-1$ //$NON-NLS-2$
		memento.putMemento(this.fMemento);
		writeSettings();
	}

	private void addMouseListeners() {
		Listener tableListener = new Listener() {
			public void handleEvent(Event e) {
				switch (e.type) {
					case SWT.MouseMove :
						onMouseMove(e);
						break;
					case SWT.MouseHover :
						onMouseHover(e);
						break;
					case SWT.MouseDown :
						onMouseDown(e);
						break;
				}
			}
		};
		int[] tableEvents = new int[] {SWT.MouseDown, SWT.MouseMove, SWT.MouseHover};
		for (int i = 0; i < tableEvents.length; i++) {
			fTree.addListener(tableEvents[i], tableListener);
		}
	}

	private void makeHoverShell() {
		fTextShell = new Shell(fTree.getShell(), SWT.NO_FOCUS | SWT.ON_TOP | SWT.TOOL);
		Display display = fTextShell.getDisplay();
		fTextShell.setBackground(display.getSystemColor(SWT.COLOR_INFO_BACKGROUND));
		GridLayout layout = new GridLayout(1, false);
		int border = ((fTree.getShell().getStyle() & SWT.NO_TRIM) == 0) ? 0 : 1;
		layout.marginHeight = border;
		layout.marginWidth = border;
		fTextShell.setLayout(layout);
		fTextShell.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		Composite shellComposite = new Composite(fTextShell, SWT.NONE);
		layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		shellComposite.setLayout(layout);
		shellComposite.setLayoutData(new GridData(GridData.FILL_BOTH | GridData.VERTICAL_ALIGN_BEGINNING));
		fTextLabel = new Text(shellComposite, SWT.WRAP | SWT.MULTI | SWT.READ_ONLY);
		GridData gd = new GridData(GridData.FILL_BOTH);
		gd.widthHint = 100;
		gd.grabExcessHorizontalSpace = true;
		fTextLabel.setLayoutData(gd);
		Color c = fTree.getDisplay().getSystemColor(SWT.COLOR_INFO_BACKGROUND);
		fTextLabel.setBackground(c);
		c = fTree.getDisplay().getSystemColor(SWT.COLOR_INFO_FOREGROUND);
		fTextLabel.setForeground(c);
		fTextLabel.setEditable(false);
		fTextShell.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				onTextShellDispose(e);
			}
		});
	}

	void onTextShellDispose(DisposeEvent e) {
		fCanOpenTextShell = true;
		setFocus();
	}

	void onMouseDown(Event e) {
		if (fTextShell != null && !fTextShell.isDisposed() && !fTextShell.isFocusControl()) {
			fTextShell.setVisible(false);
			fCanOpenTextShell = true;
		}
	}

	void onMouseHover(Event e) {
		if (!fCanOpenTextShell || fTextShell == null || fTextShell.isDisposed())
			return;
		fCanOpenTextShell = false;
		Point point = new Point(e.x, e.y);
		TreeItem item = fTree.getItem(point);
		if (item == null)
			return;

		String message = null;
		if (item.getData() instanceof LogEntry) {
			message = ((LogEntry) item.getData()).getStack();
		} else if (item.getData() instanceof LogSession) {
			LogSession session = ((LogSession) item.getData());
			message = Messages.LogView_SessionStarted;
			if (session.getDate() != null) {
				DateFormat formatter = new SimpleDateFormat(LogEntry.F_DATE_FORMAT);
				message += formatter.format(session.getDate());
			}
		}

		if (message == null)
			return;

		fTextLabel.setText(message);
		Rectangle bounds = fTree.getDisplay().getBounds();
		Point cursorPoint = fTree.getDisplay().getCursorLocation();
		int x = point.x;
		int y = point.y + 25;
		int width = fTree.getColumn(0).getWidth();
		int height = 125;
		if (cursorPoint.x + width > bounds.width)
			x -= width;
		if (cursorPoint.y + height + 25 > bounds.height)
			y -= height + 27;

		fTextShell.setLocation(fTree.toDisplay(x, y));
		fTextShell.setSize(width, height);
		fTextShell.setVisible(true);
	}

	void onMouseMove(Event e) {
		if (fTextShell != null && !fTextShell.isDisposed() && fTextShell.isVisible())
			fTextShell.setVisible(false);

		Point point = new Point(e.x, e.y);
		TreeItem item = fTree.getItem(point);
		if (item == null)
			return;
		Image image = item.getImage();
		Object data = item.getData();
		if (data instanceof LogEntry) {
			LogEntry entry = (LogEntry) data;
			int parentCount = getNumberOfParents(entry);
			int startRange = 20 + Math.max(image.getBounds().width + 2, 7 + 2) * parentCount;
			int endRange = startRange + 16;
			fCanOpenTextShell = e.x >= startRange && e.x <= endRange;
		}
	}

	private int getNumberOfParents(AbstractEntry entry) {
		AbstractEntry parent = (AbstractEntry) entry.getParent(entry);
		if (parent == null)
			return 0;
		return 1 + getNumberOfParents(parent);
	}

	public Comparator getComparator() {
		return fComparator;
	}

	private void setComparator(byte sortType) {
		if (sortType == DATE) {
			fComparator = new Comparator() {
				public int compare(Object e1, Object e2) {
					long date1 = 0;
					long date2 = 0;
					if ((e1 instanceof LogEntry) && (e2 instanceof LogEntry)) {
						date1 = ((LogEntry) e1).getDate().getTime();
						date2 = ((LogEntry) e2).getDate().getTime();
					} else if ((e1 instanceof LogSession) && (e2 instanceof LogSession)) {
						date1 = ((LogSession) e1).getDate() == null ? 0 : ((LogSession) e1).getDate().getTime();
						date2 = ((LogSession) e2).getDate() == null ? 0 : ((LogSession) e2).getDate().getTime();
					}
					if (date1 == date2) {
						int result = elements.indexOf(e2) - elements.indexOf(e1);
						if (DATE_ORDER == DESCENDING)
							result *= DESCENDING;
						return result;
					}
					if (DATE_ORDER == DESCENDING)
						return date1 > date2 ? DESCENDING : ASCENDING;
					return date1 < date2 ? DESCENDING : ASCENDING;
				}
			};
		} else if (sortType == PLUGIN) {
			fComparator = new Comparator() {
				public int compare(Object e1, Object e2) {
					if ((e1 instanceof LogEntry) && (e2 instanceof LogEntry)) {
						LogEntry entry1 = (LogEntry) e1;
						LogEntry entry2 = (LogEntry) e2;
						return getDefaultComparator().compare(entry1.getPluginId(), entry2.getPluginId()) * PLUGIN_ORDER;
					}
					return 0;
				}
			};
		} else {
			fComparator = new Comparator() {
				public int compare(Object e1, Object e2) {
					if ((e1 instanceof LogEntry) && (e2 instanceof LogEntry)) {
						LogEntry entry1 = (LogEntry) e1;
						LogEntry entry2 = (LogEntry) e2;
						return getDefaultComparator().compare(entry1.getMessage(), entry2.getMessage()) * MESSAGE_ORDER;
					}
					return 0;
				}
			};
		}
	}

	private Comparator getDefaultComparator() {
		return Policy.getComparator();
	}

	private ViewerComparator getViewerComparator(byte sortType) {
		if (sortType == PLUGIN) {
			return new ViewerComparator() {
				public int compare(Viewer viewer, Object e1, Object e2) {
					if ((e1 instanceof LogEntry) && (e2 instanceof LogEntry)) {
						LogEntry entry1 = (LogEntry) e1;
						LogEntry entry2 = (LogEntry) e2;
						return getComparator().compare(entry1.getPluginId(), entry2.getPluginId()) * PLUGIN_ORDER;
					}
					return 0;
				}
			};
		} else if (sortType == MESSAGE) {
			return new ViewerComparator() {
				public int compare(Viewer viewer, Object e1, Object e2) {
					if ((e1 instanceof LogEntry) && (e2 instanceof LogEntry)) {
						LogEntry entry1 = (LogEntry) e1;
						LogEntry entry2 = (LogEntry) e2;
						return getComparator().compare(entry1.getMessage(), entry2.getMessage()) * MESSAGE_ORDER;
					}
					return 0;
				}
			};
		} else {
			return new ViewerComparator() {
				public int compare(Viewer viewer, Object e1, Object e2) {
					long date1 = 0;
					long date2 = 0;
					if ((e1 instanceof LogEntry) && (e2 instanceof LogEntry)) {
						date1 = ((LogEntry) e1).getDate().getTime();
						date2 = ((LogEntry) e2).getDate().getTime();
					} else if ((e1 instanceof LogSession) && (e2 instanceof LogSession)) {
						date1 = ((LogSession) e1).getDate() == null ? 0 : ((LogSession) e1).getDate().getTime();
						date2 = ((LogSession) e2).getDate() == null ? 0 : ((LogSession) e2).getDate().getTime();
					}

					if (date1 == date2) {
						int result = elements.indexOf(e2) - elements.indexOf(e1);
						if (DATE_ORDER == DESCENDING)
							result *= DESCENDING;
						return result;
					}
					if (DATE_ORDER == DESCENDING)
						return date1 > date2 ? DESCENDING : ASCENDING;
					return date1 < date2 ? DESCENDING : ASCENDING;
				}
			};
		}
	}

	private void resetDialogButtons() {
		((EventDetailsDialogAction) fPropertiesAction).resetDialogButtons();
	}

	/**
	 * Returns the filter dialog settings object used to maintain
	 * state between filter dialogs
	 * @return the dialog settings to be used
	 */
	private IDialogSettings getLogSettings() {
		IDialogSettings settings = Activator.getDefault().getDialogSettings();
		return settings.getSection(getClass().getName());
	}

	/**
	 * Returns the plugin preferences used to maintain
	 * state of log view
	 * @return the plugin preferences
	 */
	private Preferences getLogPreferences() {
		return Activator.getDefault().getPluginPreferences();
	}

	private void readSettings() {
		IDialogSettings s = getLogSettings();
		Preferences p = getLogPreferences();
		if (s == null || p == null) {
			initializeMemento();
			return;
		}
		try {
			fMemento.putString(P_USE_LIMIT, s.getBoolean(P_USE_LIMIT) ? "true" : "false"); //$NON-NLS-1$ //$NON-NLS-2$
			fMemento.putString(P_LOG_INFO, s.getBoolean(P_LOG_INFO) ? "true" : "false"); //$NON-NLS-1$ //$NON-NLS-2$
			fMemento.putString(P_LOG_WARNING, s.getBoolean(P_LOG_WARNING) ? "true" : "false"); //$NON-NLS-1$ //$NON-NLS-2$
			fMemento.putString(P_LOG_ERROR, s.getBoolean(P_LOG_ERROR) ? "true" : "false"); //$NON-NLS-1$ //$NON-NLS-2$
			fMemento.putString(P_SHOW_ALL_SESSIONS, s.getBoolean(P_SHOW_ALL_SESSIONS) ? "true" : "false"); //$NON-NLS-1$ //$NON-NLS-2$
			fMemento.putInteger(P_LOG_LIMIT, s.getInt(P_LOG_LIMIT));
			fMemento.putInteger(P_COLUMN_1, p.getInt(P_COLUMN_1) > 0 ? p.getInt(P_COLUMN_1) : 300);
			fMemento.putInteger(P_COLUMN_2, p.getInt(P_COLUMN_2) > 0 ? p.getInt(P_COLUMN_2) : 150);
			fMemento.putInteger(P_COLUMN_3, p.getInt(P_COLUMN_3) > 0 ? p.getInt(P_COLUMN_3) : 150);
			fMemento.putString(P_ACTIVATE, p.getBoolean(P_ACTIVATE) ? "true" : "false"); //$NON-NLS-1$ //$NON-NLS-2$
			int order = p.getInt(P_ORDER_VALUE);
			fMemento.putInteger(P_ORDER_VALUE, order == 0 ? DESCENDING : order);
			fMemento.putInteger(P_ORDER_TYPE, p.getInt(P_ORDER_TYPE));
			fMemento.putBoolean(P_SHOW_FILTER_TEXT, p.getBoolean(P_SHOW_FILTER_TEXT));
			fMemento.putInteger(P_GROUP_BY, p.getInt(P_GROUP_BY));
		} catch (NumberFormatException e) {
			fMemento.putInteger(P_LOG_LIMIT, 50);
			fMemento.putInteger(P_COLUMN_1, 300);
			fMemento.putInteger(P_COLUMN_2, 150);
			fMemento.putInteger(P_COLUMN_3, 150);
			fMemento.putInteger(P_ORDER_TYPE, DATE);
			fMemento.putInteger(P_ORDER_VALUE, DESCENDING);
			fMemento.putInteger(P_GROUP_BY, GROUP_BY_NONE);
		}
	}

	private void writeSettings() {
		writeViewSettings();
		writeFilterSettings();
	}

	private void writeFilterSettings() {
		IDialogSettings settings = getLogSettings();
		if (settings == null)
			settings = Activator.getDefault().getDialogSettings().addNewSection(getClass().getName());
		settings.put(P_USE_LIMIT, fMemento.getString(P_USE_LIMIT).equals("true")); //$NON-NLS-1$
		settings.put(P_LOG_LIMIT, fMemento.getInteger(P_LOG_LIMIT).intValue());
		settings.put(P_LOG_INFO, fMemento.getString(P_LOG_INFO).equals("true")); //$NON-NLS-1$
		settings.put(P_LOG_WARNING, fMemento.getString(P_LOG_WARNING).equals("true")); //$NON-NLS-1$
		settings.put(P_LOG_ERROR, fMemento.getString(P_LOG_ERROR).equals("true")); //$NON-NLS-1$
		settings.put(P_SHOW_ALL_SESSIONS, fMemento.getString(P_SHOW_ALL_SESSIONS).equals("true")); //$NON-NLS-1$
	}

	private void writeViewSettings() {
		Preferences preferences = getLogPreferences();
		preferences.setValue(P_COLUMN_1, fMemento.getInteger(P_COLUMN_1).intValue());
		preferences.setValue(P_COLUMN_2, fMemento.getInteger(P_COLUMN_2).intValue());
		preferences.setValue(P_COLUMN_3, fMemento.getInteger(P_COLUMN_3).intValue());
		preferences.setValue(P_ACTIVATE, fMemento.getString(P_ACTIVATE).equals("true")); //$NON-NLS-1$
		int order = fMemento.getInteger(P_ORDER_VALUE).intValue();
		preferences.setValue(P_ORDER_VALUE, order == 0 ? DESCENDING : order);
		preferences.setValue(P_ORDER_TYPE, fMemento.getInteger(P_ORDER_TYPE).intValue());
		preferences.setValue(P_SHOW_FILTER_TEXT, fMemento.getBoolean(P_SHOW_FILTER_TEXT).booleanValue());
		preferences.setValue(P_GROUP_BY, fMemento.getInteger(P_GROUP_BY).intValue());
	}

	public void sortByDateDescending() {
		setColumnSorting(fColumn3, DESCENDING);
	}

	protected Job getOpenLogFileJob() {
		final Shell shell = getViewSite().getShell();
		return new Job(Messages.OpenLogDialog_message) {
			protected IStatus run(IProgressMonitor monitor) {
				boolean failed = false;
				if (fInputFile.length() <= LogReader.MAX_FILE_LENGTH) {
					failed = !Program.launch(fInputFile.getAbsolutePath());
					if (failed) {
						Program p = Program.findProgram(".txt"); //$NON-NLS-1$
						if (p != null) {
							p.execute(fInputFile.getAbsolutePath());
							return Status.OK_STATUS;
						}
					}
				}
				if (failed) {
					final OpenLogDialog openDialog = new OpenLogDialog(shell, fInputFile);
					Display.getDefault().asyncExec(new Runnable() {
						public void run() {
							openDialog.create();
							openDialog.open();
						}
					});
				}
				return Status.OK_STATUS;
			}
		};
	}

	protected File getLogFile() {
		return fInputFile;
	}

	public boolean isCurrentLogSession(LogSession session) {
		return (fInputFile.equals(Platform.getLogFileLocation().toFile())) && (currentSession != null) && (currentSession.equals(session));
	}
}
