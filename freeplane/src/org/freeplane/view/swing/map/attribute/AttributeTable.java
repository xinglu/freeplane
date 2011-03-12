/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2008 Dimitry Polivaev
 *
 *  This file author is Dimitry Polivaev
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.freeplane.view.swing.map.attribute;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.KeyboardFocusManager;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.net.URI;
import java.util.EventObject;

import javax.swing.AbstractCellEditor;
import javax.swing.ComboBoxEditor;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableModelEvent;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;

import org.freeplane.core.controller.Controller;
import org.freeplane.core.frame.ViewController;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.FreeplaneDate;
import org.freeplane.features.common.attribute.AttributeRegistry;
import org.freeplane.features.common.attribute.AttributeTableLayoutModel;
import org.freeplane.features.common.attribute.ColumnWidthChangeEvent;
import org.freeplane.features.common.attribute.IAttributeTableModel;
import org.freeplane.features.common.attribute.IColumnWidthChangeListener;
import org.freeplane.features.common.attribute.NodeAttributeTableModel;
import org.freeplane.features.common.icon.IconController;
import org.freeplane.features.common.map.MapController;
import org.freeplane.features.common.map.NodeModel;
import org.freeplane.features.common.text.TextController;
import org.freeplane.features.common.url.UrlManager;
import org.freeplane.features.mindmapmode.text.EditNodeBase;
import org.freeplane.features.mindmapmode.text.EditNodeBase.IEditControl;
import org.freeplane.features.mindmapmode.text.IEditBaseCreator.EditedComponent;
import org.freeplane.features.mindmapmode.text.MTextController;
import org.freeplane.view.swing.map.MapView;
import org.freeplane.view.swing.map.NodeView;

/**
 * @author Dimitry Polivaev
 */
class AttributeTable extends JTable implements IColumnWidthChangeListener {
	private static final String EDITING_STOPPED = AttributeTable.class.getName() + ".editingStopped";
	private static int CLICK_COUNT_TO_START = 2;

	static private class HeaderMouseListener extends MouseAdapter {
		@Override
		public void mouseReleased(final MouseEvent e) {
			final JTableHeader header = (JTableHeader) e.getSource();
			final AttributeTable table = (AttributeTable) header.getTable();
			final float zoom = table.attributeView.getMapView().getZoom();
			final AttributeTableModelDecoratorAdapter model = (AttributeTableModelDecoratorAdapter) table
			.getModel();
			for (int col = 0; col < table.getColumnCount(); col++) {
				final int modelColumnWidth = model.getColumnWidth(col);
				final int currentColumnWidth = (int) (table.getColumnModel().getColumn(col).getWidth() / zoom);
				if (modelColumnWidth != currentColumnWidth) {
					model.setColumnWidth(col, currentColumnWidth);
				}
			}
		}
	}

	static private class MyFocusListener implements FocusListener {
		private AttributeTable focusedTable;

		/*
		 * (non-Javadoc)
		 * @see
		 * java.awt.event.FocusListener#focusGained(java.awt.event.FocusEvent)
		 */
		public void focusGained(final FocusEvent event) {
			final Component source = (Component) event.getSource();
			event.getOppositeComponent();
			if (source instanceof AttributeTable) {
				focusedTable = (AttributeTable) source;
			}
			else {
				focusedTable = (AttributeTable) SwingUtilities.getAncestorOfClass(AttributeTable.class, source);
			}
			EventQueue.invokeLater(new Runnable() {
				public void run() {
					if (focusedTable != null) {
						final Component newNodeViewInFocus = SwingUtilities.getAncestorOfClass(NodeView.class,
						    focusedTable);
						if (newNodeViewInFocus != null) {
							final NodeView viewer = (NodeView) newNodeViewInFocus;
							if (viewer != viewer.getMap().getSelected()) {
								viewer.getMap().selectAsTheOnlyOneSelected(viewer, false);
							}
						}
					}
				}
			});
		}

