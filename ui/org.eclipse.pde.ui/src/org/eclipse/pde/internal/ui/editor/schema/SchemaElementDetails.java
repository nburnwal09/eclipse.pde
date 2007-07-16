/*******************************************************************************
 * Copyright (c) 2000, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.editor.schema;

import java.util.ArrayList;

import org.eclipse.osgi.util.NLS;
import org.eclipse.pde.core.IModelChangedEvent;
import org.eclipse.pde.internal.core.ischema.IMetaAttribute;
import org.eclipse.pde.internal.core.ischema.ISchema;
import org.eclipse.pde.internal.core.ischema.ISchemaAttribute;
import org.eclipse.pde.internal.core.ischema.ISchemaComplexType;
import org.eclipse.pde.internal.core.ischema.ISchemaObject;
import org.eclipse.pde.internal.core.schema.Schema;
import org.eclipse.pde.internal.core.schema.SchemaElement;
import org.eclipse.pde.internal.core.schema.SchemaElementReference;
import org.eclipse.pde.internal.ui.PDEUIMessages;
import org.eclipse.pde.internal.ui.editor.FormEntryAdapter;
import org.eclipse.pde.internal.ui.parts.ComboPart;
import org.eclipse.pde.internal.ui.parts.FormEntry;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.forms.IFormColors;
import org.eclipse.ui.forms.widgets.FormToolkit;

public class SchemaElementDetails extends AbstractSchemaDetails {

	private SchemaElement fElement;
	private FormEntry fName;
	private ComboPart fLabelProperty;
	private ComboPart fIcon;
	private Button fDepTrue;
	private Button fDepFalse;
	private Button fTransTrue;
	private Button fTransFalse;
	
	public SchemaElementDetails(ElementSection section) {
		super(section, true);
	}

	public void createDetails(Composite parent) {
		FormToolkit toolkit = getManagedForm().getToolkit();
		Color foreground = toolkit.getColors().getColor(IFormColors.TITLE);
		
		fName = new FormEntry(parent, toolkit, PDEUIMessages.SchemaDetails_name, SWT.NONE);
		
		Label label = toolkit.createLabel(parent, PDEUIMessages.SchemaDetails_deprecated);
		label.setForeground(foreground);
		Button[] buttons = createTrueFalseButtons(parent, toolkit, 2);
		fDepTrue = buttons[0];
		fDepFalse = buttons[1];
		
		label = toolkit.createLabel(parent, PDEUIMessages.SchemaElementDetails_labelProperty);
		label.setForeground(foreground);
		fLabelProperty = createComboPart(parent, toolkit, new String[0], 2);
		
		label = toolkit.createLabel(parent, PDEUIMessages.SchemaElementDetails_icon);
		label.setForeground(foreground);
		fIcon = createComboPart(parent, toolkit, new String[0], 2);

		label = toolkit.createLabel(parent, PDEUIMessages.SchemaDetails_translatable);
		label.setForeground(foreground);
		buttons = createTrueFalseButtons(parent, toolkit, 2);
		fTransTrue = buttons[0];
		fTransFalse = buttons[1];
		
		setText(PDEUIMessages.SchemaElementDetails_title);
	}

	public void updateFields(ISchemaObject object) {
		if (object instanceof SchemaElementReference)
			object = ((SchemaElementReference)object).getReferencedObject();
		fElement = (SchemaElement)object;
		if (fElement == null)
			return;
		setDecription(NLS.bind(PDEUIMessages.SchemaElementDetails_description, fElement.getName()));
		fName.setValue(fElement.getName(), true);
		String labProp = fElement.getLabelProperty();
		fLabelProperty.setItems(getLabelItems());
		fLabelProperty.setText(labProp != null ? labProp : ""); //$NON-NLS-1$
		String icProp = fElement.getIconProperty();
		fIcon.setItems(getIconItems());
		fIcon.setText(icProp != null ? icProp : ""); //$NON-NLS-1$
		
		fDepTrue.setSelection(fElement.isDeprecated());
		fDepFalse.setSelection(!fElement.isDeprecated());
		
		boolean isTranslatable = true;
		if ((fElement.getType() instanceof ISchemaComplexType &&
				((ISchemaComplexType)fElement.getType()).getCompositor() != null) ||
				fElement.getAttributeCount() != 0)
			isTranslatable = false;

		fTransTrue.setSelection(fElement.hasTranslatableContent());
		fTransFalse.setSelection(!fElement.hasTranslatableContent());
		
		boolean editable = isEditableElement();
		fIcon.setEnabled(editable);
		fLabelProperty.setEnabled(editable);
		fName.setEditable(editable);
		
		fDepTrue.setEnabled(editable);
		fDepFalse.setEnabled(editable);
		fTransTrue.setEnabled(editable && isTranslatable);
		fTransFalse.setEnabled(editable && isTranslatable);
	}

	public void hookListeners() {
		fIcon.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (blockListeners())
					return;
				String icon = fIcon.getSelection();
				if (icon == null || icon.equals("")) //$NON-NLS-1$
					fElement.setIconProperty(null);
				else
					fElement.setIconProperty(icon);
			}
		});
		fLabelProperty.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (blockListeners())
					return;
				String label = fLabelProperty.getSelection();
				if (label == null || label.equals("")) //$NON-NLS-1$
					fElement.setLabelProperty(null);
				else
					fElement.setLabelProperty(label);
			}
		});
		fName.setFormEntryListener(new FormEntryAdapter(this) {
			public void textValueChanged(FormEntry entry) {
				if (blockListeners())
					return;
				if (fName.getValue().length() != 0) {
					fElement.setName(fName.getValue());
					((Schema)fElement.getSchema()).updateReferencesFor(fElement, ISchema.REFRESH_RENAME);
					setDecription(NLS.bind(PDEUIMessages.SchemaElementDetails_description, fElement.getName()));
				} else {
					fName.setValue(fElement.getName(),true);
				}
			}
		});
		fDepTrue.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (blockListeners())
					return;
				fElement.setDeprecatedProperty(fDepTrue.getSelection());
			}
		});
		fTransTrue.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (blockListeners())
					return;
				fElement.setTranslatableProperty(fTransTrue.getSelection());
			}
		});
	}
	
	private String[] getIconItems() {
		ISchemaAttribute[] attribs = fElement.getAttributes();
		ArrayList list = new ArrayList();
		list.add(""); //$NON-NLS-1$
		for (int i = 0; i < attribs.length; i++) {
			if (attribs[i].getKind() == IMetaAttribute.RESOURCE) {
				list.add(attribs[i].getName());
			}
		}
		return (String[]) list.toArray(new String[list.size()]);
	}
	
	private String[] getLabelItems() {
		ISchemaAttribute[] attribs = fElement.getAttributes();
		String[] labels = new String[attribs.length + 1];
		labels[0] = ""; //$NON-NLS-1$
		for (int i = 0; i < attribs.length; i++) {
			labels[i + 1] = attribs[i].getName();
		}
		return labels;
	}
	
	public void modelChanged(IModelChangedEvent event) {
		Object[] changedObjs = event.getChangedObjects();
		if(event.getChangeType() == IModelChangedEvent.INSERT && changedObjs.length > 0) {
			if (changedObjs[0] instanceof SchemaElement) {	
				fName.getText().setFocus();
		    }
		}
		super.modelChanged(event);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.forms.AbstractFormPart#commit(boolean)
	 */
	public void commit(boolean onSave) {
		super.commit(onSave);
		// Only required for form entries
		fName.commit();
	}	
}