		/*
		 * (non-Javadoc)
		 * @see
		 * java.awt.event.FocusListener#focusLost(java.awt.event.FocusEvent)
		 */
		public void focusLost(final FocusEvent event) {
			if (event.isTemporary()) {
				return;
			}
			final Component oppositeComponent = event.getOppositeComponent();
			if (oppositeComponent == null) {
				return;
			}
			final Component newTable;
			if (oppositeComponent instanceof AttributeTable) {
				newTable = oppositeComponent;
			}
			else {
				newTable = SwingUtilities.getAncestorOfClass(AttributeTable.class, oppositeComponent);
			}
			if (focusedTable == null) {
				return;
			}
			if (focusedTable != newTable) {
				if (focusedTable.isEditing()) {
					focusedTable.getCellEditor().stopCellEditing();
				}
				if (!focusedTable.attributeView.isPopupShown()) {
					final AttributeView attributeView = focusedTable.getAttributeView();
					final String currentAttributeViewType = AttributeRegistry.getRegistry(
					    attributeView.getNode().getMap()).getAttributeViewType();
					if (attributeView.getViewType() != currentAttributeViewType) {
						attributeView.stateChanged(null);
					}
				}
				focusedTable = null;
				return;
			}
		}
	}

	static private MouseListener componentListener = new HeaderMouseListener();
	static private ComboBoxModel defaultComboBoxModel = null;
	static private AttributeTableCellRenderer dtcr = new AttributeTableCellRenderer();
	private static final int EXTRA_HEIGHT = 4;
	static private MyFocusListener focusListener = new MyFocusListener();
	static private CursorUpdater cursorUpdater = new CursorUpdater();
	private static final int MAX_HEIGTH = 300;
	private static final int MAX_WIDTH = 300;
	private static final long serialVersionUID = 1L;
	private static final float TABLE_ROW_HEIGHT = 4;

	static ComboBoxModel getDefaultComboBoxModel() {
		if (AttributeTable.defaultComboBoxModel == null) {
			AttributeTable.defaultComboBoxModel = new DefaultComboBoxModel();
		}
		return AttributeTable.defaultComboBoxModel;
	}

	final private AttributeView attributeView;
	private int highRowIndex = 0;
	private static DefaultCellEditor dce;

	AttributeTable(final AttributeView attributeView) {
		super();
		this.attributeView = attributeView;
		addFocusListener(AttributeTable.focusListener);
		addMouseListener(AttributeTable.cursorUpdater);
		addMouseMotionListener(AttributeTable.cursorUpdater);
		final JTableHeader tableHeader = getTableHeader();
		final TableCellRenderer defaultRenderer = tableHeader.getDefaultRenderer();
		tableHeader.setDefaultRenderer(new TableCellRenderer() {
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
			                                               int row, int column) {
				final Component c = defaultRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				final int height = (int) (((AttributeTable)table).getZoom() * 6);
				final Dimension preferredSize = new Dimension(1, height);
				c.setPreferredSize(preferredSize);
				return c;
				
			}
		});
		setTableHeader(tableHeader);
		if (attributeView.getMapView().getModeController().canEdit()) {
			tableHeader.addMouseListener(AttributeTable.componentListener);
		}
		else {
			tableHeader.setResizingAllowed(false);
		}
		setModel(attributeView.getCurrentAttributeTableModel());
		updateFontSize(this, 1F);
		updateColumnWidths();
		setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		getTableHeader().setReorderingAllowed(false);
		getRowHeight();
		updateRowHeights();
		setRowSelectionAllowed(false);
		putClientProperty("JTable.autoStartsEdit", Boolean.FALSE);
	}

	private void changeSelectedRowHeight(final int rowIndex) {
		if (highRowIndex != rowIndex) {
			if (highRowIndex < getRowCount()) {
				final int h = getRowHeight(highRowIndex);
				setRowHeight(highRowIndex, h - AttributeTable.EXTRA_HEIGHT);
			}
			final int h = getRowHeight(rowIndex);
			setRowHeight(rowIndex, h + AttributeTable.EXTRA_HEIGHT);
			highRowIndex = rowIndex;
			assert highRowIndex >= 0;
		}
	}

	@Override
	public void changeSelection(int rowIndex, int columnIndex, final boolean toggle, final boolean extend) {
		final int rowCount = getRowCount();
		if (rowCount == 0) {
			return;
		}
		if (rowIndex >= rowCount) {
			rowIndex = 0;
			columnIndex = 0;
		}
		changeSelectedRowHeight(rowIndex);
		super.changeSelection(rowIndex, columnIndex, toggle, extend);
	}

	public void columnWidthChanged(final ColumnWidthChangeEvent event) {
		final float zoom = getZoom();
		final int col = event.getColumnNumber();
		final AttributeTableLayoutModel layoutModel = (AttributeTableLayoutModel) event.getSource();
		final int width = layoutModel.getColumnWidth(col);
		getColumnModel().getColumn(col).setPreferredWidth((int) (width * zoom));
		final MapView map = attributeView.getMapView();
		final NodeModel node = attributeView.getNode();
		map.getModeController().getMapController().nodeChanged(node);
	}

	/**
	 * @return Returns the currentModel.
	 */
	public AttributeTableModelDecoratorAdapter getAttributeTableModel() {
		return (AttributeTableModelDecoratorAdapter) getModel();
	}

	public AttributeView getAttributeView() {
		return attributeView;
	}
	
	

	@Override
    public boolean editCellAt(int row, int column, EventObject e) {
		if(isEditing() && getCellEditor() instanceof DialogTableCellEditor){
			return false;
		}
		if(column == 1 && e instanceof MouseEvent){
			final MouseEvent me = (MouseEvent) e;
			final Object value = getValueAt(row, column);
			if(value instanceof URI){
				final URI uri = (URI) value;
				final Icon linkIcon = getLinkIcon(uri);
				final int xmax = linkIcon != null ? linkIcon.getIconWidth() : 0;
				final int x = me.getX() - getColumnModel().getColumn(0).getWidth();
				if(x < xmax){
					UrlManager.getController().loadURL(uri);
					return false;
				}
             }
		}
		putClientProperty("AttributeTable.EditEvent", e);
		try{
			if(super.editCellAt(row, column, e)){
				final TableCellEditor cellEditor = getCellEditor();
				if(isEditing() && cellEditor instanceof DialogTableCellEditor){
					((JComponent)editorComp).paintImmediately(0, 0, editorComp.getWidth(), editorComp.getHeight());
					((DialogTableCellEditor)cellEditor).startEditing();
					return false;
				}
				return true;
			}
			return false;
		}
		finally{
			putClientProperty("AttributeTable.EditEvent", null);
		}
    }

	Icon getLinkIcon(final URI uri) {
		NodeModel nodeModel = ((IAttributeTableModel)getModel()).getNode();
	    final Icon linkIcon = IconController.getLinkIcon(uri, nodeModel);
	    return linkIcon;
    }
	
	@SuppressWarnings("serial")
    private class DialogTableCellEditor extends AbstractCellEditor implements TableCellEditor{
		
		final private IEditControl editControl;
		private Object value;
		private EditNodeBase editBase;
		public DialogTableCellEditor() {
			super();
			editControl = new IEditControl() {
				public void split(String newText, int position) {
				}
				
				public void ok(String newText) {
					value = newText;
					stopCellEditing();
				}
				
				public void cancel() {
					stopCellEditing();
				}
			};
        }

		public IEditControl getEditControl() {
        	return editControl;
        }

		public void setEditBase(EditNodeBase editBase) {
        	this.editBase = editBase;
        }
		
		public Object getCellEditorValue() {
	        return value;
        }

		public void startEditing(){
			if(editBase == null){
				return;
			}
			final JFrame frame = (JFrame) JOptionPane.getFrameForComponent(AttributeTable.this);
			editBase.show(frame);
		}

		public boolean isCellEditable(EventObject anEvent) {
			if (anEvent instanceof MouseEvent) { 
				return ((MouseEvent)anEvent).getClickCount() >= CLICK_COUNT_TO_START;
			}
			return true;
		}
		public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
	        return new AttributeTableCellRenderer().getTableCellRendererComponent(table, value, true, true, row, column);
        }
	};

	@Override
	public TableCellEditor getCellEditor(final int row, final int col) {
		return getCellEditor(row, col, (EventObject) getClientProperty("AttributeTable.EditEvent"));
	}
	public TableCellEditor getCellEditor(final int row, final int col, EventObject e) {
		if (dce != null) {
			dce.stopCellEditing();
		}
		if(col == 1){
			final KeyEvent kev;
			if(e instanceof KeyEvent){
				kev = (KeyEvent) e;
			}
			else{
				kev = null;
			}
			MTextController textController = (MTextController) TextController.getController();
			final IAttributeTableModel model = (IAttributeTableModel) getModel();
			final String text = getValueAt(row, col).toString();
			final DialogTableCellEditor dialogTableCellEditor = new DialogTableCellEditor();
			EditNodeBase base = textController.getEditNodeBase(model.getNode(), text, EditedComponent.TEXT, dialogTableCellEditor.getEditControl(), kev, false);
			if(base != null){
				dialogTableCellEditor.setEditBase(base);
				return dialogTableCellEditor;
			}
		}
		final JComboBox comboBox;
		if (dce == null) {
			comboBox = new JComboBox();
			comboBox.addFocusListener(AttributeTable.focusListener);
			comboBox.getEditor().getEditorComponent().addFocusListener(AttributeTable.focusListener);
			dce = new DefaultCellEditor(comboBox);
			dce.setClickCountToStart(CLICK_COUNT_TO_START);
		}
		return dce;
	}


	@Override
	public TableCellRenderer getCellRenderer(final int row, final int column) {
		return AttributeTable.dtcr;
	}

	private float getFontSize() {
		return AttributeRegistry.getRegistry(attributeView.getNode().getMap()).getFontSize();
	}

	@Override
	public Dimension getPreferredScrollableViewportSize() {
		if (!isValid()) {
			validate();
		}
		final Dimension dimension = super.getPreferredSize();
		MapView map = (MapView) SwingUtilities.getAncestorOfClass(MapView.class, this);
		if(map != null){
			dimension.width = Math.min(map.getZoomed(map.getMaxNodeWidth()), dimension.width);
			dimension.height = Math.min(map.getZoomed(AttributeTable.MAX_HEIGTH) - getTableHeaderHeight(), dimension.height);
		}
		else{
			dimension.width = Math.min(MAX_WIDTH, dimension.width);
			dimension.height = Math.min(MAX_HEIGTH, dimension.height);
		}
		return dimension;
	}

	int getTableHeaderHeight() {
		final JTableHeader tableHeader = getTableHeader();
		return tableHeader != null ? tableHeader.getPreferredSize().height : 0;
	}

	float getZoom() {
		return attributeView.getMapView().getZoom();
	}

	/**
	 */
	public void insertRow(final int row) {
		if (getModel() instanceof ExtendedAttributeTableModelDecorator) {
			final ExtendedAttributeTableModelDecorator model = (ExtendedAttributeTableModelDecorator) getModel();
			if (isEditing() && getCellEditor() != null && !getCellEditor().stopCellEditing()) {
				return;
			}
			model.insertRow(row);
			changeSelection(row, 0, false, false);
			if (editCellAt(row, 0)) {
				getEditorComponent().requestFocus();
			}
		}
	}

	@Override
	public boolean isVisible() {
		return super.isVisible() && attributeView.areAttributesVisible();
	}

	/**
	 */
	public void moveRowDown(final int row) {
		if (getModel() instanceof ExtendedAttributeTableModelDecorator && row < getRowCount() - 1) {
			final ExtendedAttributeTableModelDecorator model = (ExtendedAttributeTableModelDecorator) getModel();
			model.moveRowDown(row);
			changeSelection(row + 1, getSelectedColumn(), false, false);
		}
	}

	/**
	 */
	public void moveRowUp(final int row) {
		if (getModel() instanceof ExtendedAttributeTableModelDecorator && row > 0) {
			final ExtendedAttributeTableModelDecorator model = (ExtendedAttributeTableModelDecorator) getModel();
			model.moveRowUp(row);
			changeSelection(row - 1, getSelectedColumn(), false, false);
		}
	}

	@Override
	public Component prepareEditor(final TableCellEditor tce, final int row, final int col) {
		if(tce instanceof DialogTableCellEditor){
			return super.prepareEditor(tce, row, col);
		}
		final JComboBox comboBox = (JComboBox) ((DefaultCellEditor) tce).getComponent();
		final NodeModel node = getAttributeTableModel().getNode();
		final AttributeRegistry attributes = AttributeRegistry.getRegistry(node.getMap());
		final ComboBoxModel model;
		switch (col) {
			case 0:
				model = attributes.getComboBoxModel();
				comboBox.setEditable(!attributes.isRestricted());
				break;
			case 1:
				final String attrName = getAttributeTableModel().getValueAt(row, 0).toString();
				model = attributes.getDefaultComboBoxModel(attrName);
				comboBox.setEditable(!attributes.isRestricted(attrName));
				break;
			default:
				model = AttributeTable.getDefaultComboBoxModel();
		}
		final Object[] items = new Object[model.getSize()];
		for (int i = 0; i < items.length; i++) {
			items[i] = model.getElementAt(i);
		}
		final DefaultComboBoxModel currentModel = new DefaultComboBoxModel(items);
		comboBox.setModel(currentModel);
		updateFontSize(comboBox, getZoom());
		return super.prepareEditor(tce, row, col);
	}

	@Override
	protected boolean processKeyBinding(final KeyStroke ks, final KeyEvent e, final int condition, final boolean pressed) {
		if (ks.getKeyCode() == KeyEvent.VK_TAB && e.getModifiers() == 0 && pressed && getSelectedColumn() == 1
		        && getSelectedRow() == getRowCount() - 1 && getModel() instanceof ExtendedAttributeTableModelDecorator) {
			insertRow(getRowCount());
			return true;
		}
		if (ks.getKeyCode() == KeyEvent.VK_ESCAPE && e.getModifiers() == 0 && pressed) {
			attributeView.getNodeView().requestFocus();
			return true;
		}
		boolean retValue = super.processKeyBinding(ks, e, condition, pressed);
		if (!retValue && condition == JComponent.WHEN_FOCUSED && isFocusOwner() && ks.getKeyCode() != KeyEvent.VK_TAB
		        && e != null && e.getID() == KeyEvent.KEY_PRESSED && !e.isActionKey()
		        && e.getKeyChar() != KeyEvent.CHAR_UNDEFINED
		        && 0 == (e.getModifiers() & (InputEvent.CTRL_MASK | InputEvent.ALT_MASK))) {
			final int leadRow = getSelectionModel().getLeadSelectionIndex();
			final int leadColumn = getColumnModel().getSelectionModel().getLeadSelectionIndex();
			if (leadRow != -1 && leadColumn != -1 && !isEditing()) {
				if (!editCellAt(leadRow, leadColumn, e)) {
					return false;
				}
			}
			final Component editorComponent = getEditorComponent();
			if (editorComponent instanceof JComboBox) {
				final JComboBox comboBox = (JComboBox) editorComponent;
				if (comboBox.isEditable()) {
					final ComboBoxEditor editor = comboBox.getEditor();
					editor.selectAll();
					KeyEvent keyEv;
					keyEv = new KeyEvent(editor.getEditorComponent(), KeyEvent.KEY_TYPED, e.getWhen(),
					    e.getModifiers(), KeyEvent.VK_UNDEFINED, e.getKeyChar(), KeyEvent.KEY_LOCATION_UNKNOWN);
					retValue = SwingUtilities.processKeyBindings(keyEv);
				}
				else {
					editorComponent.requestFocus();
					retValue = true;
				}
			}
		}
		if (ks.getKeyCode() == KeyEvent.VK_SPACE) {
			return true;
		}
		return retValue;
	}

	@Override
	public void removeEditor() {
		getAttributeTableModel().editingCanceled();
		super.removeEditor();
	}

	/**
	 */
	public void removeRow(final int row) {
		if (getModel() instanceof ExtendedAttributeTableModelDecorator) {
			final ExtendedAttributeTableModelDecorator model = (ExtendedAttributeTableModelDecorator) getModel();
			model.removeRow(row);
			final int rowCount = getRowCount();
			if (row <= rowCount - 1) {
				changeSelection(row, getSelectedColumn(), false, false);
			}
			else if (rowCount >= 1) {
				changeSelection(row - 1, getSelectedColumn(), false, false);
			}
		}
	}

	@Override
	public void setModel(final TableModel dataModel) {
		super.setModel(dataModel);
	}

	/**
	 *
	 */
	public void setOptimalColumnWidths() {
		Component comp = null;
		int cellWidth = 0;
		int maxCellWidth = 2 * (int) (Math.ceil(getFontSize() + AttributeTable.TABLE_ROW_HEIGHT));
		for (int col = 0; col < 2; col++) {
			for (int row = 0; row < getRowCount(); row++) {
				comp = AttributeTable.dtcr.getTableCellRendererComponent(this, getValueAt(row, col), false, false, row,
				    col);
				cellWidth = comp.getPreferredSize().width;
				maxCellWidth = Math.max(cellWidth, maxCellWidth);
			}
			getAttributeTableModel().setColumnWidth(col, maxCellWidth + 1);
		}
	}

	@Override
	public void tableChanged(final TableModelEvent e) {
		if(isEditing() && null == getClientProperty(EDITING_STOPPED) ){
			removeEditor();
		}
		int selectedRow = getSelectedRow();
		super.tableChanged(e);
		if (getParent() == null) {
			return;
		}
			switch(e.getType())
			{
				case TableModelEvent.DELETE:
					if(selectedRow != -1 ){
						if(e.getFirstRow() <= selectedRow){
							if( e.getLastRow() >= selectedRow && e.getFirstRow() != 0) {
								changeSelection(e.getFirstRow() - 1, 0, false, false);
							}
							else if(e.getLastRow() < selectedRow){
								int rowIndex = selectedRow - (e.getLastRow() - e.getFirstRow() + 1);
								if(rowIndex < 0){
									rowIndex = 0;
								}
								if(rowIndex < getRowCount()){
									changeSelection(rowIndex , getSelectedColumn(), false, false);
								}
							}
						}
					}
					break;
				case TableModelEvent.INSERT:
					changeSelection(e.getFirstRow() , getSelectedColumn(), false, false);
					break;
				default:
					if(selectedRow > getRowCount() && getRowCount() > 0){
						changeSelection(getRowCount() - 1 , getSelectedColumn(), false, false);
					}
			}
		getParent().getParent().invalidate();
		final NodeModel node = attributeView.getNode();
		MapController mapController = attributeView.getMapView().getModeController().getMapController();
		mapController.nodeChanged(node, NodeAttributeTableModel.class, null, null);
	}

	void updateAttributeTable() {
		updateFontSize(this, 1F);
		updateRowHeights();
		updateColumnWidths();
	}

	private void updateColumnWidths() {
		final float zoom = getZoom();
		for (int i = 0; i < 2; i++) {
			final int width = (int) (getAttributeTableModel().getColumnWidth(i) * zoom);
			getColumnModel().getColumn(i).setPreferredWidth(width);
		}
	}

	private void updateFontSize(final Component c, final float zoom) {
		Font font = c.getFont();
		if (font != null) {
			final float oldFontSize = font.getSize2D();
			final float newFontSize = getFontSize() * zoom;
			if (Float.compare(oldFontSize, newFontSize) != 0) {
				font = font.deriveFont(newFontSize);
				c.setFont(font);
			}
		}
	}

	private void updateRowHeights() {
		final int rowCount = getRowCount();
		if (rowCount == 0) {
			return;
		}
		final int constHeight = getTableHeaderHeight() + AttributeTable.EXTRA_HEIGHT;
		final float zoom = getZoom();
		final float fontSize = getFontSize();
		final float tableRowHeight = fontSize + zoom * AttributeTable.TABLE_ROW_HEIGHT;
		int newHeight = (int) ((tableRowHeight * rowCount + (zoom - 1) * constHeight) / rowCount);
		if (newHeight < 1) {
			newHeight = 1;
		}
		final int highRowsNumber = (int) ((tableRowHeight - newHeight) * rowCount);
		for (int i = 0; i < highRowsNumber; i++) {
			setRowHeight(i, 1 + newHeight + (i == highRowIndex ? AttributeTable.EXTRA_HEIGHT : 0));
		}
		for (int i = highRowsNumber; i < rowCount; i++) {
			setRowHeight(i, newHeight + (i == highRowIndex ? AttributeTable.EXTRA_HEIGHT : 0));
		}
	}

	public void viewRemoved(NodeView nodeView) {
		getModel().removeTableModelListener(this);
	}

	@Override
    public void editingStopped(ChangeEvent e) {
		try{
			putClientProperty(EDITING_STOPPED, Boolean.TRUE);
		       // Take in the new value
	        TableCellEditor editor = getCellEditor();
	        if (editor != null) {
	            Object value = editor.getCellEditorValue();
	            if(value != null){
	            	setValueAt(value, editingRow, editingColumn);
	            }
	            removeEditor();
	        }
		}
		finally{
			putClientProperty(EDITING_STOPPED, null);
		}
    }

	@Override
    public void setValueAt(Object aValue, int row, int column) {
	    super.setValueAt(column == 0 ? aValue.toString() : aValue, row, column);
    }

	@Override
    public void valueChanged(ListSelectionEvent e) {
	    super.valueChanged(e);
	    tableSelectionChanged();
    }
	
	

	@Override
    public void columnSelectionChanged(ListSelectionEvent e) {
	    super.columnSelectionChanged(e);
	    tableSelectionChanged();
    }

	private void tableSelectionChanged() {
		final int r = getSelectedRow();
		final int c = getSelectedColumn();
		final ViewController viewController = Controller.getCurrentController().getViewController();
		if(r >= 0 && c >= 0){
			final Object value = getValueAt(r, c);
			viewController.addObjectTypeInfo(value);
		}
    }
}
